package com.wut.screenwebsx.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 4.1 路段运行状态业务服务。
 *
 * Data strategy:
 * 1) Prefer trajectory-driven statistics.
 * 2) No mock/fallback payload is returned.
 * 3) Always normalize response payloads to match docs/第四章-接口文档.md (4.1).
 */
@Service
@Slf4j
public class WindControlRoadStatusService {
    private static final Pattern SEGMENT_STAKE_PATTERN =
            Pattern.compile("(?i)k(\\d+)(?:\\+\\d+)?\\s*-\\s*k(\\d+)(?:\\+\\d+)?");

    private static final String TRAFFIC_SEGMENT_1 = "K3178-红山口服务区";
    private static final String TRAFFIC_SEGMENT_2 = "红山口服务区-红山口互通";
    private static final String TRAFFIC_SEGMENT_3 = "红山口互通-K3203";
    private static final List<String> TRAFFIC_SEGMENT_ORDER = List.of(
            TRAFFIC_SEGMENT_1,
            TRAFFIC_SEGMENT_2,
            TRAFFIC_SEGMENT_3
    );

    private final WindControlStateService stateService;
    private final WindControlTrajectoryService trajectoryService;

    public WindControlRoadStatusService(WindControlStateService stateService,
                                        WindControlTrajectoryService trajectoryService) {
        this.stateService = stateService;
        this.trajectoryService = trajectoryService;
    }

    /**
     * 4.1.1 全线状态可视化。
     */
    public List<Map<String, Object>> getRoadRunOverview(long timestamp) {
        List<Map<String, Object>> realRows = trajectoryService.buildSectionParameterDetections(
                timestamp,
                stateService.getFullLineWindSections()
        );
        if (realRows == null) {
            log.warn("4.1.1 road-statuses no real trajectory data, timestamp={}", timestamp);
            return List.of();
        }
        return normalizeOverviewSections(timestamp, realRows);
    }

    /**
     * 4.1.4 服务区进出车辆。
     */
    public List<Map<String, Object>> getServiceAreaVehicleStats(long timestamp) {
        List<Map<String, Object>> realRows = trajectoryService.buildServiceAreaVehicleStats(timestamp, stateService.getFullLineWindSections());
        if (realRows == null) {
            log.warn("4.1.4 service-areas no real trajectory data, timestamp={}", timestamp);
            return List.of();
        }
        return normalizeServiceAreaRows(realRows, timestamp);
    }

    /**
     * 4.1.5 交通状态分析。
     */
    public List<Map<String, Object>> getTrafficStateAnalysis(long timestamp) {
        List<Map<String, Object>> realRows = trajectoryService.buildTrafficStateAnalysis(timestamp, stateService.getFullLineWindSections());
        if (realRows == null) {
            log.warn("4.1.5 traffic-states no real trajectory data, timestamp={}", timestamp);
            return List.of();
        }
        return normalizeTrafficStateRows(realRows);
    }

    /**
     * 4.1.2 断面参数检测。
     */
    public List<Map<String, Object>> getSectionParameterDetections(long timestamp, Integer direction) {
        List<Map<String, Object>> realRows = trajectoryService.buildSectionParameterDetections(timestamp, stateService.getFullLineWindSections());
        if (realRows == null) {
            log.warn("4.1.2 section-parameter-detections no real trajectory data, timestamp={}, direction={}", timestamp, direction);
            return List.of();
        }
        List<Map<String, Object>> rows = filterByDirection(realRows, direction);
        return normalizeSectionParameterRows(rows, timestamp);
    }

    /**
     * 4.1.3 事件检测信息。
     */
    public List<Map<String, Object>> getEventDetectionInfos(long timestamp) {
        List<Map<String, Object>> realRows = trajectoryService.buildEventDetections(
                timestamp,
                stateService.getFullLineWindSections(),
                stateService.getSpeedThresholdByWindLevel()
        );
        if (realRows == null) {
            log.warn("4.1.3 event-detections no real trajectory data, timestamp={}", timestamp);
            return List.of();
        }
        List<Map<String, Object>> normalized = normalizeEventRows(realRows, timestamp);
        persistDetectionEvents(normalized, timestamp);
        return normalized;
    }

    private List<Map<String, Object>> filterByDirection(List<Map<String, Object>> rows, Integer direction) {
        if (direction == null) {
            return rows;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int rowDirection = normalizeDirection(row.get("direction"), 2);
            if (rowDirection == direction) {
                result.add(row);
            }
        }
        return result;
    }

    private List<Map<String, Object>> normalizeOverviewSections(long timestamp, List<Map<String, Object>> rawSections) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (rawSections == null) {
            return normalized;
        }
        for (Map<String, Object> map : rawSections) {
            String segmentName = pickText(map, "segment", "segmentName");
            int direction = normalizeDirection(map.get("direction"), 2);
            int currentVehicleCount = Math.max(0, stateService.intValue(map.get("currentVehicleCount"), 0));
            Double avgSpeed = nullableDoubleValue(map.get("avgSpeedKmPerHour"));
            String congestionStatus = currentVehicleCount == 0
                    ? "SMOOTH"
                    : normalizeCongestionStatus(
                    valueOf(map.get("congestionStatus")),
                    avgSpeed
            );
            String color = colorByCongestionStatus(congestionStatus);
            String segmentId = toDisplaySegmentId(segmentName);
            if (segmentId == null) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("segmentId", segmentId);
            row.put("direction", direction);
            row.put("color", color);
            row.put("timestamp", timestamp);
            normalized.add(row);
        }
        normalized.sort(Comparator
                .comparingInt((Map<String, Object> row) -> segmentSortKey(stateService.stringValue(row.get("segmentId"))))
                .thenComparingInt(row -> normalizeDirection(row.get("direction"), 2)));
        return normalized;
    }

    private String toDisplaySegmentId(String segmentName) {
        if (segmentName == null || segmentName.isBlank()) {
            return null;
        }
        Matcher matcher = SEGMENT_STAKE_PATTERN.matcher(segmentName);
        if (!matcher.find()) {
            return null;
        }
        int a = parseIntSafely(matcher.group(1), -1);
        int b = parseIntSafely(matcher.group(2), -1);
        if (a <= 0 || b <= 0) {
            return null;
        }
        int low = Math.min(a, b);
        return "k" + low;
    }

    private int segmentSortKey(String segmentId) {
        if (segmentId == null || segmentId.isBlank()) {
            return Integer.MAX_VALUE;
        }
        String normalized = segmentId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("k")) {
            return Integer.MAX_VALUE;
        }
        int value = parseIntSafely(normalized.substring(1), Integer.MIN_VALUE);
        if (value == Integer.MIN_VALUE) {
            return Integer.MAX_VALUE;
        }
        return -value;
    }

    private int parseIntSafely(String text, int defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private List<Map<String, Object>> normalizeServiceAreaRows(List<Map<String, Object>> rows, long timestamp) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (rows == null) {
            return normalized;
        }
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("serviceArea", pickText(row, "serviceArea", "segment", "segmentName"));
            item.put("timestamp", longValue(row.get("timestamp"), timestamp));
            item.put("inboundVehicle", Math.max(0, stateService.intValue(row.get("inboundVehicle"), 0)));
            item.put("outboundVehicle", Math.max(0, stateService.intValue(row.get("outboundVehicle"), 0)));
            item.put("insideVehicle", Math.max(0, stateService.intValue(row.get("insideVehicle"), 0)));
            normalized.add(item);
        }
        return normalized;
    }

    private List<Map<String, Object>> normalizeTrafficStateRows(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        if (rows == null) {
            return new ArrayList<>();
        }
        for (Map<String, Object> row : rows) {
            String segment = toTrafficSegmentName(pickText(row, "segment", "segmentName"));
            if (segment == null) {
                continue;
            }
            int direction = normalizeDirection(row.get("direction"), 2);
            int vehPerHour = Math.max(0, stateService.intValue(row.get("vehPerHour"), 0));
            int updatedEveryMin = Math.max(1, stateService.intValue(row.get("updatedEveryMin"), 5));

            String key = segment + "#" + direction;
            Map<String, Object> merged = grouped.get(key);
            if (merged == null) {
                merged = new LinkedHashMap<>();
                merged.put("segment", segment);
                merged.put("direction", direction);
                merged.put("vehPerHour", vehPerHour);
                merged.put("updatedEveryMin", updatedEveryMin);
                grouped.put(key, merged);
            } else {
                merged.put("vehPerHour", stateService.intValue(merged.get("vehPerHour"), 0) + vehPerHour);
                merged.put("updatedEveryMin", Math.min(
                        stateService.intValue(merged.get("updatedEveryMin"), 5),
                        updatedEveryMin
                ));
            }
        }
        List<Map<String, Object>> normalized = new ArrayList<>(grouped.values());
        normalized.sort(Comparator
                .comparingInt((Map<String, Object> item) -> trafficSegmentOrder(stateService.stringValue(item.get("segment"))))
                .thenComparingInt(item -> normalizeDirection(item.get("direction"), 2)));
        return normalized;
    }

    private String toTrafficSegmentName(String rawSegment) {
        if (rawSegment == null) {
            return null;
        }
        String segment = rawSegment.trim();
        if (segment.isEmpty()) {
            return null;
        }
        String lower = segment.toLowerCase(Locale.ROOT);
        if (lower.contains("k3178")) {
            return TRAFFIC_SEGMENT_1;
        }
        if (lower.contains("红山口服务区") && lower.contains("互通")) {
            return TRAFFIC_SEGMENT_2;
        }
        if (lower.contains("k3194") || lower.contains("k3195") || lower.contains("k3196")
                || lower.contains("k3197") || lower.contains("k3198") || lower.contains("k3199")
                || lower.contains("k3200")) {
            return TRAFFIC_SEGMENT_2;
        }
        if (lower.contains("红山口互通") && lower.contains("k3203")) {
            return TRAFFIC_SEGMENT_3;
        }
        if (lower.contains("红山口互通")) {
            return TRAFFIC_SEGMENT_3;
        }
        if (lower.contains("k3201") || lower.contains("k3202") || lower.contains("k3203")) {
            return TRAFFIC_SEGMENT_3;
        }
        return null;
    }

    private int trafficSegmentOrder(String segment) {
        int idx = TRAFFIC_SEGMENT_ORDER.indexOf(segment);
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }

    private List<Map<String, Object>> normalizeSectionParameterRows(List<Map<String, Object>> rows, long timestamp) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (rows == null) {
            return normalized;
        }
        for (Map<String, Object> row : rows) {
            int currentVehicleCount = Math.max(0, stateService.intValue(row.get("currentVehicleCount"), 0));
            Double avgSpeed = nullableDoubleValue(row.get("avgSpeedKmPerHour"));
            if (avgSpeed != null && avgSpeed < 0D) {
                avgSpeed = 0D;
            }
            String status = currentVehicleCount == 0
                    ? "SMOOTH"
                    : normalizeCongestionStatus(valueOf(row.get("congestionStatus")), avgSpeed);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("timestamp", longValue(row.get("timestamp"), timestamp));
            item.put("segmentId", pickText(row, "segmentId"));
            item.put("segment", toPureStakeRangeText(pickText(row, "segment", "segmentName")));
            item.put("direction", normalizeDirection(row.get("direction"), 2));
            item.put("currentVehicleCount", currentVehicleCount);
            item.put("avgSpeedKmPerHour", avgSpeed == null ? null : Math.round(avgSpeed * 10D) / 10D);
            item.put("congestionStatus", status);
            item.put("updateIntervalMin", Math.max(1, stateService.intValue(row.get("updateIntervalMin"), 5)));
            normalized.add(item);
        }
        return normalized;
    }

    private List<Map<String, Object>> normalizeEventRows(List<Map<String, Object>> rows, long timestamp) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (rows == null) {
            return normalized;
        }
        int seq = 1;
        for (Map<String, Object> row : rows) {
            String eventId = valueOf(row.get("eventId"));
            if (eventId.isBlank()) {
                eventId = "DET-" + (timestamp % 100000) + "-" + seq;
            }
            seq++;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eventId", eventId);
            item.put("eventType", defaultIfBlank(valueOf(row.get("eventType")), "OVERSPEED"));
            item.put("segment", toPureStakeRangeText(pickText(row, "segment", "segmentName")));
            item.put("vehiclePlate", defaultIfBlank(valueOf(row.get("vehiclePlate")), "UNKNOWN"));
            item.put("thresholdSpeedKmPerHour", Math.max(0, stateService.intValue(row.get("thresholdSpeedKmPerHour"), 0)));
            item.put("status", defaultIfBlank(valueOf(row.get("status")), "UNPROCESSED"));
            item.put("timestamp", longValue(row.get("timestamp"), timestamp));
            normalized.add(item);
        }
        return normalized;
    }

    private void persistDetectionEvents(List<Map<String, Object>> rows, long timestamp) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int success = 0;
        int failed = 0;
        for (Map<String, Object> row : rows) {
            try {
                stateService.getPersistenceService().upsertDetectionEvent(row);
                success++;
            } catch (Exception ex) {
                failed++;
                log.warn("persist 4.1 detection event failed, eventId={}, timestamp={}, reason={}",
                        valueOf(row.get("eventId")), timestamp, ex.getMessage());
            }
        }
        if (failed > 0) {
            log.info("4.1.3 detection events persisted with partial failures, success={}, failed={}, timestamp={}",
                    success, failed, timestamp);
        }
    }

    private boolean isInterchangeSegment(String segmentName) {
        String lower = segmentName.toLowerCase(Locale.ROOT);
        return lower.contains("互通") || lower.contains("interchange") || lower.contains("junction");
    }

    private String normalizeCongestionStatus(String raw, Double avgSpeed) {
        String s = raw.toUpperCase(Locale.ROOT);
        if ("SMOOTH".equals(s) || "SLOW".equals(s) || "CONGESTED".equals(s)) {
            return s;
        }
        if (avgSpeed == null) {
            return "CONGESTED";
        }
        if (avgSpeed > 80D) {
            return "SMOOTH";
        }
        if (avgSpeed >= 60D) {
            return "SLOW";
        }
        return "CONGESTED";
    }

    private String colorByCongestionStatus(String congestionStatus) {
        if ("SMOOTH".equals(congestionStatus)) {
            return "green";
        }
        if ("SLOW".equals(congestionStatus)) {
            return "yellow";
        }
        return "red";
    }

    private int normalizeDirection(Object raw, int defaultDirection) {
        int d = stateService.intValue(raw, defaultDirection);
        return d == 1 ? 1 : 2;
    }

    private String toPureStakeRangeText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        Matcher matcher = SEGMENT_STAKE_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return "";
        }
        return "K" + matcher.group(1) + "-K" + matcher.group(2);
    }

    private String pickText(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            String value = valueOf(row.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private long longValue(Object raw, long fallback) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(valueOf(raw));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double doubleValue(Object raw, double fallback) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(valueOf(raw));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Double nullableDoubleValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        String text = valueOf(raw);
        if (text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String valueOf(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
