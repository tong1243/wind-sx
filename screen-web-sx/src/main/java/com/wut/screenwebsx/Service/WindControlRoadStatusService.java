package com.wut.screenwebsx.Service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 4.1 路段运行状态业务服务。
 *
 * 数据策略：
 * 1. 优先使用表1-1轨迹数据（traj_near_real_xxxx_xx_xx / traj_near_real_yyyyMMdd）实时计算；
 * 2. 当轨迹源当前不可用或时间窗无数据时，回退到原有演示规则，保障接口稳定返回；
 * 3. 所有返回字段保持不变，前端无需调整字段名。
 */
@Service
public class WindControlRoadStatusService {
    /** 共享状态服务（提供全线路段、阈值等信息）。 */
    private final WindControlStateService stateService;
    /** 4.2 服务（用于复用全线风区可视化结果）。 */
    private final WindControlWindImpactService windImpactService;
    /** 轨迹聚合服务（负责 4.1 轨迹统计逻辑）。 */
    private final WindControlTrajectoryService trajectoryService;

    /**
     * 构造函数。
     *
     * @param stateService 共享状态服务
     * @param windImpactService 风影响服务
     * @param trajectoryService 轨迹聚合服务
     */
    public WindControlRoadStatusService(WindControlStateService stateService,
                                        WindControlWindImpactService windImpactService,
                                        WindControlTrajectoryService trajectoryService) {
        this.stateService = stateService;
        this.windImpactService = windImpactService;
        this.trajectoryService = trajectoryService;
    }

    /**
     * 查询路段运行总览（4.1.1）。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 总览对象
     */
    public Map<String, Object> getRoadRunOverview(long timestamp) {
        Set<String> interchangeNames = new LinkedHashSet<>();
        Set<String> serviceAreaNames = new LinkedHashSet<>();
        for (Map<String, Object> section : stateService.getFullLineWindSections()) {
            String segmentName = stateService.stringValue(section.get("segmentName"));
            if (segmentName.contains("互通")) {
                interchangeNames.add(segmentName);
            }
            if (segmentName.contains("服务区")) {
                serviceAreaNames.add(segmentName);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("digitalTwinEnabled", true);
        data.put("interchangeCount", interchangeNames.size());
        data.put("serviceAreaCount", serviceAreaNames.size());
        data.put("sections", windImpactService.getWindVisualization(timestamp, "real").get("sections"));
        return data;
    }

    /**
     * 查询服务区车辆统计（4.1.4）。
     *
     * 处理逻辑：
     * 1. 优先使用轨迹时序统计（进入/离开/在内）；
     * 2. 轨迹不可用时回退到规则生成值，保证接口可联调。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 服务区统计列表
     */
    public List<Map<String, Object>> getServiceAreaVehicleStats(long timestamp) {
        List<Map<String, Object>> realRows = trajectoryService.buildServiceAreaVehicleStats(timestamp, stateService.getFullLineWindSections());
        if (realRows != null) {
            return realRows;
        }
        return buildFallbackServiceAreaVehicleStats(timestamp);
    }

    /**
     * 查询交通状态分析（4.1.5）。
     *
     * 处理逻辑：
     * 1. 优先使用轨迹窗口流量折算 vehPerHour；
     * 2. 轨迹不可用时回退到风级驱动的演示值。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 交通状态列表
     */
    public List<Map<String, Object>> getTrafficStateAnalysis(long timestamp) {
        List<Map<String, Object>> realRows = trajectoryService.buildTrafficStateAnalysis(timestamp, stateService.getFullLineWindSections());
        if (realRows != null) {
            return realRows;
        }
        return buildFallbackTrafficStateAnalysis();
    }

    /**
     * 查询断面参数检测结果（4.1.2）。
     *
     * 处理逻辑：
     * 1. 优先使用轨迹窗口末时刻在途车辆与平均速度；
     * 2. 轨迹不可用时回退到规则生成；
     * 3. 支持 direction 过滤（1 下行 / 2 上行）。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param direction 可选方向过滤：1 下行，2 上行
     * @return 断面检测结果列表
     */
    public List<Map<String, Object>> getSectionParameterDetections(long timestamp, Integer direction) {
        List<Map<String, Object>> realRows = trajectoryService.buildSectionParameterDetections(timestamp, stateService.getFullLineWindSections());
        if (realRows != null) {
            return filterByDirection(realRows, direction);
        }
        return buildFallbackSectionParameterDetections(timestamp, direction);
    }

    /**
     * 查询事件检测信息（4.1.3）。
     *
     * 处理逻辑：
     * 1. 优先使用轨迹末点识别（超速/停驶）；
     * 2. 轨迹不可用时回退到示例事件；
     * 3. 有轨迹但无事件时返回空列表（不再强制造数）。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 事件列表
     */
    public List<Map<String, Object>> getEventDetectionInfos(long timestamp) {
        List<Map<String, Object>> realRows = trajectoryService.buildEventDetections(
                timestamp,
                stateService.getFullLineWindSections(),
                stateService.getSpeedThresholdByWindLevel()
        );
        if (realRows != null) {
            return realRows;
        }
        return buildFallbackEventDetections(timestamp);
    }

    /**
     * 方向过滤工具。
     */
    private List<Map<String, Object>> filterByDirection(List<Map<String, Object>> rows, Integer direction) {
        if (direction == null) {
            return rows;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int rowDirection = stateService.intValue(row.get("direction"), 1);
            if (rowDirection == direction) {
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 轨迹不可用时的服务区统计回退值。
     */
    private List<Map<String, Object>> buildFallbackServiceAreaVehicleStats(long timestamp) {
        Set<String> serviceAreas = new LinkedHashSet<>();
        for (Map<String, Object> section : stateService.getFullLineWindSections()) {
            String segmentName = stateService.stringValue(section.get("segmentName"));
            if (segmentName.contains("服务区")) {
                serviceAreas.add(segmentName);
            }
        }
        if (serviceAreas.isEmpty()) {
            serviceAreas.add("红山口服务区南");
            serviceAreas.add("红山口服务区北");
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

    /**
     * 轨迹不可用时的交通状态回退值。
     */
    private List<Map<String, Object>> buildFallbackTrafficStateAnalysis() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> section : stateService.getFullLineWindSections()) {
            String segment = stateService.stringValue(section.get("segmentName"));
            int direction = stateService.intValue(section.get("direction"), 1);
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

    /**
     * 轨迹不可用时的断面参数回退值。
     */
    private List<Map<String, Object>> buildFallbackSectionParameterDetections(long timestamp, Integer direction) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> section : stateService.getFullLineWindSections()) {
            int sectionDirection = stateService.intValue(section.get("direction"), 1);
            if (direction != null && sectionDirection != direction) {
                continue;
            }

            int realWindLevel = stateService.intValue(section.get("realWindLevel"), 6);
            int avgSpeed = Math.max(40, 95 - realWindLevel * 5);
            String congestionStatus = avgSpeed < 60 ? "CONGESTED" : (avgSpeed <= 80 ? "SLOW" : "SMOOTH");
            int currentVehicles = 20 + Math.abs(stateService.stringValue(section.get("segmentId")).hashCode() % 45);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", timestamp);
            row.put("segmentId", section.get("segmentId"));
            row.put("segment", section.get("segmentName"));
            row.put("direction", sectionDirection);
            row.put("currentVehicleCount", currentVehicles);
            row.put("avgSpeedKmPerHour", avgSpeed);
            row.put("congestionStatus", congestionStatus);
            row.put("updateIntervalMin", 5);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 轨迹不可用时的事件检测回退值。
     */
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
                "vehiclePlate", "新A8F21X",
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
                    "vehiclePlate", "新A3K92M",
                    "thresholdSpeedKmPerHour", 0,
                    "status", "UNPROCESSED",
                    "timestamp", timestamp
            ));
        }
        return rows;
    }
}
