package com.wut.screenwebsx.Context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wut.screendbmysqlsx.Model.WindData;
import com.wut.screendbmysqlsx.Service.WindDataService;
import com.wut.screenwebsx.Service.WindRiskSpeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wind.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WindRealtimeDataContext {
    private static final int DIRECTION_TURPAN = 1;
    private static final int DIRECTION_HAMI = 2;
    private static final DateTimeFormatter DEFAULT_TIME_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WindDataService windDataService;
    private final WindRiskSpeedService windRiskSpeedService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${wind.realtime.topic:wind-realtime}")
    private String topicName;

    @Value("${wind.realtime.default-data-source:UDP_WIND}")
    private String defaultDataSource;

    @Value("${wind.realtime.default-direction:1}")
    private int defaultDirection;

    @Value("${wind.realtime.duplicate-both-directions-when-missing:true}")
    private boolean duplicateBothDirectionsWhenMissing;

    @Value("${wind.realtime.auto-calculate:true}")
    private boolean autoCalculate;

    @Value("${wind.realtime.calculate-input-data-source:}")
    private String calculateInputDataSource;

    @Value("${wind.realtime.calculate-output-source:MATLAB_RULE_V1}")
    private String calculateOutputDataSource;

    @KafkaListener(topics = "${wind.realtime.topic:wind-realtime}", groupId = "${wind.realtime.group-id:group-wind}")
    public void windRealtimeListener(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        LocalDateTime minHour = null;
        LocalDateTime maxHour = null;
        int upsertRows = 0;

        try {
            List<WindData> allRows = new ArrayList<>();
            for (ConsumerRecord<String, String> record : records) {
                allRows.addAll(parseWindRows(record.value()));
            }

            if (!allRows.isEmpty()) {
                upsertRows = windDataService.upsertBatch(allRows);
                for (WindData row : allRows) {
                    LocalDateTime time = row.getTimeStamp();
                    if (time == null) {
                        continue;
                    }
                    if (minHour == null || time.isBefore(minHour)) {
                        minHour = time;
                    }
                    if (maxHour == null || time.isAfter(maxHour)) {
                        maxHour = time;
                    }
                }

                if (autoCalculate && minHour != null && maxHour != null) {
                    String inputSource = hasText(calculateInputDataSource) ? calculateInputDataSource.trim() : null;
                    String outputSource = hasText(calculateOutputDataSource) ? calculateOutputDataSource.trim() : "MATLAB_RULE_V1";
                    Map<String, Object> result = windRiskSpeedService.calculateAndPersist(
                            toEpochMilli(minHour),
                            toEpochMilli(maxHour),
                            inputSource,
                            outputSource
                    );
                    log.info("wind realtime consumed: topic={}, records={}, rows={}, range=[{}, {}], calcResult={}",
                            topicName, records.size(), upsertRows, minHour, maxHour, result);
                } else {
                    log.info("wind realtime consumed: topic={}, records={}, rows={}, range=[{}, {}], autoCalculate={}",
                            topicName, records.size(), upsertRows, minHour, maxHour, autoCalculate);
                }
            }
        } catch (Exception e) {
            log.error("wind realtime consume failed: topic={}, records={}, rows={}", topicName, records.size(), upsertRows, e);
        } finally {
            if (ack != null) {
                ack.acknowledge();
            }
        }
    }

    private List<WindData> parseWindRows(String payload) {
        if (!hasText(payload)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            List<WindData> rows = new ArrayList<>();
            collectRows(root, null, rows);
            return rows;
        } catch (Exception e) {
            log.warn("skip invalid wind payload: {}", payload, e);
            return List.of();
        }
    }

    private void collectRows(JsonNode node, LocalDateTime inheritedTime, List<WindData> out) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectRows(item, inheritedTime, out);
            }
            return;
        }

        JsonNode dataNode = node.has("data") ? node.path("data") : node;
        LocalDateTime thisTime = firstNonNullTime(
                parseTimestamp(textOf(node, "timestamp", "timeStamp", "time", "ts")),
                parseTimestamp(textOf(dataNode, "timestamp", "timeStamp", "time", "ts")),
                inheritedTime,
                LocalDateTime.now()
        );

        if (dataNode.isArray()) {
            for (JsonNode item : dataNode) {
                collectRows(item, thisTime, out);
            }
            return;
        }

        String startStake = firstText(
                textOf(dataNode, "startStake", "start_stake", "startKm", "start_km"),
                textOf(node, "startStake", "start_stake", "startKm", "start_km")
        );
        String endStake = firstText(
                textOf(dataNode, "endStake", "end_stake", "endKm", "end_km"),
                textOf(node, "endStake", "end_stake", "endKm", "end_km")
        );
        if (!hasText(startStake) && !hasText(endStake)) {
            return;
        }

        BigDecimal windSpeed = BigDecimal.valueOf(parseDouble(firstText(
                textOf(dataNode, "windSpeed", "wind_speed", "wind"),
                textOf(node, "windSpeed", "wind_speed", "wind")
        ), 0D));

        String sectionName = firstText(
                textOf(dataNode, "sectionName", "section_name"),
                textOf(node, "sectionName", "section_name")
        );
        String windDirection = firstText(
                textOf(dataNode, "windDirection", "wind_direction", "windDir", "wind_dir"),
                textOf(node, "windDirection", "wind_direction", "windDir", "wind_dir")
        );
        Integer heavySpeedLimit = parseIntegerOrNull(firstText(
                textOf(dataNode, "heavyVehicleSpeedLimit", "heavy_vehicle_speed_limit", "truckSpeedLimit", "truck_speed_limit"),
                textOf(node, "heavyVehicleSpeedLimit", "heavy_vehicle_speed_limit", "truckSpeedLimit", "truck_speed_limit")
        ));
        Integer lightSpeedLimit = parseIntegerOrNull(firstText(
                textOf(dataNode, "lightVehicleSpeedLimit", "light_vehicle_speed_limit", "carSpeedLimit", "car_speed_limit"),
                textOf(node, "lightVehicleSpeedLimit", "light_vehicle_speed_limit", "carSpeedLimit", "car_speed_limit")
        ));
        Integer controlLevel = parseIntegerOrNull(firstText(
                textOf(dataNode, "controlLevel", "control_level"),
                textOf(node, "controlLevel", "control_level")
        ));

        String dataSource = firstText(
                textOf(dataNode, "dataSource", "data_source", "source"),
                textOf(node, "dataSource", "data_source", "source")
        );
        if (!hasText(dataSource)) {
            dataSource = defaultDataSource;
        }

        LocalDateTime hour = truncateToHour(thisTime);
        LocalDateTime now = LocalDateTime.now();
        for (Integer direction : resolveDirections(dataNode, node)) {
            WindData row = new WindData();
            row.setTimeStamp(hour);
            row.setDirection(direction);
            row.setStartStake(startStake);
            row.setEndStake(endStake);
            row.setSectionName(sectionName);
            row.setWindSpeed(windSpeed);
            row.setWindDirection(windDirection);
            row.setHeavyVehicleSpeedLimit(heavySpeedLimit);
            row.setLightVehicleSpeedLimit(lightSpeedLimit);
            row.setControlLevel(controlLevel);
            row.setDataSource(dataSource);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            out.add(row);
        }
    }

    private List<Integer> resolveDirections(JsonNode dataNode, JsonNode rootNode) {
        Integer direction = normalizeDirection(firstText(
                textOf(dataNode, "direction", "roadDirect", "road_direction"),
                textOf(rootNode, "direction", "roadDirect", "road_direction")
        ));
        if (direction != null) {
            return List.of(direction);
        }

        if (duplicateBothDirectionsWhenMissing) {
            return List.of(DIRECTION_TURPAN, DIRECTION_HAMI);
        }

        Integer fallback = normalizeDirection(Integer.toString(defaultDirection));
        return List.of(fallback == null ? DIRECTION_TURPAN : fallback);
    }

    private Integer normalizeDirection(String value) {
        if (!hasText(value)) {
            return null;
        }

        String text = value.trim().toLowerCase(Locale.ROOT);
        if (text.contains("\u5410\u9c81\u756a") || text.contains("turpan")) {
            return DIRECTION_TURPAN;
        }
        if (text.contains("\u54c8\u5bc6") || text.contains("hami")) {
            return DIRECTION_HAMI;
        }

        try {
            int numeric = (int) Double.parseDouble(text);
            if (numeric == DIRECTION_TURPAN || numeric == DIRECTION_HAMI) {
                return numeric;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private LocalDateTime parseTimestamp(String raw) {
        if (!hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        try {
            long epoch = (long) Double.parseDouble(text);
            if (epoch <= 0L) {
                return null;
            }
            if (epoch < 100000000000L) {
                epoch = epoch * 1000L;
            }
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault());
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text, DEFAULT_TIME_PATTERN);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text.replace('/', '-'), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private LocalDateTime truncateToHour(LocalDateTime time) {
        if (time == null) {
            return LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        }
        return time.withMinute(0).withSecond(0).withNano(0);
    }

    private long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Integer parseIntegerOrNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private double parseDouble(String value, double defaultValue) {
        if (!hasText(value)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String textOf(JsonNode node, String... names) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        for (String name : names) {
            JsonNode field = node.path(name);
            if (field != null && !field.isNull() && !field.isMissingNode()) {
                String text = field.asText("").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private LocalDateTime firstNonNullTime(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
