package com.wut.screenwebsx.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 4.1 路段运行状态业务服务。
 *
 * Data strategy:
 * 1) Prefer trajectory-driven statistics.
 * 2) Fall back to deterministic mock values when trajectory is unavailable.
 * 3) Always normalize response payloads to match docs/第四章-接口文档.md (4.1).
 */
@Service
@Slf4j
public class WindControlRoadStatusService {
    private final WindControlStateService stateService;
    private final WindControlWindImpactService windImpactService;
    private final WindControlTrajectoryService trajectoryService;

    public WindControlRoadStatusService(WindControlStateService stateService,
                                        WindControlWindImpactService windImpactService,
                                        WindControlTrajectoryService trajectoryService) {
        this.stateService = stateService;
        this.windImpactService = windImpactService;
        this.trajectoryService = trajectoryService;
    }

    /**
     * 4.1.1 全线状态可视化。
     */
    public Map<String, Object> getRoadRunOverview(long timestamp) {
        List<Map<String, Object>> sections = normalizeOverviewSections(
                windImpactService.getWindVisualization(timestamp, "real").get("sections")
        );

        Set<String> interchangeNames = new LinkedHashSet<>();
        Set<String> serviceAreaNames = new LinkedHashSet<>();
        for (Map<String, Object> section : sections) {
            String segmentName = stateService.stringValue(section.get("segmentName"));
            if (isInterchangeSegment(segmentName)) {
                interchangeNames.add(segmentName);
            }
            if (isServiceAreaSegment(segmentName)) {
                serviceAreaNames.add(segmentName);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("digitalTwinEnabled", true);
        data.put("interchangeCount", interchangeNames.size());
        data.put("serviceAreaCount", serviceAreaNames.size());
        data.put("sections", sections);
        return data;
    }

    /**
     * 4.1.4 服务区进出车辆。
     */
    public List<Map<String, Object>> getServiceAreaVehicleStats(long timestamp) {
        List<Map<String, Object>> realRows = trajectoryService.buildServiceAreaVehicleStats(timestamp, stateService.getFullLineWindSections());
        List<Map<String, Object>> rows = realRows != null ? realRows : buildFallbackServiceAreaVehicleStats(timestamp);
        if (realRows == null) {
            log.info("4.1.4 service-areas fallback data used, timestamp={}", timestamp);
        }
        return normalizeServiceAreaRows(rows, timestamp);
    }

    /**
     * 4.1.5 交通状态分析。
     */
    public List<Map<String, Object>> getTrafficStateAnalysis(long timestamp) {
        List<Map<String, Object>> realRows = trajectoryService.buildTrafficStateAnalysis(timestamp, stateService.getFullLineWindSections());
        List<Map<String, Object>> rows = realRows != null ? realRows : buildFallbackTrafficStateAnalysis();
        if (realRows == null) {
            log.info("4.1.5 traffic-states fallback data used, timestamp={}", timestamp);
        }
        return normalizeTrafficStateRows(rows);
    }

    /**
     * 4.1.2 断面参数检测。
     */
    public List<Map<String, Object>> getSectionParameterDetections(long timestamp, Integer direction) {
        List<Map<String, Object>> realRows = trajectoryService.buildSectionParameterDetections(timestamp, stateService.getFullLineWindSections());
        List<Map<String, Object>> rows = realRows != null
                ? filterByDirection(realRows, direction)
                : buildFallbackSectionParameterDetections(timestamp, direction);
        if (realRows == null) {
            log.info("4.1.2 section-parameter-detections fallback data used, timestamp={}, direction={}", timestamp, direction);
        }
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
        List<Map<String, Object>> rows = realRows != null ? realRows : buildFallbackEventDetections(timestamp);
        if (realRows == null) {
            log.info("4.1.3 event-detections fallback data used, timestamp={}", timestamp);
        }
        return normalizeEventRows(rows, timestamp);
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

    private List<Map<String, Object>> buildFallbackServiceAreaVehicleStats(long timestamp) {
        Set<String> serviceAreas = new LinkedHashSet<>();
        for (Map<String, Object> section : stateService.getFullLineWindSections()) {
            String segmentName = stateService.stringValue(section.get("segmentName"));
            if (isServiceAreaSegment(segmentName)) {
                serviceAreas.add(segmentName);
            }
        }
        if (serviceAreas.isEmpty()) {
            serviceAreas.add("ServiceArea-South");
            serviceAreas.add("ServiceArea-North");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (String serviceArea : serviceAreas) {
            int base = Math.abs(serviceArea.hashCode() % 40) + 20;
            int minuteFactor = (int) ((timestamp / 60000) % 10);
            int inbound = base + minuteFactor;
            int outbound = Math.max(5, base - 6 + minuteFactor / 2);
            int inside = inbound + outbound + 20;
            rows.add(stateService.row(
                    "serviceArea", serviceArea,
                    "timestamp", timestamp,
                    "inboundVehicle", inbound,
                    "outboundVehicle", outbound,
                    "insideVehicle", inside
            ));
        }
        return rows;
    }

    private List<Map<String, Object>> buildFallbackTrafficStateAnalysis() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> section : stateService.getFullLineWindSections()) {
            String segment = stateService.stringValue(section.get("segmentName"));
            int direction = normalizeDirection(section.get("direction"), 2);
            int realWind = stateService.intValue(section.get("realWindLevel"), 6);
            int flow = Math.max(200, 1500 - realWind * 95);
            rows.add(stateService.row(
                    "segment", segment,
                    "direction", direction,
                    "vehPerHour", flow,
                    "updatedEveryMin", 5
            ));
        }
        return rows;
    }

    private List<Map<String, Object>> buildFallbackSectionParameterDetections(long timestamp, Integer direction) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> section : stateService.getFullLineWindSections()) {
            int sectionDirection = normalizeDirection(section.get("direction"), 2);
            if (direction != null && sectionDirection != direction) {
                continue;
            }

            int realWindLevel = stateService.intValue(section.get("realWindLevel"), 6);
            int avgSpeed = Math.max(40, 95 - realWindLevel * 5);
            String congestionStatus = avgSpeed < 60 ? "CONGESTED" : (avgSpeed <= 80 ? "SLOW" : "SMOOTH");
            int currentVehicles = 20 + Math.abs(stateService.stringValue(section.get("segmentId")).hashCode() % 45);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", timestamp);
            row.put("segmentId", stateService.stringValue(section.get("segmentId")));
            row.put("segment", stateService.stringValue(section.get("segmentName")));
            row.put("direction", sectionDirection);
            row.put("currentVehicleCount", currentVehicles);
            row.put("avgSpeedKmPerHour", avgSpeed);
            row.put("congestionStatus", congestionStatus);
            row.put("updateIntervalMin", 5);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> buildFallbackEventDetections(long timestamp) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> sections = stateService.getFullLineWindSections();
        if (sections.isEmpty()) {
            return rows;
        }

        String firstSegment = stateService.stringValue(sections.get(0).get("segmentName"));
        rows.add(stateService.row(
                "eventId", "DET-" + (timestamp % 100000),
                "eventType", "OVERSPEED",
                "segment", firstSegment,
                "vehiclePlate", "TEST-A8F21X",
                "thresholdSpeedKmPerHour", 120,
                "status", "UNPROCESSED",
                "timestamp", timestamp
        ));

        if (sections.size() > 1) {
            String secondSegment = stateService.stringValue(sections.get(1).get("segmentName"));
            rows.add(stateService.row(
                    "eventId", "DET-" + ((timestamp + 1) % 100000),
                    "eventType", "STOPPED",
                    "segment", secondSegment,
                    "vehiclePlate", "TEST-A3K92M",
                    "thresholdSpeedKmPerHour", 0,
                    "status", "UNPROCESSED",
                    "timestamp", timestamp
            ));
        }
        return rows;
    }

    private List<Map<String, Object>> normalizeOverviewSections(Object rawSections) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (!(rawSections instanceof List<?> list)) {
            return normalized;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String segmentId = valueOf(map.get("segmentId"));
            String segmentName = valueOf(map.get("segmentName"));
            int direction = normalizeDirection(map.get("direction"), 2);
            int windLevel = Math.max(0, stateService.intValue(map.get("windLevel"), 0));
            String color = normalizeColor(valueOf(map.get("color")), windLevel);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("segmentId", segmentId);
            row.put("segmentName", segmentName);
            row.put("direction", direction);
            row.put("windLevel", windLevel);
            row.put("color", color);
            normalized.add(row);
        }
        return normalized;
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
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (rows == null) {
            return normalized;
        }
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("segment", pickText(row, "segment", "segmentName"));
            item.put("direction", normalizeDirection(row.get("direction"), 2));
            item.put("vehPerHour", Math.max(0, stateService.intValue(row.get("vehPerHour"), 0)));
            item.put("updatedEveryMin", Math.max(1, stateService.intValue(row.get("updatedEveryMin"), 5)));
            normalized.add(item);
        }
        return normalized;
    }

    private List<Map<String, Object>> normalizeSectionParameterRows(List<Map<String, Object>> rows, long timestamp) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (rows == null) {
            return normalized;
        }
        for (Map<String, Object> row : rows) {
            double avgSpeed = doubleValue(row.get("avgSpeedKmPerHour"), 0D);
            if (avgSpeed < 0D) {
                avgSpeed = 0D;
            }
            String status = normalizeCongestionStatus(valueOf(row.get("congestionStatus")), avgSpeed);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("timestamp", longValue(row.get("timestamp"), timestamp));
            item.put("segmentId", pickText(row, "segmentId"));
            item.put("segment", pickText(row, "segment", "segmentName"));
            item.put("direction", normalizeDirection(row.get("direction"), 2));
            item.put("currentVehicleCount", Math.max(0, stateService.intValue(row.get("currentVehicleCount"), 0)));
            item.put("avgSpeedKmPerHour", Math.round(avgSpeed * 10D) / 10D);
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
            item.put("segment", pickText(row, "segment", "segmentName"));
            item.put("vehiclePlate", defaultIfBlank(valueOf(row.get("vehiclePlate")), "UNKNOWN"));
            item.put("thresholdSpeedKmPerHour", Math.max(0, stateService.intValue(row.get("thresholdSpeedKmPerHour"), 0)));
            item.put("status", defaultIfBlank(valueOf(row.get("status")), "UNPROCESSED"));
            item.put("timestamp", longValue(row.get("timestamp"), timestamp));
            normalized.add(item);
        }
        return normalized;
    }

    private boolean isServiceAreaSegment(String segmentName) {
        String lower = segmentName.toLowerCase(Locale.ROOT);
        return lower.contains("服务区") || lower.contains("service area") || lower.contains("servicearea") || lower.contains("service");
    }

    private boolean isInterchangeSegment(String segmentName) {
        String lower = segmentName.toLowerCase(Locale.ROOT);
        return lower.contains("互通") || lower.contains("interchange") || lower.contains("junction");
    }

    private String normalizeCongestionStatus(String raw, double avgSpeed) {
        String s = raw.toUpperCase(Locale.ROOT);
        if ("SMOOTH".equals(s) || "SLOW".equals(s) || "CONGESTED".equals(s)) {
            return s;
        }
        if (avgSpeed > 80D) {
            return "SMOOTH";
        }
        if (avgSpeed >= 60D) {
            return "SLOW";
        }
        return "CONGESTED";
    }

    private String normalizeColor(String raw, int windLevel) {
        String s = raw.toLowerCase(Locale.ROOT);
        if ("red".equals(s) || "yellow".equals(s) || "green".equals(s)) {
            return s;
        }
        if (windLevel >= 11) {
            return "red";
        }
        if (windLevel >= 9) {
            return "yellow";
        }
        return "green";
    }

    private int normalizeDirection(Object raw, int defaultDirection) {
        int d = stateService.intValue(raw, defaultDirection);
        return d == 1 ? 1 : 2;
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

    private String valueOf(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
