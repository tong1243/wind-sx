package com.wut.screenwebsx.Service;

import com.wut.screendbmysqlsx.Model.WindData;
import com.wut.screendbmysqlsx.Service.WindDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 4.2 大风时空影响业务服务。
 *
 * 关键说明：
 * 1. 真实大风数据优先读取 wind_data 表；
 * 2. 当 wind_data 在目标时间窗无记录时，自动回退到共享状态中的原有演示/静态逻辑；
 * 3. 方向规范始终使用 1（吐鲁番）与 2（哈密）；
 * 4. 交通量字段继续优先使用轨迹服务计算（4.1 已接 traj_near_real_*）。
 */
@Service
public class WindControlWindImpactService {
    /** 下行方向（哈密）。 */
    private static final int DIRECTION_HAMI = 2;
    /** 上行方向（吐鲁番）。 */
    private static final int DIRECTION_TURPAN = 1;
    /** 4 小时时窗。 */
    private static final long WINDOW_4H_MS = 4L * 60 * 60 * 1000;
    /** 72 小时时窗。 */
    private static final long WINDOW_72H_MS = 72L * 60 * 60 * 1000;
    /** 默认时间格式。 */
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 桩号提取规则，支持 K3191 与 K3191+800。 */
    private static final Pattern STAKE_PATTERN = Pattern.compile("K(\\d+(?:\\+\\d+)?)", Pattern.CASE_INSENSITIVE);

    /** 公共状态服务。 */
    private final WindControlStateService stateService;
    /** 轨迹聚合服务（用于交通量估算）。 */
    private final WindControlTrajectoryService trajectoryService;
    /** wind_data 数据服务。 */
    private final WindDataService windDataService;

    /**
     * 构造函数。
     *
     * @param stateService 公共状态服务
     * @param trajectoryService 轨迹聚合服务
     * @param windDataService wind_data 服务
     */
    public WindControlWindImpactService(WindControlStateService stateService,
                                        WindControlTrajectoryService trajectoryService,
                                        WindDataService windDataService) {
        this.stateService = stateService;
        this.trajectoryService = trajectoryService;
        this.windDataService = windDataService;
    }

    /**
     * 查询全线风力可视化数据（4.2.1）。
     *
     * 规则：
     * 1. mode=real：读取 wind_data 的“最新快照”；
     * 2. mode=forecast：读取未来 4h 窗口并取每段最大风级；
     * 3. mode=max4h/max72h：分别读取未来 4h/72h 窗口最大风级；
     * 4. 无真实风数据时，回退到 stateService 的既有字段。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param mode 模式：real/forecast/max4h/max72h（兼容 4h）
     * @return 可视化结果
     */
    public Map<String, Object> getWindVisualization(long timestamp, String mode) {
        String finalMode = mode == null ? "real" : mode.toLowerCase(Locale.ROOT);
        LocalDateTime now = toLocalDateTime(timestamp);

        List<WindData> latestRows = windDataService.listLatestSnapshot(now);
        List<WindData> future4hRows = ("forecast".equals(finalMode) || "max4h".equals(finalMode) || "4h".equals(finalMode))
                ? windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_4H_MS))
                : List.of();
        List<WindData> future72hRows = "max72h".equals(finalMode)
                ? windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_72H_MS))
                : List.of();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> seg : stateService.getFullLineWindSections()) {
            String segmentName = stateService.stringValue(seg.get("segmentName"));
            int direction = stateService.intValue(seg.get("direction"), DIRECTION_HAMI);

            Integer windLevelFromDb;
            if ("forecast".equals(finalMode) || "max4h".equals(finalMode) || "4h".equals(finalMode)) {
                windLevelFromDb = resolveMaxWindLevelFromRows(future4hRows, segmentName, direction);
            } else if ("max72h".equals(finalMode)) {
                windLevelFromDb = resolveMaxWindLevelFromRows(future72hRows, segmentName, direction);
            } else {
                windLevelFromDb = resolveMaxWindLevelFromRows(latestRows, segmentName, direction);
            }

            int fallbackWindLevel;
            if ("forecast".equals(finalMode)) {
                fallbackWindLevel = stateService.intValue(seg.get("forecastWindLevel"), 0);
            } else if ("max4h".equals(finalMode) || "4h".equals(finalMode) || "max72h".equals(finalMode)) {
                fallbackWindLevel = stateService.intValue(seg.get("max72hWindLevel"), 0);
            } else {
                fallbackWindLevel = stateService.intValue(seg.get("realWindLevel"), 0);
            }
            int windLevel = windLevelFromDb == null ? fallbackWindLevel : windLevelFromDb;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("segmentId", seg.get("segmentId"));
            row.put("segmentName", segmentName);
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
        for (Integer level : new TreeSet<>(stateService.getSpeedThresholdByWindLevel().keySet())) {
            rows.add(new LinkedHashMap<>(stateService.getSpeedThresholdByWindLevel().get(level)));
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
        stateService.mergeIfPresent(existing, body, "passengerSpeedLimit");
        stateService.mergeIfPresent(existing, body, "freightSpeedLimit");
        stateService.mergeIfPresent(existing, body, "dangerousGoodsSpeedLimit");
        stateService.persistSnapshot();
        return new LinkedHashMap<>(existing);
    }

    /**
     * 风力时空影响研判（4.2.3）。
     *
     * 数据融合逻辑：
     * 1. maxWindLevel：优先 wind_data（实时/未来4h），没有再回退 stateService；
     * 2. currentControlLevel：优先 wind_data.control_level，没有再回退状态快照；
     * 3. trafficVolumeVehPerHour：优先轨迹聚合服务；
     * 4. 其余字段与原接口保持一致。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param periodType real/future4h/all
     * @param direction 可选方向：1/2
     * @return 研判结果
     */
    public Map<String, Object> evaluateSpatiotemporalImpact(long timestamp, String periodType, Integer direction) {
        String normalizedPeriodType = periodType == null ? "all" : periodType.toLowerCase(Locale.ROOT);
        Integer normalizedDirection = normalizeDirection(direction);
        LocalDateTime now = toLocalDateTime(timestamp);

        List<WindData> latestRows = windDataService.listLatestSnapshot(now);
        List<WindData> future4hRows = windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_4H_MS));

        List<Map<String, Object>> records = new ArrayList<>();
        List<Map<String, Object>> intervals = listControlIntervals();
        for (Map<String, Object> interval : intervals) {
            String intervalName = stateService.stringValue(interval.get("name"));
            String stakeRange = stateService.stringValue(interval.get("stakeRange"));
            Integer intervalDirection = interval.containsKey("direction")
                    ? stateService.intValue(interval.get("direction"), 0)
                    : null;

            List<Map<String, Object>> intervalSegments = stateService.getFullLineWindSections().stream()
                    .filter(row -> inStakeRange(stateService.stringValue(row.get("segmentName")), stakeRange))
                    .collect(Collectors.toList());
            if (intervalSegments.isEmpty()) {
                continue;
            }

            List<Integer> directions = intervalDirection == null || intervalDirection == 0
                    ? List.of(DIRECTION_HAMI, DIRECTION_TURPAN)
                    : List.of(intervalDirection);

            for (Integer dir : directions) {
                if (normalizedDirection != null && !normalizedDirection.equals(dir)) {
                    continue;
                }
                Map<String, Object> realRecord = buildImpactRecord(intervalName, stakeRange, timestamp, "real", dir, intervalSegments, latestRows, future4hRows);
                Map<String, Object> future4hRecord = buildImpactRecord(intervalName, stakeRange, timestamp, "future4h", dir, intervalSegments, latestRows, future4hRows);
                if ("real".equals(normalizedPeriodType)) {
                    records.add(realRecord);
                } else if ("future4h".equals(normalizedPeriodType)) {
                    records.add(future4hRecord);
                } else {
                    records.add(realRecord);
                    records.add(future4hRecord);
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("periodType", normalizedPeriodType);
        data.put("records", records);
        return data;
    }

    /**
     * 查询大风观测/历史/预测序列（4.2.4）。
     *
     * 优先级：
     * 1. 优先从 wind_data 拉取并聚合为时间序列；
     * 2. 当 wind_data 在时窗无数据时，回退到既有模拟序列。
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
        if ("real".equals(p)) {
            rows = windDataService.listLatestSnapshot(now);
        } else if ("history".equals(p)) {
            rows = windDataService.listByTimeRange(toLocalDateTime(timestamp - WINDOW_72H_MS), now);
        } else {
            rows = windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_72H_MS));
        }

        List<Map<String, Object>> recordList = buildWindQueryRecordsFromDb(rows, normalizedDirection, p);
        if (recordList.isEmpty()) {
            return buildFallbackWindData(timestamp, p, normalizedDirection);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("period", p);
        data.put("records", recordList);
        return data;
    }

    /**
     * 阻断时长预测（4.2.5）。
     *
     * 优先逻辑：
     * 1. 从 wind_data 最新快照统计“严重风区段数”（风级>=11）；
     * 2. 若无 wind_data 再回退 stateService。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 预测结果
     */
    public Map<String, Object> predictBlockDuration(long timestamp) {
        LocalDateTime now = toLocalDateTime(timestamp);
        List<WindData> latestRows = windDataService.listLatestSnapshot(now);

        int severeCount = 0;
        if (!latestRows.isEmpty()) {
            for (WindData row : latestRows) {
                Integer level = toWindLevel(row.getWindSpeed());
                if (level != null && level >= 11) {
                    severeCount++;
                }
            }
        } else {
            for (Map<String, Object> row : stateService.getFullLineWindSections()) {
                int maxWind = Math.max(
                        stateService.intValue(row.get("realWindLevel"), 0),
                        stateService.intValue(row.get("forecastWindLevel"), 0)
                );
                if (maxWind >= 11) {
                    severeCount++;
                }
            }
        }

        int predictedMinutes = severeCount * 25;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("severeSegmentCount", severeCount);
        data.put("predictedBlockDurationMin", predictedMinutes);
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
                                                  List<Map<String, Object>> intervalSegments,
                                                  List<WindData> latestRows,
                                                  List<WindData> future4hRows) {
        Integer maxWindFromDb = "future4h".equals(periodType)
                ? resolveMaxWindLevelFromRows(future4hRows, stakeRange, direction)
                : resolveMaxWindLevelFromRows(latestRows, stakeRange, direction);
        int maxWind = maxWindFromDb == null
                ? resolveFallbackMaxWind(intervalSegments, periodType, direction)
                : maxWindFromDb;

        int recommendedLevel = stateService.mapWindToControlLevel(maxWind);

        Integer currentLevelFromDb = resolveCurrentControlLevelFromRows(latestRows, stakeRange, direction);
        int currentLevel = currentLevelFromDb == null
                ? calcCurrentLevelByRange(stakeRange)
                : currentLevelFromDb;

        Integer trajectoryTrafficVolume = trajectoryService.estimateTrafficVolumeVehPerHour(timestamp, stakeRange, direction);
        int trafficVolume = trajectoryTrafficVolume == null
                ? 1100 + Math.abs((intervalName + direction).hashCode() % 350)
                : trajectoryTrafficVolume;

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
        record.put("needAdjust", recommendedLevel != currentLevel);
        return record;
    }

    /**
     * 回退模式下从共享状态计算区间最大风级。
     */
    private int resolveFallbackMaxWind(List<Map<String, Object>> intervalSegments, String periodType, int direction) {
        int maxWind = 0;
        for (Map<String, Object> row : intervalSegments) {
            int rowDirection = stateService.intValue(row.get("direction"), DIRECTION_HAMI);
            if (rowDirection != direction) {
                continue;
            }
            int level = "future4h".equals(periodType)
                    ? Math.max(stateService.intValue(row.get("forecastWindLevel"), 0), stateService.intValue(row.get("max72hWindLevel"), 0))
                    : stateService.intValue(row.get("realWindLevel"), 0);
            maxWind = Math.max(maxWind, level);
        }
        return maxWind;
    }

    /**
     * 根据桩号区间计算当前控制等级（回退逻辑）。
     */
    private int calcCurrentLevelByRange(String stakeRange) {
        int level = stateService.getDefaultControlLevel();
        for (Map.Entry<String, Integer> entry : stateService.getCurrentControlLevelBySegment().entrySet()) {
            if (inStakeRange(entry.getKey(), stakeRange)) {
                level = Math.min(level, entry.getValue());
            }
        }
        return level;
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
        row.put("passengerSpeedLimit", Math.max(0, passenger));
        row.put("freightSpeedLimit", Math.max(0, freight));
        row.put("dangerousGoodsSpeedLimit", Math.max(0, freight));
        return row;
    }

    /**
     * 方向参数标准化。
     */
    private Integer normalizeDirection(Integer direction) {
        if (direction == null) {
            return null;
        }
        if (direction != DIRECTION_HAMI && direction != DIRECTION_TURPAN) {
            throw new IllegalArgumentException("direction must be 1(turpan) or 2(hami)");
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
        if ("1".equals(s) || "上行".equals(s) || "吐鲁番".equals(s) || "turpan".equals(s) || "toez".equals(s) || "to_ez".equals(s)) {
            return DIRECTION_TURPAN;
        }
        if ("2".equals(s) || "下行".equals(s) || "哈密".equals(s) || "hami".equals(s) || "towh".equals(s) || "to_wh".equals(s)) {
            return DIRECTION_HAMI;
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
        if (windLevel >= 11) {
            return "red";
        }
        if (windLevel >= 9) {
            return "yellow";
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
    private List<Map<String, Object>> buildWindQueryRecordsFromDb(List<WindData> rows, Integer direction, String period) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        // 以“时间+方向”为键聚合，取该时刻同方向最大风级。
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
            Integer level = toWindLevel(row.getWindSpeed());
            if (level == null) {
                continue;
            }
            String key = row.getTimeStamp().format(DATETIME_FMT) + "#" + rowDirection;
            WindAgg agg = aggMap.computeIfAbsent(key, k -> new WindAgg(row.getTimeStamp(), rowDirection));
            if (agg.maxWindLevel == null || level > agg.maxWindLevel) {
                agg.maxWindLevel = level;
                agg.windDirection = stateService.stringValue(row.getWindDirection());
            }
        }

        List<WindAgg> aggList = new ArrayList<>(aggMap.values());
        aggList.sort(Comparator.comparing(a -> a.time));

        List<Map<String, Object>> records = new ArrayList<>();
        int durationMin = "real".equals(period) ? 5 : 60;
        for (WindAgg agg : aggList) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", agg.time.format(DATETIME_FMT));
            row.put("direction", agg.direction);
            row.put("windLevel", agg.maxWindLevel);
            row.put("windDirection", agg.windDirection == null || agg.windDirection.isBlank() ? "N/A" : agg.windDirection);
            row.put("durationMin", durationMin);
            records.add(row);
        }
        return records;
    }

    /**
     * wind_data 缺失时的风序列回退逻辑。
     */
    private Map<String, Object> buildFallbackWindData(long timestamp, String period, Integer direction) {
        List<Integer> directionList = direction == null
                ? List.of(DIRECTION_HAMI, DIRECTION_TURPAN)
                : List.of(direction);
        int points = "real".equals(period) ? 1 : 72;
        long stepMs = "real".equals(period) ? 5L * 60 * 1000 : 60L * 60 * 1000;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            long t;
            if ("forecast".equals(period)) {
                t = timestamp + (long) i * stepMs;
            } else if ("history".equals(period)) {
                t = timestamp - (long) i * stepMs;
            } else {
                t = timestamp;
            }

            for (Integer dir : directionList) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("time", fmt.format(Instant.ofEpochMilli(t)));
                row.put("direction", dir);
                row.put("windLevel", 7 + (i % 4));
                row.put("windDirection", i % 2 == 0 ? "NW" : "W");
                row.put("durationMin", "real".equals(period) ? 5 : 60);
                records.add(row);
            }
        }
        if ("history".equals(period)) {
            records.sort((a, b) -> stateService.stringValue(a.get("time")).compareTo(stateService.stringValue(b.get("time"))));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("period", period);
        data.put("records", records);
        return data;
    }

    /**
     * 风序列聚合中间对象。
     */
    private static class WindAgg {
        private final LocalDateTime time;
        private final int direction;
        private Integer maxWindLevel;
        private String windDirection;

        private WindAgg(LocalDateTime time, int direction) {
            this.time = time;
            this.direction = direction;
        }
    }
}
