package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Request.WindThresholdUpdateReq;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DataParamParseUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screendbmysqlsx.Model.Parameters;
import com.wut.screendbmysqlsx.Model.RoadSegmentStatic;
import com.wut.screendbmysqlsx.Model.Traj;
import com.wut.screendbmysqlsx.Service.ParametersService;
import com.wut.screendbmysqlsx.Service.RoadSegmentStaticService;
import com.wut.screendbmysqlsx.Service.TrajService;
import com.wut.screenwebsx.Config.DockingInterfaceConfig.Docking;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 第四章接口聚合服务。
 * <p>
 * 该服务集中维护 4.1~4.5 的数据装配逻辑，
 * 各模块 Service 通过组合调用本类，避免重复实现。
 */
@Component
public class ControlModuleService {
    /** 五分钟窗口（毫秒）。 */
    private static final long WINDOW_5MIN_MS = 300000L;
    /** 一小时窗口（毫秒）。 */
    private static final long HOUR_MS = 3600000L;
    /** 标准日期时间格式。 */
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 不带秒的日期时间格式。 */
    private static final DateTimeFormatter DATETIME_FMT_NO_SEC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 哈密方向标识。 */
    private static final String DIR_HAMI = "哈密";
    /** 吐鲁番方向标识。 */
    private static final String DIR_TLF = "吐鲁番";

    /** 路段静态信息服务。 */
    private final RoadSegmentStaticService roadSegmentStaticService;
    /** 断面参数服务。 */
    private final ParametersService parametersService;
    /** 轨迹服务。 */
    private final TrajService trajService;

    /** 风力阈值规则（4.2.2）。 */
    private final List<WindThresholdRule> windThresholdRules = new CopyOnWriteArrayList<>();
    /** 信息发布设施（4.3.1）。 */
    private final List<Map<String, Object>> publishFacilityRecords = new CopyOnWriteArrayList<>();
    /** 封路设备信息（4.3.2）。 */
    private final List<Map<String, Object>> closureDeviceRecords = new CopyOnWriteArrayList<>();
    /** 执勤人员信息（4.3.3）。 */
    private final List<Map<String, Object>> dutyStaffRecords = new CopyOnWriteArrayList<>();
    /** 执勤班组信息（4.3.4）。 */
    private final List<Map<String, Object>> dutyTeamRecords = new CopyOnWriteArrayList<>();
    /** 管控预案库（4.4.2）。 */
    private final List<Map<String, Object>> planLibraryRecords = new CopyOnWriteArrayList<>();
    /** 可变信息发布内容（4.4.3）。 */
    private final List<Map<String, Object>> vmsContentRecords = new CopyOnWriteArrayList<>();
    /** 人员设备调用预案库（4.4.4）。 */
    private final List<Map<String, Object>> resourceDeploymentRecords = new CopyOnWriteArrayList<>();
    /** 大风事件记录（4.5.4）。 */
    private final List<Map<String, Object>> windEventRecords = new CopyOnWriteArrayList<>();
    /** 管控区间定义（4.2.3）。 */
    private final List<ControlIntervalDef> controlIntervals = Arrays.asList(
            new ControlIntervalDef("主线起点至红山口服务区", "K3178-K3193", 0, 15000),
            new ControlIntervalDef("红山口服务区至红山口互通", "K3194-K3200", 16000, 22000),
            new ControlIntervalDef("红山口互通至主线终点", "K3201-K3204", 23000, 26000)
    );

    @Autowired
    public ControlModuleService(RoadSegmentStaticService roadSegmentStaticService,
                                ParametersService parametersService,
                                TrajService trajService) {
        this.roadSegmentStaticService = roadSegmentStaticService;
        this.parametersService = parametersService;
        this.trajService = trajService;
    }

    /**
     * 初始化第四章静态演示数据。
     */
    @PostConstruct
    public void initStaticData() {
        initWindThresholdRules();
        initPublishFacilities();
        initClosureDevices();
        initDutyStaff();
        initDutyTeams();
        initPlanLibrary();
        initVmsContent();
        initResourceDeploymentPlan();
        initWindEventRecords();
    }

    /**
     * 4.1.5 交通状态分析。
     *
     * @param timestamp 毫秒时间戳
     * @return 交通统计、风力等级和方向信息
     */
    @Docking
    public Map<String, Object> collectTrafficStatusAnalysis(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        long cumulativeCount = trajService.getDistinctTrajIdList(tableDateStr).stream()
                .map(Traj::getTrajId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long windowStart = Math.max(0L, timestamp - WINDOW_5MIN_MS);
        long vehiclesCount = trajService.getListByTimestampRange(tableDateStr, windowStart, timestamp).stream()
                .map(Traj::getTrajId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        double averageSpeed = DataParamParseUtil.getRoundValue(collectAverageSpeed(tableDateStr));
        List<RoadSegmentStatic> segmentList = roadSegmentStaticService.getEnabledSegments();
        RoadSegmentStatic maxWindSegment = segmentList.stream()
                .max(Comparator.comparingInt(segment -> calcWindLevel(segment.getStartLocationM(), timestamp, "实时")))
                .orElse(null);
        int maxWindLevel = maxWindSegment == null ? 0 : calcWindLevel(maxWindSegment.getStartLocationM(), timestamp, "实时");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp);
        result.put("cumulativeCount", cumulativeCount);
        result.put("vehiclesCount", vehiclesCount);
        result.put("averageSpeed", averageSpeed);
        result.put("windLevel", maxWindLevel <= 0 ? "" : maxWindLevel + "级");
        result.put("windStake", maxWindSegment == null ? "" : maxWindSegment.getStartStake());
        result.put("direction", maxWindSegment == null ? DIR_HAMI : maxWindSegment.getDirection());
        return result;
    }

    /**
     * 4.2.1 全线风力可视化。
     *
     * @param timestamp  毫秒时间戳
     * @param periodType 时段类型（实时/预测/4h内）
     * @return 路段风力渲染数据
     */
    @Docking
    public Map<String, Object> collectWindMainlineVisualization(long timestamp, String periodType) {
        String normalizedPeriodType = normalizePeriodType(periodType);
        List<Map<String, Object>> segmentDataList = new ArrayList<>();

        for (RoadSegmentStatic segment : roadSegmentStaticService.getEnabledSegments()) {
            int segmentWindLevel = calcWindLevel(segment.getStartLocationM(), timestamp, normalizedPeriodType);
            int maxWindLevel4h = Math.min(12, segmentWindLevel + 1);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("direction", segment.getDirection());
            row.put("stake", segment.getStartStake());
            row.put("startLocation", segment.getStartLocationM());
            row.put("endLocation", segment.getStartLocationM() == null ? null : segment.getStartLocationM() + 1000);
            row.put("startStake", segment.getStartStake());
            row.put("endStake", segment.getEndStake());
            row.put("segmentType", segment.getSegmentType());
            row.put("timeStamp", DateParamParseUtil.getDateTimePickerStr(timestamp));
            row.put("periodType", normalizedPeriodType);
            row.put("segmentWindLevel", segmentWindLevel + "级");
            row.put("maxWindLevel", maxWindLevel4h + "级");
            row.put("color", getWindColor(segmentWindLevel));
            segmentDataList.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("periodType", normalizedPeriodType);
        data.put("segmentList", segmentDataList);
        return data;
    }

    /**
     * 4.2.2 查询风力限速阈值配置。
     */
    @Docking
    public Map<String, Object> collectWindSpeedThresholdConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ruleList", windThresholdRules.stream().map(this::toThresholdMap).collect(Collectors.toList()));
        data.put("lastUpdateTime", DateParamParseUtil.getDateTimePickerStr(System.currentTimeMillis()));
        return data;
    }

    /**
     * 4.2.2 更新风力限速阈值配置。
     *
     * @param req 阈值更新请求
     * @return 更新后的规则列表
     */
    @Docking
    public Map<String, Object> updateWindSpeedThreshold(WindThresholdUpdateReq req) {
        if (req == null || !hasText(req.getControlLevel())) {
            return null;
        }
        WindThresholdRule target = windThresholdRules.stream()
                .filter(rule -> Objects.equals(rule.controlLevel, req.getControlLevel()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return null;
        }
        if (hasText(req.getWindRange())) {
            target.windRange = req.getWindRange().trim();
        }
        if (req.getLightVehicleSpeedLimit() != null) {
            target.lightVehicleSpeedLimit = req.getLightVehicleSpeedLimit();
        }
        if (req.getHeavyVehicleSpeedLimit() != null) {
            target.heavyVehicleSpeedLimit = req.getHeavyVehicleSpeedLimit();
        }
        target.updateTime = LocalDateTime.now();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("updatedRule", toThresholdMap(target));
        data.put("ruleList", windThresholdRules.stream().map(this::toThresholdMap).collect(Collectors.toList()));
        return data;
    }

    /**
     * 4.2.3 风力时空影响判断。
     *
     * @param timestamp  毫秒时间戳
     * @param periodType 时段类型（实时/未来4h/全部）
     * @param direction  方向筛选
     * @return 管控区间影响判断结果
     */
    @Docking
    public Map<String, Object> collectWindSpacetimeImpact(long timestamp, String periodType, String direction) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        String normalizedPeriodType = normalizeSpacetimePeriodType(periodType);
        double baseTrafficVolume = collectAverageStream(tableDateStr);
        List<RoadSegmentStatic> segmentList = roadSegmentStaticService.getEnabledSegments();
        List<Map<String, Object>> recordList = new ArrayList<>();

        for (ControlIntervalDef intervalDef : controlIntervals) {
            for (String currentDirection : Arrays.asList(DIR_HAMI, DIR_TLF)) {
                if (hasText(direction) && !isDirectionMatch(currentDirection, direction)) {
                    continue;
                }

                if ("全部".equals(normalizedPeriodType) || "实时".equals(normalizedPeriodType)) {
                    recordList.add(buildSpacetimeRecord(intervalDef, currentDirection, timestamp, "实时", baseTrafficVolume, segmentList));
                }
                if ("全部".equals(normalizedPeriodType) || "未来4h".equals(normalizedPeriodType)) {
                    recordList.add(buildSpacetimeRecord(intervalDef, currentDirection, timestamp, "未来4h", baseTrafficVolume, segmentList));
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("periodType", normalizedPeriodType);
        data.put("recordList", recordList);
        return data;
    }

    /**
     * 4.2.4 大风数据查询。
     *
     * @param timestamp  毫秒时间戳
     * @param periodType 查询类型（实时/历史/预测）
     * @param direction  方向筛选
     * @return 大风查询结果
     */
    @Docking
    public Map<String, Object> collectWindDataQuery(long timestamp, String periodType, String direction) {
        String normalizedPeriodType = normalizeQueryPeriodType(periodType);
        List<Long> timePoints = buildWindQueryTimePoints(timestamp, normalizedPeriodType);
        List<RoadSegmentStatic> segmentList = roadSegmentStaticService.getEnabledSegments();
        if (hasText(direction)) {
            segmentList = segmentList.stream().filter(segment -> isDirectionMatch(segment.getDirection(), direction)).collect(Collectors.toList());
        }
        if (segmentList.size() > 10) {
            segmentList = segmentList.subList(0, 10);
        }

        List<Map<String, Object>> recordList = new ArrayList<>();
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        for (Long currentTimestamp : timePoints) {
            for (RoadSegmentStatic segment : segmentList) {
                int windLevel = calcWindLevel(segment.getStartLocationM(), currentTimestamp, "实时");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("timestamp", DateParamParseUtil.getDateTimePickerStr(currentTimestamp));
                row.put("direction", segment.getDirection());
                row.put("startStake", segment.getStartStake());
                row.put("endStake", segment.getEndStake());
                row.put("windSpeed", levelToWindSpeed(windLevel));
                row.put("windDirection", directions[Math.floorMod((segment.getStartLocationM() == null ? 0 : segment.getStartLocationM() / 1000), directions.length)]);
                row.put("expectedDuration", estimateWindDurationHours(windLevel) + "h");
                row.put("periodType", normalizedPeriodType);
                recordList.add(row);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("queryPeriodType", normalizedPeriodType);
        data.put("recordList", recordList);
        return data;
    }

    /**
     * 4.3.1 信息发布设施管理。
     */
    @Docking
    public Map<String, Object> collectPublishFacilities() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordList", deepCopyMapList(publishFacilityRecords));
        return data;
    }

    /**
     * 4.3.2 封路设备信息管理。
     */
    @Docking
    public Map<String, Object> collectClosureDevices() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordList", deepCopyMapList(closureDeviceRecords));
        return data;
    }

    /**
     * 4.3.3 执勤人员信息管理。
     */
    @Docking
    public Map<String, Object> collectDutyStaff() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordList", deepCopyMapList(dutyStaffRecords));
        return data;
    }

    /**
     * 4.3.4 执勤班组信息编组。
     */
    @Docking
    public Map<String, Object> collectDutyTeams() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordList", deepCopyMapList(dutyTeamRecords));
        return data;
    }

    /**
     * 4.4.1 管控总体原则。
     */
    @Docking
    public Map<String, Object> collectOverallPrinciples() {
        List<Map<String, Object>> principleList = new ArrayList<>();
        principleList.add(row("controlArea", "风险区段", "principle", "根据管控等级实施分车型限速，VMS与APP联合发布限速信息。"));
        principleList.add(row("controlArea", "风险区段上游出口", "principle", "风险区段限速/禁行时，采用物理封路与VMS分流。"));
        principleList.add(row("controlArea", "风险区段上游入口", "principle", "风险区段限速时引导预约，一级管控时禁止进入。"));
        principleList.add(row("controlArea", "风险区段内服务区", "principle", "一级管控触发紧急避险，引导车辆进服务区等待放行。"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("principleList", principleList);
        data.put("windControlRuleList", windThresholdRules.stream().map(this::toThresholdMap).collect(Collectors.toList()));
        return data;
    }

    /**
     * 4.4.2 管控方案预案库。
     */
    @Docking
    public Map<String, Object> collectPlanLibrary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordList", deepCopyMapList(planLibraryRecords));
        return data;
    }

    /**
     * 4.4.3 可变信息发布内容。
     *
     * @param controlLevel 管控等级筛选
     * @return 发布内容列表
     */
    @Docking
    public Map<String, Object> collectVmsContent(String controlLevel) {
        String targetControlLevel = hasText(controlLevel) ? controlLevel.trim() : "三级管控";
        List<Map<String, Object>> list = vmsContentRecords.stream()
                .filter(item -> Objects.equals(item.get("controlLevel"), targetControlLevel))
                .map(LinkedHashMap::new)
                .collect(Collectors.toList());
        if (CollectionEmptyUtil.forList(list)) {
            list = vmsContentRecords.stream().map(LinkedHashMap::new).collect(Collectors.toList());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("controlLevel", targetControlLevel);
        data.put("recordList", list);
        return data;
    }

    /**
     * 4.4.4 人员设备调用预案库。
     */
    @Docking
    public Map<String, Object> collectResourceDeploymentPlan() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordList", deepCopyMapList(resourceDeploymentRecords));
        return data;
    }

    /**
     * 4.5.1 管控执行流程。
     */
    @Docking
    public Map<String, Object> collectExecutionProcess() {
        List<Map<String, Object>> stepList = new ArrayList<>();
        stepList.add(row("stepNo", 1, "stepName", "风力等级识别", "description", "根据实时风速判断风力等级并匹配管控等级。"));
        stepList.add(row("stepNo", 2, "stepName", "预案匹配", "description", "读取预案库，匹配对应管控方案模板。"));
        stepList.add(row("stepNo", 3, "stepName", "方案生成", "description", "结合未来24小时风力预测，生成可执行方案。"));
        stepList.add(row("stepNo", 4, "stepName", "人工确认", "description", "管理人员确认并发布方案。"));
        stepList.add(row("stepNo", 5, "stepName", "消息下发", "description", "通过短信/电话/邮件等方式推送给执行人员。"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stepList", stepList);
        return data;
    }

    /**
     * 4.5.2 管控方案生成。
     *
     * @param timestamp 毫秒时间戳
     * @param direction 方向
     * @return 自动生成的管控方案
     */
    @Docking
    public Map<String, Object> collectPlanGeneration(long timestamp, String direction) {
        String normalizedDirection = hasText(direction) ? direction.trim() : DIR_HAMI;
        String controlLevel = getDirectionMaxControlLevel(timestamp, normalizedDirection);
        Map<String, Object> planTemplate = findPlanTemplate(controlLevel);
        WindThresholdRule thresholdRule = getThresholdRuleByControlLevel(controlLevel);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("direction", normalizedDirection + "方向");
        data.put("publishPeriod", DateParamParseUtil.getDateTimePickerStr(timestamp) + " - " + DateParamParseUtil.getDateTimePickerStr(timestamp + 4 * HOUR_MS));
        data.put("segmentInterval", planTemplate.getOrDefault("innerSegmentPlan", "主线区间"));
        data.put("upstreamIntervalControlLevel", getLowerControlLevel(controlLevel));
        data.put("controlLevel", controlLevel);
        data.put("innerVmsContent", planTemplate.getOrDefault("innerSegmentPlan", ""));
        data.put("upstreamExitVmsContent", planTemplate.getOrDefault("upstreamExitPlan", ""));
        data.put("upstreamStationVmsContent", planTemplate.getOrDefault("upstreamEntrancePlan", ""));
        data.put("serviceAreaDuty", "三班组长");
        data.put("closureContact", "红山口服务区南仓库");
        data.put("heavyVehicleLimit", formatVehicleAction(thresholdRule == null ? 0 : thresholdRule.heavyVehicleSpeedLimit));
        data.put("lightVehicleLimit", formatVehicleAction(thresholdRule == null ? 0 : thresholdRule.lightVehicleSpeedLimit));
        data.put("serviceAreaUnlockWindLevel", "11级");
        data.put("closureUnlockWindLevel", "9级");
        data.put("speedUnlockWindLevel", "6级");
        return data;
    }

    /**
     * 4.5.3 方案自动更新。
     *
     * @param timestamp 毫秒时间戳
     * @param direction 方向
     * @return 方案自动更新结果
     */
    @Docking
    public Map<String, Object> collectPlanAutoUpdate(long timestamp, String direction) {
        String normalizedDirection = hasText(direction) ? direction.trim() : DIR_HAMI;
        List<RoadSegmentStatic> segmentList = roadSegmentStaticService.getEnabledSegments();
        List<Map<String, Object>> recordList = new ArrayList<>();

        for (int i = 0; i < controlIntervals.size(); i++) {
            ControlIntervalDef intervalDef = controlIntervals.get(i);
            int currentWindLevel = getIntervalMaxWindLevel(segmentList, intervalDef, normalizedDirection, timestamp, "实时");
            int recommendWindLevel = getIntervalMaxWindLevel(segmentList, intervalDef, normalizedDirection, timestamp, "未来4h");
            String currentLevel = mapWindLevelToControlLevel(currentWindLevel);
            String recommendLevel = mapWindLevelToControlLevel(recommendWindLevel);
            WindThresholdRule recommendRule = getThresholdRuleByControlLevel(recommendLevel);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("segmentInterval", intervalDef.controlInterval + (i + 1));
            row.put("controlLevel", currentLevel);
            row.put("recommendedLevel", recommendLevel);
            row.put("heavyVehicle", formatVehicleAction(recommendRule == null ? 0 : recommendRule.heavyVehicleSpeedLimit));
            row.put("lightVehicle", formatVehicleAction(recommendRule == null ? 0 : recommendRule.lightVehicleSpeedLimit));
            row.put("dutyStaff", i % 2 == 0 ? "3班组长" : "/");
            row.put("estimatedDuration", (2 + i) + "h");
            row.put("needAdjust", !Objects.equals(currentLevel, recommendLevel));
            recordList.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("direction", normalizedDirection);
        data.put("recordList", recordList);
        return data;
    }

    /**
     * 4.5.4 大风事件记录查询。
     *
     * @return 过滤后的事件记录
     */
    @Docking
    public Map<String, Object> collectWindEventRecords(String startStake,
                                                       String endStake,
                                                       String direction,
                                                       String controlPlan,
                                                       String startTime,
                                                       String endTime) {
        Long startMeter = parseStakeToMeter(startStake);
        Long endMeter = parseStakeToMeter(endStake);
        LocalDateTime startDateTime = parseDateTime(startTime);
        LocalDateTime endDateTime = parseDateTime(endTime);

        List<Map<String, Object>> recordList = windEventRecords.stream()
                .filter(record -> filterWindEventRecord(record, startMeter, endMeter, direction, controlPlan, startDateTime, endDateTime))
                .map(LinkedHashMap::new)
                .peek(this::fillWindEventDurationHours)
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", recordList.size());
        data.put("recordList", recordList);
        return data;
    }

    private Map<String, Object> buildSpacetimeRecord(ControlIntervalDef intervalDef,
                                                     String direction,
                                                     long timestamp,
                                                     String periodType,
                                                     double baseTrafficVolume,
                                                     List<RoadSegmentStatic> segmentList) {
        int windLevel = getIntervalMaxWindLevel(segmentList, intervalDef, direction, timestamp, periodType);
        int trafficVolume = (int) DataParamParseUtil.getRoundValue(baseTrafficVolume + (intervalDef.startMeter / 5000.0));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("controlInterval", intervalDef.controlInterval);
        row.put("stakeRange", intervalDef.stakeRange);
        row.put("direction", direction);
        row.put("baseTime", DateParamParseUtil.getTimeDataStr(timestamp));
        row.put("periodType", periodType);
        row.put("windLevel", windLevel + "级");
        row.put("controlLevel", mapWindLevelToControlLevel(windLevel));
        row.put("trafficVolume", Math.max(0, trafficVolume));
        return row;
    }

    private int getIntervalMaxWindLevel(List<RoadSegmentStatic> segmentList,
                                        ControlIntervalDef intervalDef,
                                        String direction,
                                        long timestamp,
                                        String periodType) {
        int maxWindLevel = 0;
        for (RoadSegmentStatic segment : segmentList) {
            if (!isDirectionMatch(segment.getDirection(), direction)) {
                continue;
            }
            Integer startLocation = segment.getStartLocationM();
            if (startLocation == null) {
                continue;
            }
            int endLocation = startLocation + 1000;
            if (endLocation <= intervalDef.startMeter || startLocation >= intervalDef.endMeter) {
                continue;
            }
            maxWindLevel = Math.max(maxWindLevel, calcWindLevel(startLocation, timestamp, periodType));
        }
        return maxWindLevel == 0 ? calcWindLevel(intervalDef.startMeter, timestamp, periodType) : maxWindLevel;
    }

    private double collectAverageSpeed(String tableDateStr) {
        List<Parameters> parametersList = parametersService.getListByDate(tableDateStr);
        if (CollectionEmptyUtil.forList(parametersList)) {
            return 0;
        }
        return parametersList.stream().mapToDouble(Parameters::getSpeed).average().orElse(0);
    }

    private double collectAverageStream(String tableDateStr) {
        List<Parameters> parametersList = parametersService.getListByDate(tableDateStr);
        if (CollectionEmptyUtil.forList(parametersList)) {
            return 0;
        }
        return parametersList.stream().mapToDouble(Parameters::getStream).average().orElse(0);
    }

    private int calcWindLevel(Integer startLocation, long timestamp, String periodType) {
        int locationFactor = startLocation == null ? 0 : Math.max(0, startLocation / 1000);
        int timeFactor = (int) Math.floorMod(timestamp / WINDOW_5MIN_MS, 7);
        int base = 6 + Math.floorMod(locationFactor + timeFactor, 7);
        if ("未来4h".equals(periodType) || "预测".equals(periodType)) {
            return Math.min(12, base + 1);
        }
        if ("4h内".equals(periodType)) {
            return Math.min(12, base + 2);
        }
        return base;
    }

    private String normalizePeriodType(String periodType) {
        if (!hasText(periodType)) {
            return "实时";
        }
        String value = periodType.trim();
        if ("预测".equals(value) || "未来4h".equals(value) || "4h内".equals(value) || "实时".equals(value)) {
            return value;
        }
        return "实时";
    }

    private String normalizeSpacetimePeriodType(String periodType) {
        if (!hasText(periodType)) {
            return "全部";
        }
        String value = periodType.trim();
        if ("实时".equals(value) || "未来4h".equals(value) || "全部".equals(value)) {
            return value;
        }
        return "全部";
    }

    private String normalizeQueryPeriodType(String periodType) {
        if (!hasText(periodType)) {
            return "实时";
        }
        String value = periodType.trim();
        if ("实时".equals(value) || "历史".equals(value) || "预测".equals(value)) {
            return value;
        }
        return "实时";
    }

    private List<Long> buildWindQueryTimePoints(long timestamp, String periodType) {
        List<Long> list = new ArrayList<>();
        if ("实时".equals(periodType)) {
            list.add(timestamp);
            return list;
        }
        if ("历史".equals(periodType)) {
            for (int i = 5; i >= 0; i--) {
                list.add(timestamp - i * HOUR_MS);
            }
            return list;
        }
        for (int i = 0; i <= 5; i++) {
            list.add(timestamp + i * HOUR_MS);
        }
        return list;
    }

    private String getWindColor(int windLevel) {
        if (windLevel >= 12) {
            return "#6A1B9A";
        }
        if (windLevel >= 10) {
            return "#1565C0";
        }
        if (windLevel >= 8) {
            return "#4FC3F7";
        }
        if (windLevel >= 7) {
            return "#00B050";
        }
        return "#9E9E9E";
    }

    private String mapWindLevelToControlLevel(int windLevel) {
        if (windLevel >= 12) {
            return "一级管控";
        }
        if (windLevel >= 11) {
            return "二级管控";
        }
        if (windLevel >= 9) {
            return "三级管控";
        }
        if (windLevel >= 7) {
            return "四级管控";
        }
        return "五级管控";
    }

    private double levelToWindSpeed(int windLevel) {
        return switch (windLevel) {
            case 12 -> 34.0;
            case 11 -> 30.0;
            case 10 -> 26.0;
            case 9 -> 22.0;
            case 8 -> 18.0;
            case 7 -> 15.0;
            default -> 12.0;
        };
    }

    private int estimateWindDurationHours(int windLevel) {
        if (windLevel >= 11) {
            return 6;
        }
        if (windLevel >= 9) {
            return 4;
        }
        if (windLevel >= 7) {
            return 2;
        }
        return 1;
    }

    private String getDirectionMaxControlLevel(long timestamp, String direction) {
        List<RoadSegmentStatic> segmentList = roadSegmentStaticService.getEnabledSegments();
        int maxWindLevel = segmentList.stream()
                .filter(segment -> isDirectionMatch(segment.getDirection(), direction))
                .mapToInt(segment -> calcWindLevel(segment.getStartLocationM(), timestamp, "实时"))
                .max()
                .orElse(6);
        return mapWindLevelToControlLevel(maxWindLevel);
    }

    private WindThresholdRule getThresholdRuleByControlLevel(String controlLevel) {
        return windThresholdRules.stream()
                .filter(rule -> Objects.equals(rule.controlLevel, controlLevel))
                .findFirst()
                .orElse(null);
    }

    private String formatVehicleAction(int speedLimit) {
        if (speedLimit <= 0) {
            return "拒绝通行";
        }
        return speedLimit + "km/h";
    }

    private Map<String, Object> findPlanTemplate(String controlLevel) {
        for (Map<String, Object> planRecord : planLibraryRecords) {
            if (Objects.equals(planRecord.get("controlLevel"), controlLevel)) {
                return new LinkedHashMap<>(planRecord);
            }
        }
        return row(
                "controlLevel", "三级管控",
                "windLevel", "9-10级",
                "innerSegmentPlan", "小客车限速60km/h，客货车限速40km/h",
                "upstreamExitPlan", "物理封路+可变信息诱导",
                "upstreamEntrancePlan", "所有车型预约通行",
                "serviceAreaPlan", "同风险区段内方案"
        );
    }

    private String getLowerControlLevel(String controlLevel) {
        return switch (controlLevel) {
            case "一级管控" -> "二级管控";
            case "二级管控" -> "三级管控";
            case "三级管控" -> "四级管控";
            case "四级管控" -> "五级管控";
            default -> "五级管控";
        };
    }

    private boolean filterWindEventRecord(Map<String, Object> record,
                                          Long startMeter,
                                          Long endMeter,
                                          String direction,
                                          String controlPlan,
                                          LocalDateTime startDateTime,
                                          LocalDateTime endDateTime) {
        if (hasText(direction) && !isDirectionMatch((String) record.get("direction"), direction)) {
            return false;
        }
        if (hasText(controlPlan) && !containsText((String) record.get("managementPlan"), controlPlan)) {
            return false;
        }

        Long locationMeter = parseStakeToMeter((String) record.get("incidentLocation"));
        if (startMeter != null && locationMeter != null && locationMeter < startMeter) {
            return false;
        }
        if (endMeter != null && locationMeter != null && locationMeter > endMeter) {
            return false;
        }

        LocalDateTime occurrenceTime = parseDateTime((String) record.get("timeOfOccurrence"));
        if (startDateTime != null && occurrenceTime != null && occurrenceTime.isBefore(startDateTime)) {
            return false;
        }
        if (endDateTime != null && occurrenceTime != null && occurrenceTime.isAfter(endDateTime)) {
            return false;
        }
        return true;
    }

    private void fillWindEventDurationHours(Map<String, Object> record) {
        LocalDateTime start = parseDateTime((String) record.get("timeOfOccurrence"));
        LocalDateTime end = parseDateTime((String) record.get("conclusionTime"));
        if (start == null || end == null || end.isBefore(start)) {
            record.put("durationHours", 0);
            return;
        }
        record.put("durationHours", Duration.between(start, end).toHours());
    }

    private LocalDateTime parseDateTime(String text) {
        if (!hasText(text)) {
            return null;
        }
        String value = text.trim().replace('/', '-');
        List<DateTimeFormatter> formatterList = Arrays.asList(
                DATETIME_FMT,
                DATETIME_FMT_NO_SEC,
                DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-M-d HH:mm")
        );
        for (DateTimeFormatter formatter : formatterList) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Long parseStakeToMeter(String stakeText) {
        if (!hasText(stakeText)) {
            return null;
        }
        String value = stakeText.toUpperCase().replace("桩号", "").replace("K", "").trim();
        try {
            if (value.contains("+")) {
                String[] parts = value.split("\\+");
                if (parts.length == 2) {
                    long km = Long.parseLong(parts[0].replaceAll("[^0-9]", ""));
                    long meter = Long.parseLong(parts[1].replaceAll("[^0-9]", ""));
                    return km * 1000 + meter;
                }
            }
            String pureNumber = value.replaceAll("[^0-9]", "");
            if (!pureNumber.isEmpty()) {
                return Long.parseLong(pureNumber);
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isDirectionMatch(String sourceDirection, String targetDirection) {
        if (!hasText(targetDirection)) {
            return true;
        }
        if (!hasText(sourceDirection)) {
            return false;
        }
        String source = sourceDirection.trim();
        String target = targetDirection.trim();
        return source.contains(target) || target.contains(source);
    }

    private boolean containsText(String source, String target) {
        if (!hasText(target)) {
            return true;
        }
        if (!hasText(source)) {
            return false;
        }
        return source.contains(target.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Map<String, Object> toThresholdMap(WindThresholdRule rule) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("controlLevel", rule.controlLevel);
        data.put("windRange", rule.windRange);
        data.put("lightVehicleSpeedLimit", rule.lightVehicleSpeedLimit);
        data.put("heavyVehicleSpeedLimit", rule.heavyVehicleSpeedLimit);
        data.put("updateTime", rule.updateTime == null ? "" : rule.updateTime.format(DATETIME_FMT));
        return data;
    }

    /**
     * 初始化风力限速阈值规则（静态配置）。
     */
    private void initWindThresholdRules() {
        if (!windThresholdRules.isEmpty()) {
            return;
        }
        windThresholdRules.add(new WindThresholdRule("五级管控", "7级以下", 120, 80));
        windThresholdRules.add(new WindThresholdRule("四级管控", "7-8级", 80, 60));
        windThresholdRules.add(new WindThresholdRule("三级管控", "9-10级", 60, 40));
        windThresholdRules.add(new WindThresholdRule("二级管控", "11级", 60, 0));
        windThresholdRules.add(new WindThresholdRule("一级管控", "12级", 0, 0));
    }

    /**
     * 初始化信息发布设施静态数据。
     */
    private void initPublishFacilities() {
        if (!publishFacilityRecords.isEmpty()) {
            return;
        }
        publishFacilityRecords.add(row("deviceStake", "K3180", "direction", DIR_TLF, "deviceId", "t3ghjd1", "deviceType", "可变信息标志", "serviceSection", "主线"));
        publishFacilityRecords.add(row("deviceStake", "K3191+800", "direction", DIR_TLF, "deviceId", "t3ghjd2", "deviceType", "可变信息标志", "serviceSection", "红山口服务区前"));
        publishFacilityRecords.add(row("deviceStake", "K3196+450", "direction", DIR_TLF, "deviceId", "t3ghjd3", "deviceType", "可变信息标志", "serviceSection", "红山口互通前"));
        publishFacilityRecords.add(row("deviceStake", "K3180", "direction", DIR_HAMI, "deviceId", "h3ghjd1", "deviceType", "可变信息标志", "serviceSection", "主线"));
        publishFacilityRecords.add(row("deviceStake", "K3194+515", "direction", DIR_HAMI, "deviceId", "h3ghjd2", "deviceType", "可变信息标志", "serviceSection", "红山口服务区前"));
        publishFacilityRecords.add(row("deviceStake", "K3199+500", "direction", DIR_HAMI, "deviceId", "h3ghjd3", "deviceType", "可变信息标志", "serviceSection", "红山口互通前"));
    }

    /**
     * 初始化封路设备静态数据。
     */
    private void initClosureDevices() {
        if (!closureDeviceRecords.isEmpty()) {
            return;
        }
        closureDeviceRecords.add(row("warehouseLocation", "交警队仓库", "equipmentType", "三角锥、防撞桶、限速标识", "quantity", 10, "availabilityStatus", "不可用"));
        closureDeviceRecords.add(row("warehouseLocation", "服务区南仓库", "equipmentType", "三角锥、防撞桶", "quantity", 10, "availabilityStatus", "可用"));
        closureDeviceRecords.add(row("warehouseLocation", "服务区北仓库", "equipmentType", "三角锥、防撞桶", "quantity", 10, "availabilityStatus", "可用"));
    }

    /**
     * 初始化执勤人员静态数据。
     */
    private void initDutyStaff() {
        if (!dutyStaffRecords.isEmpty()) {
            return;
        }
        dutyStaffRecords.add(row("staffId", "Staff001", "staffName", "张三", "onDutyStatus", "在岗", "team", "班组1", "phoneNumber", "15676648462"));
        dutyStaffRecords.add(row("staffId", "Staff002", "staffName", "张四", "onDutyStatus", "在岗", "team", "班组1", "phoneNumber", "15676648463"));
        dutyStaffRecords.add(row("staffId", "Staff003", "staffName", "张五", "onDutyStatus", "在岗", "team", "班组2", "phoneNumber", "15676648464"));
        dutyStaffRecords.add(row("staffId", "Staff004", "staffName", "张六", "onDutyStatus", "不在岗", "team", "未分配", "phoneNumber", "15676648465"));
        dutyStaffRecords.add(row("staffId", "Staff005", "staffName", "张七", "onDutyStatus", "在岗", "team", "班组3", "phoneNumber", "15676648466"));
    }

    /**
     * 初始化执勤班组静态数据。
     */
    private void initDutyTeams() {
        if (!dutyTeamRecords.isEmpty()) {
            return;
        }
        dutyTeamRecords.add(row("team", "班组1", "teamMembers", "张三,张四", "teamLeader", "张三", "responsibleNode", "红山口互通南", "dispatchStatus", "否"));
        dutyTeamRecords.add(row("team", "班组2", "teamMembers", "张五", "teamLeader", "张五", "responsibleNode", "红山口服务区南", "dispatchStatus", "是"));
        dutyTeamRecords.add(row("team", "班组3", "teamMembers", "张七", "teamLeader", "张七", "responsibleNode", "红山口互通北", "dispatchStatus", "否"));
    }

    /**
     * 初始化管控预案库静态数据。
     */
    private void initPlanLibrary() {
        if (!planLibraryRecords.isEmpty()) {
            return;
        }
        planLibraryRecords.add(row(
                "controlLevel", "五级管控",
                "windLevel", "7级以下",
                "innerSegmentPlan", "小客车限速120km/h，客货车限速80km/h",
                "upstreamExitPlan", "所有车辆正常通行",
                "upstreamEntrancePlan", "所有车型正常放行",
                "serviceAreaPlan", "同风险区段内方案"
        ));
        planLibraryRecords.add(row(
                "controlLevel", "四级管控",
                "windLevel", "7-8级",
                "innerSegmentPlan", "小客车限速80km/h，客货车限速60km/h",
                "upstreamExitPlan", "小客车限速100km/h，客货车限速70km/h",
                "upstreamEntrancePlan", "所有车型正常放行",
                "serviceAreaPlan", "同风险区段内方案"
        ));
        planLibraryRecords.add(row(
                "controlLevel", "三级管控",
                "windLevel", "9-10级",
                "innerSegmentPlan", "小客车限速60km/h，客货车限速40km/h",
                "upstreamExitPlan", "物理封路+可变信息诱导",
                "upstreamEntrancePlan", "所有车型预约通行",
                "serviceAreaPlan", "同风险区段内方案"
        ));
        planLibraryRecords.add(row(
                "controlLevel", "二级管控",
                "windLevel", "11级",
                "innerSegmentPlan", "限速20km/h",
                "upstreamExitPlan", "物理封路+可变信息诱导",
                "upstreamEntrancePlan", "小客车预约通行，客货车禁止通行",
                "serviceAreaPlan", "同风险区段内方案"
        ));
        planLibraryRecords.add(row(
                "controlLevel", "一级管控",
                "windLevel", "12级",
                "innerSegmentPlan", "限速20km/h（滞留车辆）",
                "upstreamExitPlan", "物理封路+可变信息诱导",
                "upstreamEntrancePlan", "所有车型禁止通行",
                "serviceAreaPlan", "物理封路+可变信息诱导"
        ));
    }

    /**
     * 初始化可变信息发布内容静态数据。
     */
    private void initVmsContent() {
        if (!vmsContentRecords.isEmpty()) {
            return;
        }
        vmsContentRecords.add(row("controlLevel", "三级管控", "scene", "管控路段内方案", "publishContent", "大风预警，限速通行，小客车60km/h，客货车40km/h。"));
        vmsContentRecords.add(row("controlLevel", "三级管控", "scene", "管控路段上游出口及收费站方案", "publishContent", "前方大风区，建议驶离并按指引分流。"));
        vmsContentRecords.add(row("controlLevel", "三级管控", "scene", "管控路段上游收费站方案", "publishContent", "前方大风区，请预约后通行。"));
        vmsContentRecords.add(row("controlLevel", "二级管控", "scene", "管控路段内方案", "publishContent", "大风强预警，限速20km/h。"));
        vmsContentRecords.add(row("controlLevel", "一级管控", "scene", "管控路段内方案", "publishContent", "极端大风，禁止通行，请驶入服务区避险。"));
    }

    /**
     * 初始化人员设备调用预案静态数据。
     */
    private void initResourceDeploymentPlan() {
        if (!resourceDeploymentRecords.isEmpty()) {
            return;
        }
        resourceDeploymentRecords.add(row("controlSegment", "吐鲁番方向红山口互通出口", "controlLocation", "K3199+100", "contactStaff", "三班组长", "equipmentLocation", "红山口服务区南仓库"));
        resourceDeploymentRecords.add(row("controlSegment", "吐鲁番方向红山口互通入口", "controlLocation", "AK0+000", "contactStaff", "三班组长", "equipmentLocation", "红山口服务区北仓库"));
        resourceDeploymentRecords.add(row("controlSegment", "吐鲁番方向七可台互通入口", "controlLocation", "K3283+900", "contactStaff", "一班组长", "equipmentLocation", "七克台东服务区北仓库"));
        resourceDeploymentRecords.add(row("controlSegment", "吐鲁番方向七可台互通出口", "controlLocation", "AK0+268", "contactStaff", "一班组长", "equipmentLocation", "七克台东服务区南仓库"));
        resourceDeploymentRecords.add(row("controlSegment", "吐鲁番方向沙墩子互通出口", "controlLocation", "K3119+300", "contactStaff", "五班组长", "equipmentLocation", "沙敦子服务区南仓库"));
    }

    /**
     * 初始化大风事件记录示例数据。
     */
    private void initWindEventRecords() {
        if (!windEventRecords.isEmpty()) {
            return;
        }
        windEventRecords.add(row(
                "incidentLocation", "K3199+100",
                "windSpeedScale", "10级大风",
                "managementPlan", "预约/封路/限速",
                "timeOfOccurrence", "2026-01-21 18:00:00",
                "conclusionTime", "2026-01-22 02:00:00",
                "controlPerimeter", "红山口服务区至红山口互通",
                "onDutyPersonnel", "张三、李四",
                "direction", DIR_TLF
        ));
        windEventRecords.add(row(
                "incidentLocation", "K3186+200",
                "windSpeedScale", "8级大风",
                "managementPlan", "限速",
                "timeOfOccurrence", "2026-01-20 09:00:00",
                "conclusionTime", "2026-01-20 13:00:00",
                "controlPerimeter", "主线起点至红山口服务区",
                "onDutyPersonnel", "王五、赵六",
                "direction", DIR_HAMI
        ));
        windEventRecords.add(row(
                "incidentLocation", "K3202+600",
                "windSpeedScale", "12级大风",
                "managementPlan", "封路/禁止通行",
                "timeOfOccurrence", "2026-01-18 04:00:00",
                "conclusionTime", "2026-01-18 12:00:00",
                "controlPerimeter", "红山口互通至主线终点",
                "onDutyPersonnel", "孙七、周八",
                "direction", DIR_TLF
        ));
    }

    private List<Map<String, Object>> deepCopyMapList(List<Map<String, Object>> source) {
        return source.stream().map(LinkedHashMap::new).collect(Collectors.toList());
    }

    private Map<String, Object> row(Object... kv) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            row.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return row;
    }

    /**
     * 风力限速阈值规则模型。
     */
    private static class WindThresholdRule {
        private final String controlLevel;
        private String windRange;
        private int lightVehicleSpeedLimit;
        private int heavyVehicleSpeedLimit;
        private LocalDateTime updateTime;

        private WindThresholdRule(String controlLevel, String windRange, int lightVehicleSpeedLimit, int heavyVehicleSpeedLimit) {
            this.controlLevel = controlLevel;
            this.windRange = windRange;
            this.lightVehicleSpeedLimit = lightVehicleSpeedLimit;
            this.heavyVehicleSpeedLimit = heavyVehicleSpeedLimit;
            this.updateTime = LocalDateTime.now();
        }
    }

    /**
     * 管控区间定义模型。
     */
    private static class ControlIntervalDef {
        private final String controlInterval;
        private final String stakeRange;
        private final int startMeter;
        private final int endMeter;

        private ControlIntervalDef(String controlInterval, String stakeRange, int startMeter, int endMeter) {
            this.controlInterval = controlInterval;
            this.stakeRange = stakeRange;
            this.startMeter = startMeter;
            this.endMeter = endMeter;
        }
    }
}
