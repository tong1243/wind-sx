package com.wut.screenwebsx.Service;

import com.wut.screendbmysqlsx.Model.WindData;
import com.wut.screendbmysqlsx.Service.WindDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 4.2 大风时空影响业务服务。
 *
 * 关键说明：
 * 1. 大风数据只读取真实来源（wind_data + 轨迹表）；
 * 2. 不再提供任何模拟兜底数据；
 * 3. 方向规范始终使用 1（去往哈密）与 2（去往吐鲁番）。
 */
@Service
public class WindControlWindImpactService {
    /** 去往哈密方向（下行）。 */
    private static final int DIRECTION_HAMI = 1;
    /** 去往吐鲁番方向（上行）。 */
    private static final int DIRECTION_TURPAN = 2;
    /** 4 小时时窗。 */
    private static final long WINDOW_4H_MS = 4L * 60 * 60 * 1000;
    /** 2 小时时窗。 */
    private static final long WINDOW_2H_MS = 2L * 60 * 60 * 1000;
    /** 24 小时时窗。 */
    private static final long WINDOW_24H_MS = 24L * 60 * 60 * 1000;
    private static final long WINDOW_72H_MS = 72L * 60 * 60 * 1000;
    /** 默认时间格式。 */
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** wind-observations 仅保留主线相关路段范围。 */
    private static final double WIND_OBSERVATION_RANGE_MIN = 3178D;
    private static final double WIND_OBSERVATION_RANGE_MAX = 3204D;
    /** 4.2.3 固定管控区间（统一双向使用）。 */
    private static final List<String> FIXED_IMPACT_STAKE_RANGES = List.of(
            "K3178-K3192",
            "K3192-K3197",
            "K3197-K3204"
    );

    /** 桩号提取规则，支持 K3191 与 K3191+800。 */
    private static final Pattern STAKE_PATTERN = Pattern.compile("K(\\d+(?:\\+\\d+)?)", Pattern.CASE_INSENSITIVE);

    /** 公共状态服务。 */
    private final WindControlStateService stateService;
    /** 轨迹聚合服务（用于交通量估算）。 */
    private final WindControlTrajectoryService trajectoryService;
    /** wind_data 数据服务。 */
    private final WindDataService windDataService;
    /** APP 侧小时级限速重算服务。 */
    private final WindRiskSpeedService windRiskSpeedService;

    /**
     * 构造函数。
     *
     * @param stateService 公共状态服务
     * @param trajectoryService 轨迹聚合服务
     * @param windDataService wind_data 服务
     */
    public WindControlWindImpactService(WindControlStateService stateService,
                                        WindControlTrajectoryService trajectoryService,
                                        WindDataService windDataService,
                                        WindRiskSpeedService windRiskSpeedService) {
        this.stateService = stateService;
        this.trajectoryService = trajectoryService;
        this.windDataService = windDataService;
        this.windRiskSpeedService = windRiskSpeedService;
    }

    /**
     * 查询全线风力可视化数据（4.2.1）。
     *
     * 规则：
     * 1. mode=real：读取 wind_data 的“最新快照”；
     * 2. mode=forecast：读取未来 4h 窗口并取每段最大风级；
     * 3. mode=max2h/max72h：分别读取未来 2h/72h 窗口最大风级；
     * 4. 无真实风数据时，不返回该路段记录。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param mode 模式：real/forecast/max2h/max72h（兼容 max4h/4h 历史入参）
     * @return 可视化结果
     */
    public Map<String, Object> getWindVisualization(long timestamp, String mode) {
        String finalMode = mode == null ? "real" : mode.toLowerCase(Locale.ROOT);
        if ("max4h".equals(finalMode) || "4h".equals(finalMode)) {
            finalMode = "max2h";
        }
        LocalDateTime now = toLocalDateTime(timestamp);

        List<WindData> latestRows = windDataService.listLatestSnapshot(now);
        List<WindData> future2hRows = "max2h".equals(finalMode)
                ? windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_2H_MS))
                : List.of();
        List<WindData> future72hRows = ("forecast".equals(finalMode) || "max72h".equals(finalMode))
                ? windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_72H_MS))
                : List.of();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> seg : stateService.getFullLineWindSections()) {
            String segmentName = stateService.stringValue(seg.get("segmentName"));
            String startStake = stateService.stringValue(seg.get("startStake"));
            String endStake = stateService.stringValue(seg.get("endStake"));
            int direction = stateService.intValue(seg.get("direction"), DIRECTION_HAMI);

            Integer windLevelFromDb;
            if ("forecast".equals(finalMode)) {
                windLevelFromDb = resolveForecastWindLevelFromRows(future72hRows, segmentName, direction, now);
            } else if ("max2h".equals(finalMode)) {
                windLevelFromDb = resolveMaxWindLevelFromRows(future2hRows, segmentName, direction);
            } else if ("max72h".equals(finalMode)) {
                windLevelFromDb = resolveMaxWindLevelFromRows(future72hRows, segmentName, direction);
            } else {
                windLevelFromDb = resolveMaxWindLevelFromRows(latestRows, segmentName, direction);
            }

            if (windLevelFromDb == null) {
                continue;
            }
            int windLevel = windLevelFromDb;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("segmentId", toDisplayStakeId(endStake, startStake));
            row.put("direction", direction);
            row.put("windLevel", windLevel);
            row.put("color", colorByWindLevel(windLevel));
            rows.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("mode", finalMode);
        data.put("sections", rows);
        return data;
    }

    /**
     * 查询风力限速阈值表（4.2.2）。
     *
     * 说明：此接口仍使用静态阈值配置（表1-6 + 运行时调整），与 wind_data 解耦。
     *
     * @return 阈值列表
     */
    public List<Map<String, Object>> getSpeedThresholds() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seenDesc = new HashSet<>();
        for (Integer level : new TreeSet<>(stateService.getSpeedThresholdByWindLevel().keySet())) {
            Map<String, Object> source = stateService.getSpeedThresholdByWindLevel().get(level);
            if (source == null) {
                continue;
            }
            String desc = stateService.stringValue(source.get("windLevelDesc")).trim();
            String key = desc.isEmpty() ? "WL#" + level : desc.toLowerCase(Locale.ROOT);
            if (!seenDesc.add(key)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(source);
            row.remove("dangerousGoodsSpeedLimit");
            rows.add(row);
        }
        return rows;
    }

    /**
     * 更新指定风级限速阈值。
     *
     * @param body 更新请求体
     * @return 更新后的阈值记录
     */
    public Map<String, Object> updateSpeedThreshold(Map<String, Object> body) {
        int windLevel = stateService.intValue(body.get("windLevel"), -1);
        if (windLevel < 1 || windLevel > 12) {
            throw new IllegalArgumentException("windLevel must be between 1 and 12");
        }

        Map<String, Object> existing = stateService.getSpeedThresholdByWindLevel().computeIfAbsent(windLevel, this::newThresholdByWindLevel);
        int oldControlLevel = stateService.intValue(existing.get("controlLevel"), stateService.mapWindToControlLevel(windLevel));
        int oldPassenger = stateService.intValue(existing.get("passengerSpeedLimit"), 999);
        int oldFreight = stateService.intValue(existing.get("freightSpeedLimit"), 999);

        int newControlLevel = stateService.intValue(body.get("controlLevel"), oldControlLevel);
        int newPassenger = stateService.intValue(body.get("passengerSpeedLimit"), oldPassenger);
        int newFreight = stateService.intValue(body.get("freightSpeedLimit"), oldFreight);

        if (newControlLevel < 1 || newControlLevel > 5) {
            throw new IllegalArgumentException("controlLevel must be between 1 and 5");
        }
        if (newControlLevel > oldControlLevel
                || newPassenger > oldPassenger
                || newFreight > oldFreight) {
            throw new IllegalArgumentException("threshold update must be stricter, not looser");
        }

        Map<Integer, Map<String, Object>> controlPlanLibrary = stateService.getControlPlanLibrary();
        Map<String, Object> sourcePlan = new LinkedHashMap<>(controlPlanLibrary.getOrDefault(oldControlLevel, Map.of()));

        existing.put("controlLevel", newControlLevel);
        stateService.mergeIfPresent(existing, body, "passengerSpeedLimit");
        stateService.mergeIfPresent(existing, body, "freightSpeedLimit");
        existing.put("controlLevelName", levelName(newControlLevel));
        existing.put("windLevelDesc", normalizeWindLevelDesc(newControlLevel));
        existing.put("dangerousGoodsSpeedLimit", stateService.intValue(existing.get("freightSpeedLimit"), 0));

        if (newControlLevel < oldControlLevel && !sourcePlan.isEmpty()) {
            for (int level = newControlLevel; level <= oldControlLevel; level++) {
                Map<String, Object> target = controlPlanLibrary.get(level);
                if (target == null) {
                    continue;
                }
                target.put("riskSectionPlan", sourcePlan.get("riskSectionPlan"));
                target.put("upstreamExitPlan", sourcePlan.get("upstreamExitPlan"));
                target.put("upstreamEntryPlan", sourcePlan.get("upstreamEntryPlan"));
                target.put("upstreamServiceAreaPlan", sourcePlan.get("upstreamServiceAreaPlan"));
                target.put("description", sourcePlan.get("riskSectionPlan"));
            }
        }

        stateService.persistSnapshot();

        Map<String, Object> result = new LinkedHashMap<>(existing);
        result.remove("dangerousGoodsSpeedLimit");
        try {
            result.put("appSync", windRiskSpeedService.syncSpeedLimitAfterThresholdUpdate(null));
        } catch (Exception e) {
            Map<String, Object> syncFail = new LinkedHashMap<>();
            syncFail.put("synced", false);
            syncFail.put("message", "sync app speed limits failed: " + e.getMessage());
            result.put("appSync", syncFail);
        }
        return result;
    }

    /**
     * 按管控等级编辑风力阈值映射。
     */
    public Map<String, Object> updateSpeedThresholdByControlLevel(int controlLevel, Map<String, Object> body) {
        if (controlLevel < 1 || controlLevel > 5) {
            throw new IllegalArgumentException("controlLevel must be between 1 and 5");
        }
        String windLevelDesc = stateService.stringValue(body == null ? null : body.get("windLevelDesc")).trim();
        if (windLevelDesc.isBlank()) {
            throw new IllegalArgumentException("windLevelDesc is required");
        }

        int sourceLevel = resolveControlLevelByWindLevelDesc(windLevelDesc);
        if (sourceLevel <= 0) {
            throw new IllegalArgumentException("windLevelDesc not found: " + windLevelDesc);
        }
        if (sourceLevel > controlLevel) {
            throw new IllegalArgumentException("threshold update must be stricter, not looser");
        }

        Integer reqPassenger = toNullableInt(body == null ? null : body.get("passengerSpeedLimit"));
        Integer reqFreight = toNullableInt(body == null ? null : body.get("freightSpeedLimit"));

        List<Integer> affectedWindLevels = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Object>> entry : stateService.getSpeedThresholdByWindLevel().entrySet()) {
            int windLevel = entry.getKey();
            Map<String, Object> row = entry.getValue();
            if (row == null) {
                continue;
            }
            int rowLevel = stateService.intValue(row.get("controlLevel"), -1);
            if (rowLevel != sourceLevel) {
                continue;
            }

            int oldPassenger = stateService.intValue(row.get("passengerSpeedLimit"), 999);
            int oldFreight = stateService.intValue(row.get("freightSpeedLimit"), 999);
            int newPassenger = reqPassenger == null ? oldPassenger : reqPassenger;
            int newFreight = reqFreight == null ? oldFreight : reqFreight;
            if (newPassenger > oldPassenger || newFreight > oldFreight) {
                throw new IllegalArgumentException("threshold update must be stricter, not looser");
            }

            row.put("controlLevel", controlLevel);
            row.put("controlLevelName", levelName(controlLevel));
            row.put("windLevelDesc", windLevelDesc);
            row.put("passengerSpeedLimit", newPassenger);
            row.put("freightSpeedLimit", newFreight);
            row.put("dangerousGoodsSpeedLimit", newFreight);
            affectedWindLevels.add(windLevel);
        }

        if (affectedWindLevels.isEmpty()) {
            throw new IllegalArgumentException("no wind level rows matched windLevelDesc: " + windLevelDesc);
        }

        Map<Integer, Map<String, Object>> controlPlanLibrary = stateService.getControlPlanLibrary();
        Map<String, Object> targetPlan = controlPlanLibrary.get(controlLevel);
        if (targetPlan != null && sourceLevel < controlLevel) {
            for (int level = sourceLevel; level <= controlLevel; level++) {
                Map<String, Object> plan = controlPlanLibrary.get(level);
                if (plan == null) {
                    continue;
                }
                plan.put("riskSectionPlan", targetPlan.get("riskSectionPlan"));
                plan.put("upstreamExitPlan", targetPlan.get("upstreamExitPlan"));
                plan.put("upstreamEntryPlan", targetPlan.get("upstreamEntryPlan"));
                plan.put("upstreamServiceAreaPlan", targetPlan.get("upstreamServiceAreaPlan"));
                plan.put("description", targetPlan.get("riskSectionPlan"));
            }
        }

        stateService.persistSnapshot();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("controlLevel", controlLevel);
        result.put("windLevelDesc", windLevelDesc);
        result.put("affectedWindLevels", affectedWindLevels.stream().sorted().toList());
        result.put("affectedControlLevels", sourceLevel < controlLevel ? buildLevelRange(sourceLevel, controlLevel) : List.of(controlLevel));
        if (reqPassenger != null) {
            result.put("passengerSpeedLimit", reqPassenger);
        }
        if (reqFreight != null) {
            result.put("freightSpeedLimit", reqFreight);
        }
        try {
            result.put("appSync", windRiskSpeedService.syncSpeedLimitAfterThresholdUpdate(null));
        } catch (Exception e) {
            Map<String, Object> syncFail = new LinkedHashMap<>();
            syncFail.put("synced", false);
            syncFail.put("message", "sync app speed limits failed: " + e.getMessage());
            result.put("appSync", syncFail);
        }
        return result;
    }

    /**
     * 风力时空影响研判（4.2.3）。
     *
     * 数据融合逻辑：
     * 1. maxWindLevel：仅基于 wind_data（实时/未来2h）；
     * 2. currentControlLevel：仅基于 wind_data.control_level；
     * 3. trafficVolumeVehPerHour：仅基于轨迹聚合服务；
     * 4. 缺失字段返回 null，不做兜底补值。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param periodType real/future2h/all（兼容 future4h 入参）
     * @param direction 可选方向：1/2
     * @return 研判结果
     */
    public Map<String, Object> evaluateSpatiotemporalImpact(long timestamp, String periodType, Integer direction) {
        String normalizedPeriodType = periodType == null ? "all" : periodType.toLowerCase(Locale.ROOT);
        if ("future4h".equals(normalizedPeriodType)) {
            normalizedPeriodType = "future2h";
        }
        if (!"real".equals(normalizedPeriodType)
                && !"future2h".equals(normalizedPeriodType)
                && !"all".equals(normalizedPeriodType)) {
            normalizedPeriodType = "all";
        }
        Integer normalizedDirection = normalizeDirection(direction);
        LocalDateTime now = toLocalDateTime(timestamp);

        List<WindData> latestRows = windDataService.listLatestSnapshot(now);
        List<WindData> future2hRows = windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_2H_MS));

        List<Map<String, Object>> records = new ArrayList<>();
        for (String stakeRange : FIXED_IMPACT_STAKE_RANGES) {
            for (Integer dir : List.of(DIRECTION_HAMI, DIRECTION_TURPAN)) {
                if (normalizedDirection != null && !normalizedDirection.equals(dir)) {
                    continue;
                }
                Map<String, Object> realRecord = buildImpactRecord(stakeRange, stakeRange, timestamp, "real", dir, latestRows, future2hRows);
                Map<String, Object> future2hRecord = buildImpactRecord(stakeRange, stakeRange, timestamp, "future2h", dir, latestRows, future2hRows);
                if ("real".equals(normalizedPeriodType)) {
                    records.add(realRecord);
                } else if ("future2h".equals(normalizedPeriodType)) {
                    records.add(future2hRecord);
                } else {
                    records.add(realRecord);
                    records.add(future2hRecord);
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("periodType", normalizedPeriodType);
        data.put("records", records);
        data.put("appSpeedPublishRecords",
                buildAppSpeedPublishRecords(timestamp, normalizedPeriodType, latestRows, future2hRows));
        return data;
    }

    /**
     * 4.2.3 实时研判（固定三段、双向，共 6 组）。
     */
    public Map<String, Object> evaluateSpatiotemporalImpactReal(long timestamp) {
        return evaluateSpatiotemporalImpact(timestamp, "real", null);
    }

    /**
     * 4.2.3 未来 2 小时研判（固定三段、双向，共 6 组）。
     */
    public Map<String, Object> evaluateSpatiotemporalImpactFuture2h(long timestamp) {
        return evaluateSpatiotemporalImpact(timestamp, "future2h", null);
    }

    /**
     * 查询大风观测/历史/预测序列（4.2.4）。
     *
     * 仅从 wind_data 拉取并聚合为时间序列；无数据时返回空 records。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param period real/history/forecast
     * @param direction 可选方向：1/2
     * @return 风数据序列
     */
    public Map<String, Object> queryWindData(long timestamp, String period, Integer direction) {
        String p = period == null ? "real" : period.toLowerCase(Locale.ROOT);
        if (!"real".equals(p) && !"history".equals(p) && !"forecast".equals(p)) {
            p = "real";
        }

        Integer normalizedDirection = normalizeDirection(direction);
        LocalDateTime now = toLocalDateTime(timestamp);
        List<WindData> rows;
        List<WindData> durationRows;
        if ("real".equals(p)) {
            rows = windDataService.listLatestSnapshot(now);
            List<WindData> futureRows = windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_72H_MS));
            durationRows = mergeWindRows(rows, futureRows);
        } else if ("history".equals(p)) {
            rows = windDataService.listByTimeRange(toLocalDateTime(timestamp - WINDOW_24H_MS), now);
            durationRows = rows;
        } else {
            // forecast 仅返回“目标时间戳”对应的一帧；前端通过 timestamp 传入当前+N小时。
            rows = windDataService.listLatestSnapshot(now);
            // 持续时间仍需看后续变化，故单独取目标时刻之后的序列。
            durationRows = windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_72H_MS));
        }

        List<Map<String, Object>> recordList = buildWindQueryRecordsFromDb(rows, durationRows, normalizedDirection, p);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("period", p);
        data.put("records", recordList);
        return data;
    }

    /**
     * 阻断时长预测（4.2.5）。
     *
     * 仅从 wind_data 最新快照统计“严重风区段数”（风级>=11）。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 预测结果
     */
    public Map<String, Object> predictBlockDuration(long timestamp) {
        LocalDateTime now = toLocalDateTime(timestamp);
        List<WindData> latestRows = windDataService.listLatestSnapshot(now);
        List<WindData> future72hRows = windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_72H_MS));

        int severeCount = 0;
        for (WindData row : latestRows) {
            Integer level = toWindLevel(row.getWindSpeed());
            if (level != null && level >= 11) {
                severeCount++;
            }
        }
        int sustainedHours = estimateMaxContinuousSevereHours(now, future72hRows);
        int predictedMinutes = Math.max(severeCount * 25, sustainedHours * 60);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("severeSegmentCount", severeCount);
        data.put("maxContinuousSevereHours", sustainedHours);
        data.put("predictedBlockDurationMin", predictedMinutes);
        return data;
    }

    /**
     * 查询 APP 限速发布数据（4.2.3 扩展）。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param periodType real/future2h/all
     * @param direction 可选方向：1/2
     * @return APP 限速发布数据
     */
    public Map<String, Object> queryAppSpeedPublish(long timestamp, String periodType, Integer direction) {
        String normalizedPeriodType = periodType == null ? "all" : periodType.toLowerCase(Locale.ROOT);
        if ("future4h".equals(normalizedPeriodType)) {
            normalizedPeriodType = "future2h";
        }
        if (!"real".equals(normalizedPeriodType)
                && !"future2h".equals(normalizedPeriodType)
                && !"all".equals(normalizedPeriodType)) {
            normalizedPeriodType = "all";
        }
        Integer normalizedDirection = normalizeDirection(direction);

        LocalDateTime now = toLocalDateTime(timestamp);
        List<WindData> latestRows = windDataService.listLatestSnapshot(now);
        List<WindData> future2hRows = windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_2H_MS));

        List<Map<String, Object>> records = buildAppSpeedPublishRecords(
                timestamp, normalizedPeriodType, latestRows, future2hRows
        );
        if (normalizedDirection != null) {
            records = records.stream()
                    .filter(row -> stateService.intValue(row.get("direction"), 0) == normalizedDirection)
                    .collect(Collectors.toList());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("periodType", normalizedPeriodType);
        data.put("records", records);
        return data;
    }

    /**
     * 构建单条区间研判记录。
     */
    private Map<String, Object> buildImpactRecord(String intervalName,
                                                  String stakeRange,
                                                  long timestamp,
                                                   String periodType,
                                                   int direction,
                                                   List<WindData> latestRows,
                                                   List<WindData> future2hRows) {
        Integer maxWind = "future2h".equals(periodType)
                ? resolveMaxWindLevelFromRows(future2hRows, stakeRange, direction)
                : resolveMaxWindLevelFromRows(latestRows, stakeRange, direction);
        Integer recommendedLevel = maxWind == null ? null : stateService.mapWindToControlLevel(maxWind);
        Integer currentLevel = resolveCurrentControlLevelFromRows(latestRows, stakeRange, direction);
        Integer trafficVolume = trajectoryService.estimateTrafficVolumeVehPerHour(timestamp, stakeRange, direction);
        Boolean needAdjust = recommendedLevel != null && currentLevel != null && !recommendedLevel.equals(currentLevel);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("controlInterval", intervalName);
        record.put("stakeRange", stakeRange);
        record.put("direction", direction);
        record.put("baseTime", timestamp);
        record.put("periodType", periodType);
        record.put("trafficVolumeVehPerHour", trafficVolume);
        record.put("maxWindLevel", maxWind);
        record.put("recommendedControlLevel", recommendedLevel);
        record.put("currentControlLevel", currentLevel);
        record.put("needAdjust", needAdjust);
        return record;
    }

    /**
     * 获取研判区间列表。
     *
     * 优先读取 stateService 的 dispatchPlanLibrary（其初始化已优先来自 control_interval_static），
     * 若无可用区间，视为静态表缺失并抛出异常。
     */
    private List<Map<String, Object>> listControlIntervals() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> plan : stateService.getDispatchPlanLibrary().values()) {
            String startStake = stateService.stringValue(plan.get("startStake"));
            String endStake = stateService.stringValue(plan.get("endStake"));
            if (startStake.isBlank() || endStake.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", stateService.stringValue(plan.get("segment")));
            row.put("stakeRange", startStake + "-" + endStake);
            if (plan.containsKey("direction")) {
                row.put("direction", stateService.intValue(plan.get("direction"), 0));
            }
            rows.add(row);
        }

        if (!rows.isEmpty()) {
            return rows;
        }
        throw new IllegalStateException("dispatchPlanLibrary 为空，请先初始化 control_interval_static 并完成状态快照加载。");
    }

    /**
     * 生成缺省阈值行。
     */
    private Map<String, Object> newThresholdByWindLevel(int windLevel) {
        int controlLevel = stateService.mapWindToControlLevel(windLevel);
        Map<String, Object> plan = stateService.getControlPlanLibrary().get(controlLevel);
        int passenger = plan == null ? Math.max(30, 80 - (windLevel - 7) * 10) : stateService.intValue(plan.get("passengerSpeedLimit"), 80);
        int freight = plan == null ? Math.max(20, 70 - (windLevel - 7) * 10) : stateService.intValue(plan.get("freightSpeedLimit"), 60);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("windLevel", windLevel);
        row.put("controlLevel", controlLevel);
        row.put("controlLevelName", levelName(controlLevel));
        row.put("windLevelDesc", normalizeWindLevelDesc(controlLevel));
        row.put("passengerSpeedLimit", Math.max(0, passenger));
        row.put("freightSpeedLimit", Math.max(0, freight));
        row.put("dangerousGoodsSpeedLimit", Math.max(0, freight));
        return row;
    }

    private int resolveControlLevelByWindLevelDesc(String windLevelDesc) {
        String token = windLevelDesc.trim().toLowerCase(Locale.ROOT);
        Map<Integer, Integer> counterByLevel = new HashMap<>();
        for (Map<String, Object> row : stateService.getSpeedThresholdByWindLevel().values()) {
            if (row == null) {
                continue;
            }
            String desc = stateService.stringValue(row.get("windLevelDesc")).trim().toLowerCase(Locale.ROOT);
            if (!token.equals(desc)) {
                continue;
            }
            int level = stateService.intValue(row.get("controlLevel"), -1);
            if (level > 0) {
                counterByLevel.put(level, counterByLevel.getOrDefault(level, 0) + 1);
            }
        }
        int matchedLevel = -1;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : counterByLevel.entrySet()) {
            int level = entry.getKey();
            int count = entry.getValue();
            if (count > bestCount) {
                bestCount = count;
                matchedLevel = level;
            }
        }
        return matchedLevel;
    }

    private Integer toNullableInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        return Integer.parseInt(s);
    }

    private List<Integer> buildLevelRange(int start, int end) {
        List<Integer> levels = new ArrayList<>();
        for (int level = start; level <= end; level++) {
            levels.add(level);
        }
        return levels;
    }

    private String normalizeWindLevelDesc(int controlLevel) {
        return switch (controlLevel) {
            case 1 -> "12级";
            case 2 -> "11级";
            case 3 -> "9-10级";
            case 4 -> "7-8级";
            default -> "7级以下";
        };
    }

    private String levelName(int level) {
        return switch (level) {
            case 1 -> "红色警戒";
            case 2 -> "橙色警戒";
            case 3 -> "黄色警戒";
            case 4 -> "蓝色警戒";
            case 5 -> "绿色警戒";
            default -> "未知";
        };
    }

    /**
     * 方向参数标准化。
     */
    private Integer normalizeDirection(Integer direction) {
        if (direction == null) {
            return null;
        }
        if (direction != DIRECTION_HAMI && direction != DIRECTION_TURPAN) {
            throw new IllegalArgumentException("direction must be 1(hami) or 2(turpan)");
        }
        return direction;
    }

    /**
     * 将 wind_data.direction 标准化为 1/2。
     */
    private int normalizeDirection(String directionText) {
        if (directionText == null || directionText.isBlank()) {
            return 0;
        }
        String s = directionText.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(s) || "下行".equals(s) || "哈密".equals(s) || "hami".equals(s) || "towh".equals(s) || "to_wh".equals(s)) {
            return DIRECTION_HAMI;
        }
        if ("2".equals(s) || "上行".equals(s) || "吐鲁番".equals(s) || "turpan".equals(s) || "toez".equals(s) || "to_ez".equals(s)) {
            return DIRECTION_TURPAN;
        }
        return 0;
    }

    /**
     * 判断路段与目标桩号区间是否重叠。
     */
    private boolean inStakeRange(String segmentName, String stakeRange) {
        double[] segment = parseRange(segmentName);
        double[] target = parseRange(stakeRange);
        if (segment == null || target == null) {
            return false;
        }
        return !(segment[1] < target[0] || segment[0] > target[1]);
    }

    /**
     * 从文本中提取桩号范围。
     */
    private double[] parseRange(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = STAKE_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
        List<Double> values = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token.contains("+")) {
                String[] parts = token.split("\\+");
                values.add(Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]) / 1000.0);
            } else {
                values.add(Double.parseDouble(token));
            }
        }
        if (values.size() < 2) {
            return null;
        }
        double start = Math.min(values.get(0), values.get(1));
        double end = Math.max(values.get(0), values.get(1));
        return new double[]{start, end};
    }

    /**
     * 时间戳转本地时间。
     */
    private LocalDateTime toLocalDateTime(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 将风速（m/s）映射为风级（1-12）。
     *
     * 采用近似蒲福风级阈值。
     */
    private Integer toWindLevel(BigDecimal windSpeed) {
        if (windSpeed == null) {
            return null;
        }
        double v = windSpeed.doubleValue();
        if (v >= 32.7) return 12;
        if (v >= 28.5) return 11;
        if (v >= 24.5) return 10;
        if (v >= 20.8) return 9;
        if (v >= 17.2) return 8;
        if (v >= 13.9) return 7;
        if (v >= 10.8) return 6;
        if (v >= 8.0) return 5;
        if (v >= 5.5) return 4;
        if (v >= 3.4) return 3;
        if (v >= 1.6) return 2;
        if (v >= 0.3) return 1;
        return 1;
    }

    /**
     * 根据风级映射前端色块颜色。
     */
    private String colorByWindLevel(int windLevel) {
        if (windLevel >= 12) {
            return "red";
        }
        if (windLevel >= 11) {
            return "orange";
        }
        if (windLevel >= 9) {
            return "yellow";
        }
        if (windLevel >= 7) {
            return "blue";
        }
        return "green";
    }

    /**
     * 从 wind_data 行集中解析某区间某方向的最大风级。
     */
    private Integer resolveMaxWindLevelFromRows(List<WindData> rows, String targetStakeRange, int direction) {
        Integer max = null;
        for (WindData row : rows) {
            if (normalizeDirection(row.getDirection()) != direction) {
                continue;
            }
            if (!inStakeRange(rowStakeRange(row), targetStakeRange)) {
                continue;
            }
            Integer level = toWindLevel(row.getWindSpeed());
            if (level == null) {
                continue;
            }
            max = max == null ? level : Math.max(max, level);
        }
        return max;
    }

    /**
     * 从未来窗口中解析最接近 targetTime 的预测风级（用于 forecast 模式）。
     */
    private Integer resolveForecastWindLevelFromRows(List<WindData> rows,
                                                     String targetStakeRange,
                                                     int direction,
                                                     LocalDateTime targetTime) {
        WindData best = null;
        long bestDistance = Long.MAX_VALUE;
        for (WindData row : rows) {
            if (normalizeDirection(row.getDirection()) != direction) {
                continue;
            }
            if (!inStakeRange(rowStakeRange(row), targetStakeRange)) {
                continue;
            }
            if (row.getTimeStamp() == null) {
                continue;
            }
            long distance = Math.abs(Duration.between(targetTime, row.getTimeStamp()).toMillis());
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && row.getTimeStamp().isBefore(best.getTimeStamp()))) {
                best = row;
                bestDistance = distance;
            }
        }
        return best == null ? null : toWindLevel(best.getWindSpeed());
    }

    /**
     * 从 wind_data 行集中解析某区间某方向的当前控制等级（取最严格等级）。
     */
    private Integer resolveCurrentControlLevelFromRows(List<WindData> rows, String targetStakeRange, int direction) {
        Integer level = null;
        for (WindData row : rows) {
            if (normalizeDirection(row.getDirection()) != direction) {
                continue;
            }
            if (!inStakeRange(rowStakeRange(row), targetStakeRange)) {
                continue;
            }
            if (row.getControlLevel() == null || row.getControlLevel() <= 0) {
                continue;
            }
            level = level == null ? row.getControlLevel() : Math.min(level, row.getControlLevel());
        }
        return level;
    }

    /**
     * 将 wind_data 的起止桩号拼成 stakeRange。
     */
    private String rowStakeRange(WindData row) {
        return stateService.stringValue(row.getStartStake()) + "-" + stateService.stringValue(row.getEndStake());
    }

    /**
     * 将 wind_data 结果聚合为 queryWindData 接口格式。
     */
    private List<Map<String, Object>> buildWindQueryRecordsFromDb(List<WindData> rows,
                                                                   List<WindData> durationRows,
                                                                   Integer direction,
                                                                   String period) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<WindAgg> aggList = buildWindAggList(rows, direction);
        aggList.sort(Comparator
                .comparing((WindAgg a) -> a.time)
                .thenComparingInt(a -> a.direction)
                .thenComparing(a -> a.stakeRange));

        List<Map<String, Object>> records = new ArrayList<>();
        int defaultDurationMin = "real".equals(period) ? 5 : 60;
        List<WindAgg> durationAggList = buildWindAggList(durationRows, direction);
        Map<String, Integer> durationByAggKey = computeDynamicDurationMinutes(
                aggList,
                durationAggList,
                defaultDurationMin
        );
        for (WindAgg agg : aggList) {
            String aggKey = buildAggKey(agg);
            int durationMin = durationByAggKey.getOrDefault(aggKey, defaultDurationMin);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", agg.time.format(DATETIME_FMT));
            row.put("direction", agg.direction);
            row.put("stakeRange", agg.stakeRange);
            row.put("windLevel", agg.maxWindLevel);
            row.put("windLevelDesc", toWindLevelDisplayText(agg.maxWindLevel));
            row.put("windDirection", toChineseWindDirection(agg.windDirection));
            row.put("durationMin", durationMin);
            records.add(row);
        }
        return records;
    }

    /**
     * 持续时长计算：
     * 对每个“方向+路段范围”序列，取当前风级到下一次风级变化的时间差（分钟）。
     */
    private Map<String, Integer> computeDynamicDurationMinutes(List<WindAgg> currentAggList,
                                                               List<WindAgg> durationAggList,
                                                               int fallbackDurationMin) {
        if (currentAggList == null || currentAggList.isEmpty()) {
            return Map.of();
        }
        Map<String, List<WindAgg>> group = new LinkedHashMap<>();
        List<WindAgg> effectiveDurationList = (durationAggList == null || durationAggList.isEmpty())
                ? currentAggList
                : durationAggList;
        for (WindAgg agg : effectiveDurationList) {
            String key = agg.direction + "|" + agg.stakeRange;
            group.computeIfAbsent(key, k -> new ArrayList<>()).add(agg);
        }

        Map<String, Integer> durationByAggKey = new HashMap<>();
        for (List<WindAgg> series : group.values()) {
            series.sort(Comparator.comparing(a -> a.time));
            int stepMin = detectStepMinutes(series, fallbackDurationMin);
            for (WindAgg current : currentAggList) {
                String seriesKey = current.direction + "|" + current.stakeRange;
                if (!seriesKey.equals(series.get(0).direction + "|" + series.get(0).stakeRange)) {
                    continue;
                }
                int startIdx = findStartIndex(series, current.time);
                if (startIdx < 0) {
                    durationByAggKey.put(buildAggKey(current), fallbackDurationMin);
                    continue;
                }
                int nextChangeIndex = -1;
                for (int j = startIdx + 1; j < series.size(); j++) {
                    WindAgg next = series.get(j);
                    if (!equalsInt(current.maxWindLevel, next.maxWindLevel)) {
                        nextChangeIndex = j;
                        break;
                    }
                }

                int durationMin;
                if (nextChangeIndex > 0) {
                    durationMin = (int) Duration.between(current.time, series.get(nextChangeIndex).time).toMinutes();
                } else {
                    WindAgg last = series.get(series.size() - 1);
                    durationMin = (int) Duration.between(current.time, last.time).toMinutes() + stepMin;
                }
                if (durationMin <= 0) {
                    durationMin = fallbackDurationMin;
                }
                durationByAggKey.put(buildAggKey(current), durationMin);
            }
        }
        return durationByAggKey;
    }

    private int findStartIndex(List<WindAgg> series, LocalDateTime time) {
        if (series == null || series.isEmpty() || time == null) {
            return -1;
        }
        for (int i = 0; i < series.size(); i++) {
            if (series.get(i).time.equals(time)) {
                return i;
            }
            if (series.get(i).time.isAfter(time)) {
                return i;
            }
        }
        return series.size() - 1;
    }

    private List<WindAgg> buildWindAggList(List<WindData> rows, Integer direction) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, WindAgg> aggMap = new LinkedHashMap<>();
        for (WindData row : rows) {
            int rowDirection = normalizeDirection(row.getDirection());
            if (rowDirection == 0) {
                continue;
            }
            if (direction != null && rowDirection != direction) {
                continue;
            }
            if (row.getTimeStamp() == null) {
                continue;
            }
            String startStake = stateService.stringValue(row.getStartStake());
            String endStake = stateService.stringValue(row.getEndStake());
            if (!isWindObservationRange(startStake, endStake)) {
                continue;
            }
            Integer level = toWindLevel(row.getWindSpeed());
            if (level == null) {
                continue;
            }
            String stakeRange = toStakeRangeText(startStake, endStake);
            if (stakeRange.isBlank()) {
                continue;
            }
            String key = row.getTimeStamp().format(DATETIME_FMT) + "#" + rowDirection + "#" + stakeRange;
            WindAgg agg = aggMap.computeIfAbsent(key, k -> new WindAgg(row.getTimeStamp(), rowDirection, stakeRange, startStake, endStake));
            if (agg.maxWindLevel == null || level > agg.maxWindLevel) {
                agg.maxWindLevel = level;
                agg.windDirection = stateService.stringValue(row.getWindDirection());
            }
        }
        return new ArrayList<>(aggMap.values());
    }

    private List<WindData> mergeWindRows(List<WindData> a, List<WindData> b) {
        List<WindData> merged = new ArrayList<>();
        if (a != null && !a.isEmpty()) {
            merged.addAll(a);
        }
        if (b != null && !b.isEmpty()) {
            merged.addAll(b);
        }
        return merged;
    }

    private int detectStepMinutes(List<WindAgg> series, int fallbackDurationMin) {
        if (series == null || series.size() < 2) {
            return fallbackDurationMin;
        }
        int minPositive = Integer.MAX_VALUE;
        for (int i = 1; i < series.size(); i++) {
            int diff = (int) Duration.between(series.get(i - 1).time, series.get(i).time).toMinutes();
            if (diff > 0 && diff < minPositive) {
                minPositive = diff;
            }
        }
        return minPositive == Integer.MAX_VALUE ? fallbackDurationMin : minPositive;
    }

    private String buildAggKey(WindAgg agg) {
        return agg.time.format(DATETIME_FMT) + "#" + agg.direction + "#" + agg.stakeRange;
    }

    private boolean equalsInt(Integer a, Integer b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.intValue() == b.intValue();
    }

    /**
     * 生成 APP 可直接消费的限速发布数据（按路段、方向输出）。
     */
    private List<Map<String, Object>> buildAppSpeedPublishRecords(long timestamp,
                                                                  String periodType,
                                                                  List<WindData> latestRows,
                                                                  List<WindData> future2hRows) {
        List<WindData> sourceRows = switch (periodType) {
            case "future2h" -> future2hRows;
            case "all" -> {
                List<WindData> merged = new ArrayList<>();
                if (latestRows != null) {
                    merged.addAll(latestRows);
                }
                if (future2hRows != null) {
                    merged.addAll(future2hRows);
                }
                yield merged;
            }
            default -> latestRows;
        };
        if (sourceRows == null || sourceRows.isEmpty()) {
            return List.of();
        }

        Map<String, Map<String, Object>> segmentMetaByKey = new HashMap<>();
        for (Map<String, Object> section : stateService.getFullLineWindSections()) {
            int direction = stateService.intValue(section.get("direction"), 0);
            String startStake = stateService.stringValue(section.get("startStake"));
            String endStake = stateService.stringValue(section.get("endStake"));
            if (direction <= 0 || startStake.isBlank() || endStake.isBlank()) {
                continue;
            }
            segmentMetaByKey.put(segmentStakeKey(direction, startStake, endStake), section);
        }

        Map<String, Integer> maxWindBySegment = new HashMap<>();
        for (WindData row : sourceRows) {
            int direction = normalizeDirection(row.getDirection());
            if (direction == 0) {
                continue;
            }
            String startStake = stateService.stringValue(row.getStartStake());
            String endStake = stateService.stringValue(row.getEndStake());
            if (startStake.isBlank() || endStake.isBlank()) {
                continue;
            }
            Integer level = toWindLevel(row.getWindSpeed());
            if (level == null) {
                continue;
            }
            String key = direction + "|" + startStake + "|" + endStake;
            Integer current = maxWindBySegment.get(key);
            if (current == null || level > current) {
                maxWindBySegment.put(key, level);
            }
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (Map.Entry<String, Integer> e : maxWindBySegment.entrySet()) {
            String[] parts = e.getKey().split("\\|", -1);
            int direction = Integer.parseInt(parts[0]);
            String startStake = parts[1];
            String endStake = parts[2];
            int windLevel = e.getValue();

            Map<String, Object> threshold = stateService.getSpeedThresholdByWindLevel().get(windLevel);
            Integer passenger = null;
            Integer freight = null;
            if (threshold != null) {
                int p = stateService.intValue(threshold.get("passengerSpeedLimit"), -1);
                int f = stateService.intValue(threshold.get("freightSpeedLimit"), -1);
                passenger = p >= 0 ? p : null;
                freight = f >= 0 ? f : null;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("baseTime", timestamp);
            row.put("periodType", periodType);
            row.put("direction", direction);
            row.put("startStake", startStake);
            row.put("endStake", endStake);
            Map<String, Object> segmentMeta = segmentMetaByKey.get(segmentStakeKey(direction, startStake, endStake));
            if (segmentMeta == null) {
                segmentMeta = segmentMetaByKey.get(segmentStakeKey(direction, endStake, startStake));
            }
            row.put("appSpeedInterval", segmentMeta == null ? null : stateService.stringValue(segmentMeta.get("appSpeedInterval")));
            row.put("controlInterval", segmentMeta == null ? null : stateService.stringValue(segmentMeta.get("controlInterval")));
            row.put("windLevel", windLevel);
            row.put("recommendedControlLevel", stateService.mapWindToControlLevel(windLevel));
            row.put("passengerSpeedLimit", passenger);
            row.put("freightSpeedLimit", freight);
            records.add(row);
        }

        records.sort((a, b) -> {
            int d = Integer.compare(stateService.intValue(a.get("direction"), 0), stateService.intValue(b.get("direction"), 0));
            if (d != 0) {
                return d;
            }
            String aStart = stateService.stringValue(a.get("startStake"));
            String bStart = stateService.stringValue(b.get("startStake"));
            return aStart.compareTo(bStart);
        });
        return records;
    }

    private String segmentStakeKey(int direction, String startStake, String endStake) {
        return direction + "|" + startStake.trim().toUpperCase(Locale.ROOT) + "|" + endStake.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStakeText(String stake) {
        if (stake == null || stake.isBlank()) {
            return "";
        }
        String value = stake.trim().toUpperCase(Locale.ROOT);
        if (!value.startsWith("K")) {
            value = "K" + value;
        }
        return value;
    }

    private String toStakeRangeText(String startStake, String endStake) {
        String start = normalizeStakeText(startStake);
        String end = normalizeStakeText(endStake);
        if (start.isBlank() || end.isBlank()) {
            return "";
        }
        return start + "-" + end;
    }

    private String toDisplayStakeId(String primaryStake, String fallbackStake) {
        String stake = normalizeStakeText(primaryStake);
        if (stake.isBlank()) {
            stake = normalizeStakeText(fallbackStake);
        }
        if (stake.isBlank()) {
            return "";
        }
        if (stake.startsWith("K")) {
            return "k" + stake.substring(1);
        }
        return "k" + stake;
    }

    private boolean isWindObservationRange(String startStake, String endStake) {
        Double start = parseStakeValue(startStake);
        Double end = parseStakeValue(endStake);
        if (start == null || end == null) {
            return false;
        }
        double min = Math.min(start, end);
        double max = Math.max(start, end);
        return min >= WIND_OBSERVATION_RANGE_MIN && max <= WIND_OBSERVATION_RANGE_MAX;
    }

    private Double parseStakeValue(String stake) {
        if (stake == null || stake.isBlank()) {
            return null;
        }
        String value = stake.trim().toUpperCase(Locale.ROOT);
        if (value.startsWith("K")) {
            value = value.substring(1);
        }
        try {
            if (value.contains("+")) {
                String[] parts = value.split("\\+", -1);
                if (parts.length != 2) {
                    return null;
                }
                double base = Double.parseDouble(parts[0]);
                double offset = Double.parseDouble(parts[1]);
                return base + offset / 1000D;
            }
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 将风向文本标准化为中文风向。
     */
    private String toChineseWindDirection(String raw) {
        if (raw == null || raw.isBlank()) {
            return "未知";
        }
        String value = raw.trim();
        // 兼容脏数据：多值拼接/换行/分隔符，优先取第一段有效方向码。
        String[] tokens = value.split("[,;/|，；\\s]+");
        String first = tokens.length == 0 ? value : tokens[0];
        String upper = first.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        if ("NORTH".equals(upper)) upper = "N";
        if ("SOUTH".equals(upper)) upper = "S";
        if ("EAST".equals(upper)) upper = "E";
        if ("WEST".equals(upper)) upper = "W";
        return switch (upper) {
            case "N" -> "北风";
            case "NNE" -> "北东北风";
            case "NE" -> "东北风";
            case "ENE" -> "东东北风";
            case "E" -> "东风";
            case "ESE" -> "东东南风";
            case "SE" -> "东南风";
            case "SSE" -> "南东南风";
            case "S" -> "南风";
            case "SSW" -> "南西南风";
            case "SW" -> "西南风";
            case "WSW" -> "西西南风";
            case "W" -> "西风";
            case "WNW" -> "西西北风";
            case "NW" -> "西北风";
            case "NNW" -> "北西北风";
            default -> {
                if (value.contains("风")) {
                    yield value;
                }
                if (value.contains("北") || value.contains("南")
                        || value.contains("东") || value.contains("西")) {
                    yield value + "风";
                }
                yield value;
            }
        };
    }

    /**
     * 风级展示文案（按业务分档，不展示 1~12 细粒度）。
     */
    private String toWindLevelDisplayText(Integer windLevel) {
        if (windLevel == null) {
            return "未知";
        }
        if (windLevel >= 12) {
            return "12级风";
        }
        if (windLevel >= 11) {
            return "11级";
        }
        if (windLevel >= 9) {
            return "9-10级";
        }
        if (windLevel >= 8) {
            return "7-8级";
        }
        return "7级及以下";
    }

    /**
     * 估算未来 72h 内最大连续严重风（11级及以上）时长。
     */
    private int estimateMaxContinuousSevereHours(LocalDateTime now, List<WindData> future72hRows) {
        if (future72hRows == null || future72hRows.isEmpty()) {
            return 0;
        }

        Map<String, List<WindData>> rowsBySegment = new HashMap<>();
        for (WindData row : future72hRows) {
            int direction = normalizeDirection(row.getDirection());
            if (direction == 0 || row.getTimeStamp() == null) {
                continue;
            }
            String startStake = stateService.stringValue(row.getStartStake());
            String endStake = stateService.stringValue(row.getEndStake());
            if (startStake.isBlank() || endStake.isBlank()) {
                continue;
            }
            String key = direction + "|" + startStake + "|" + endStake;
            rowsBySegment.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        int maxHours = 0;
        for (List<WindData> rows : rowsBySegment.values()) {
            rows.sort(Comparator.comparing(WindData::getTimeStamp));
            Set<LocalDateTime> severeHours = new HashSet<>();
            for (WindData row : rows) {
                Integer level = toWindLevel(row.getWindSpeed());
                if (level != null && level >= 11 && !row.getTimeStamp().isBefore(now)) {
                    LocalDateTime hour = row.getTimeStamp().withMinute(0).withSecond(0).withNano(0);
                    severeHours.add(hour);
                }
            }
            if (severeHours.isEmpty()) {
                continue;
            }
            List<LocalDateTime> sortedHours = new ArrayList<>(severeHours);
            sortedHours.sort(LocalDateTime::compareTo);
            int current = 1;
            int best = 1;
            for (int i = 1; i < sortedHours.size(); i++) {
                LocalDateTime prev = sortedHours.get(i - 1);
                LocalDateTime cur = sortedHours.get(i);
                if (prev.plusHours(1).equals(cur)) {
                    current++;
                    if (current > best) {
                        best = current;
                    }
                } else {
                    current = 1;
                }
            }
            if (best > maxHours) {
                maxHours = best;
            }
        }
        return maxHours;
    }

    /**
     * 风序列聚合中间对象。
     */
    private static class WindAgg {
        private final LocalDateTime time;
        private final int direction;
        private final String stakeRange;
        private final String startStake;
        private final String endStake;
        private Integer maxWindLevel;
        private String windDirection;

        private WindAgg(LocalDateTime time, int direction, String stakeRange, String startStake, String endStake) {
            this.time = time;
            this.direction = direction;
            this.stakeRange = stakeRange;
            this.startStake = startStake;
            this.endStake = endStake;
        }
    }
}
