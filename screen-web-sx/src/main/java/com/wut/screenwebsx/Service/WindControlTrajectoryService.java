package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screendbmysqlsx.Model.RoadSegmentStatic;
import com.wut.screendbmysqlsx.Model.Traj;
import com.wut.screendbmysqlsx.Service.RoadSegmentStaticService;
import com.wut.screendbmysqlsx.Service.TrajService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轨迹数据聚合服务。
 *
 * 设计目标：
 * 1. 将 4.1（路段运行状态）与表1-1轨迹表 traj_near_real_xxxx_xx_xx（或 yyyyMMdd 后缀）打通；
 * 2. 统一完成“按路段/方向/时间窗”的车辆统计、速度统计、服务区进出统计与事件识别；
 * 3. 对 4.2 中需要交通量的字段提供轨迹驱动值；
 * 4. 当轨迹表不可用或当前时窗无数据时，返回 null 让上层业务继续走原有回退逻辑，不直接抛错。
 */
@Slf4j
@Service
public class WindControlTrajectoryService {
    /** 下行方向（哈密）。 */
    private static final int DIRECTION_HAMI = 2;
    /** 上行方向（吐鲁番）。 */
    private static final int DIRECTION_TURPAN = 1;
    /** 默认5分钟统计窗。 */
    private static final long WINDOW_5MIN_MS = 5L * 60 * 1000;
    /** 统计窗对应分钟数。 */
    private static final int WINDOW_5MIN = 5;
    /** 桩号匹配规则，支持 K3178 与 K3178+500。 */
    private static final Pattern STAKE_PATTERN = Pattern.compile("K(\\d+)(?:\\+(\\d+))?", Pattern.CASE_INSENSITIVE);
    /** 时间后缀 yyyy_MM_dd。 */
    private static final DateTimeFormatter TABLE_SUFFIX_UNDERSCORE = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    /** 轨迹数据服务。 */
    private final TrajService trajService;
    /** 路段静态服务（提供 start_location_m 与方向定义）。 */
    private final RoadSegmentStaticService roadSegmentStaticService;
    /** 公共状态服务（用于安全取值与统一组装行结构）。 */
    private final WindControlStateService stateService;

    /**
     * 构造函数。
     *
     * @param trajService 轨迹服务
     * @param roadSegmentStaticService 路段静态服务
     * @param stateService 公共状态服务
     */
    public WindControlTrajectoryService(TrajService trajService,
                                        RoadSegmentStaticService roadSegmentStaticService,
                                        WindControlStateService stateService) {
        this.trajService = trajService;
        this.roadSegmentStaticService = roadSegmentStaticService;
        this.stateService = stateService;
    }

    /**
     * 按轨迹数据构建 4.1.5 交通状态分析结果。
     *
     * 口径：
     * 1. 在 5 分钟窗口内，统计每个路段命中的去重轨迹数；
     * 2. 将窗口去重轨迹数折算为小时流量（vehPerHour = count * 12）；
     * 3. 若轨迹源不可用或当前窗口无数据，则返回 null 交由上层回退。
     *
     * @param timestamp 业务时间戳（毫秒）
     * @param sections 风区路段列表
     * @return 交通状态列表；无轨迹数据时返回 null
     */
    public List<Map<String, Object>> buildTrafficStateAnalysis(long timestamp, List<Map<String, Object>> sections) {
        AnalyzeResult result = analyzeBySections(timestamp, sections);
        if (!result.hasTrajectoryData) {
            return null;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SectionContext ctx : result.sectionContextList) {
            SectionMetric metric = result.metricBySectionKey.get(ctx.sectionKey);
            int vehPerHour = metric == null ? 0 : metric.toVehPerHour(WINDOW_5MIN);
            rows.add(stateService.row(
                    "segment", ctx.segmentName,
                    "direction", ctx.direction,
                    "vehPerHour", vehPerHour,
                    "updatedEveryMin", WINDOW_5MIN
            ));
        }
        return rows;
    }

    /**
     * 按轨迹数据构建 4.1.2 断面参数检测结果。
     *
     * 口径：
     * 1. 车辆数使用“窗口末时刻每路段在途去重轨迹数”；
     * 2. 平均速度使用“窗口末时刻在途车辆平均速度（km/h）”；
     * 3. 拥堵状态：>80=SMOOTH，60-80=SLOW，<60=CONGESTED，无速度=NO_DATA；
     * 4. 若轨迹源不可用或当前窗口无数据，则返回 null 交由上层回退。
     *
     * @param timestamp 业务时间戳（毫秒）
     * @param sections 风区路段列表
     * @return 断面参数列表；无轨迹数据时返回 null
     */
    public List<Map<String, Object>> buildSectionParameterDetections(long timestamp, List<Map<String, Object>> sections) {
        AnalyzeResult result = analyzeBySections(timestamp, sections);
        if (!result.hasTrajectoryData) {
            return null;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SectionContext ctx : result.sectionContextList) {
            SectionMetric metric = result.metricBySectionKey.get(ctx.sectionKey);
            int currentCount = metric == null ? 0 : metric.currentVehicleCount;
            Double avgSpeed = metric == null ? null : metric.avgSpeedKmh();
            rows.add(stateService.row(
                    "timestamp", timestamp,
                    "segmentId", ctx.segmentId,
                    "segment", ctx.segmentName,
                    "direction", ctx.direction,
                    "currentVehicleCount", currentCount,
                    "avgSpeedKmPerHour", avgSpeed,
                    "congestionStatus", mapCongestionStatus(avgSpeed),
                    "updateIntervalMin", WINDOW_5MIN
            ));
        }
        return rows;
    }

    /**
     * 按轨迹数据构建 4.1.4 服务区进出统计。
     *
     * 口径：
     * 1. 对每条轨迹在时间窗内按时序扫描，进入服务区记 inbound，离开记 outbound；
     * 2. 最后一个点仍在服务区范围内的轨迹记为 inside；
     * 3. 若轨迹源不可用或当前窗口无数据，则返回 null 交由上层回退。
     *
     * @param timestamp 业务时间戳（毫秒）
     * @param sections 风区路段列表
     * @return 服务区统计列表；无轨迹数据时返回 null
     */
    public List<Map<String, Object>> buildServiceAreaVehicleStats(long timestamp, List<Map<String, Object>> sections) {
        AnalyzeResult result = analyzeBySections(timestamp, sections);
        if (!result.hasTrajectoryData) {
            return null;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SectionContext ctx : result.sectionContextList) {
            if (!ctx.serviceArea) {
                continue;
            }
            SectionMetric metric = result.metricBySectionKey.get(ctx.sectionKey);
            int in = metric == null ? 0 : metric.inboundVehicleCount;
            int out = metric == null ? 0 : metric.outboundVehicleCount;
            int inside = metric == null ? 0 : metric.insideVehicleSet.size();
            rows.add(stateService.row(
                    "serviceArea", ctx.segmentName,
                    "timestamp", timestamp,
                    "inboundVehicle", in,
                    "outboundVehicle", out,
                    "insideVehicle", inside
            ));
        }
        return rows;
    }

    /**
     * 按轨迹数据构建 4.1.3 事件检测信息。
     *
     * 口径：
     * 1. 对每条轨迹取窗口末点位进行识别；
     * 2. 超速阈值优先取“该路段实时风级对应 passengerSpeedLimit”，若缺失则默认 120km/h；
     * 3. 速度<=2km/h 识别为停驶；
     * 4. 若轨迹源不可用或当前窗口无数据，则返回 null 交由上层回退。
     *
     * @param timestamp 业务时间戳（毫秒）
     * @param sections 风区路段列表
     * @param speedThresholdByWindLevel 风级阈值配置
     * @return 事件列表；无轨迹数据时返回 null
     */
    public List<Map<String, Object>> buildEventDetections(long timestamp,
                                                          List<Map<String, Object>> sections,
                                                          Map<Integer, Map<String, Object>> speedThresholdByWindLevel) {
        AnalyzeResult result = analyzeBySections(timestamp, sections);
        if (!result.hasTrajectoryData) {
            return null;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int seq = 1;
        for (LatestMatch match : result.latestMatchList) {
            if (match.speedKmh == null) {
                continue;
            }
            int overspeedThreshold = resolveOverspeedThreshold(match.section.realWindLevel, speedThresholdByWindLevel);
            String vehiclePlate = stateService.stringValue(match.traj.getCarId());
            if (vehiclePlate.isBlank()) {
                vehiclePlate = "TRAJ-" + match.trajId;
            }

            if (overspeedThreshold > 0 && match.speedKmh > overspeedThreshold) {
                rows.add(stateService.row(
                        "eventId", buildEventId(timestamp, seq++),
                        "eventType", "OVERSPEED",
                        "segment", match.section.segmentName,
                        "vehiclePlate", vehiclePlate,
                        "thresholdSpeedKmPerHour", overspeedThreshold,
                        "status", "UNPROCESSED",
                        "timestamp", timestamp
                ));
            }
            if (match.speedKmh <= 2D) {
                rows.add(stateService.row(
                        "eventId", buildEventId(timestamp, seq++),
                        "eventType", "STOPPED",
                        "segment", match.section.segmentName,
                        "vehiclePlate", vehiclePlate,
                        "thresholdSpeedKmPerHour", 0,
                        "status", "UNPROCESSED",
                        "timestamp", timestamp
                ));
            }
            if (rows.size() >= 200) {
                break;
            }
        }
        return rows;
    }

    /**
     * 估算指定桩号区间和方向的小时车流量。
     *
     * 用于 4.2 研判结果中的 trafficVolumeVehPerHour 字段。
     *
     * @param timestamp 业务时间戳（毫秒）
     * @param stakeRange 区间桩号文本，如 K3178-K3193
     * @param direction 方向（1/2）
     * @return 估算小时车流量；无轨迹数据或无法解析区间时返回 null
     */
    public Integer estimateTrafficVolumeVehPerHour(long timestamp, String stakeRange, int direction) {
        Double[] range = parseStakeRangeToRelative(stakeRange, buildDirectionOffsetMap().get(direction));
        if (range == null) {
            return null;
        }

        List<Traj> trajList = loadTrajectoryWindow(timestamp, WINDOW_5MIN_MS);
        if (trajList.isEmpty()) {
            return null;
        }

        Set<Long> vehicleSet = new HashSet<>();
        for (Traj traj : trajList) {
            if (traj.getTrajId() == null || traj.getFrenetX() == null) {
                continue;
            }
            if (normalizeDirection(traj.getRoadDirect(), 0) != direction) {
                continue;
            }
            if (traj.getFrenetX() >= range[0] && traj.getFrenetX() < range[1]) {
                vehicleSet.add(traj.getTrajId());
            }
        }
        return (int) Math.round(vehicleSet.size() * 60D / WINDOW_5MIN);
    }

    /**
     * 对“轨迹点 + 路段定义”做一次完整聚合分析。
     */
    private AnalyzeResult analyzeBySections(long timestamp, List<Map<String, Object>> sections) {
        AnalyzeResult result = new AnalyzeResult();
        result.sectionContextList = buildSectionContexts(sections);
        if (result.sectionContextList.isEmpty()) {
            return result;
        }

        List<Traj> trajWindowList = loadTrajectoryWindow(timestamp, WINDOW_5MIN_MS);
        if (trajWindowList.isEmpty()) {
            return result;
        }
        result.hasTrajectoryData = true;

        Map<Long, List<Traj>> trajById = groupAndSortByTrajId(trajWindowList);
        Map<Integer, List<SectionContext>> sectionByDirection = new HashMap<>();
        for (SectionContext ctx : result.sectionContextList) {
            sectionByDirection.computeIfAbsent(ctx.direction, key -> new ArrayList<>()).add(ctx);
            result.metricBySectionKey.put(ctx.sectionKey, new SectionMetric());
        }

        // 第一阶段：窗口流量 + 末时刻在途车辆 + 末时刻速度
        for (Map.Entry<Long, List<Traj>> entry : trajById.entrySet()) {
            Long trajId = entry.getKey();
            List<Traj> trajList = entry.getValue();
            if (trajList.isEmpty()) {
                continue;
            }

            Set<String> windowHitSectionKeySet = new HashSet<>();
            for (Traj point : trajList) {
                SectionContext hit = matchSection(point, sectionByDirection);
                if (hit != null) {
                    windowHitSectionKeySet.add(hit.sectionKey);
                }
            }
            for (String sectionKey : windowHitSectionKeySet) {
                result.metricBySectionKey.get(sectionKey).windowDistinctVehicleCount++;
            }

            Traj latest = trajList.get(trajList.size() - 1);
            SectionContext latestSection = matchSection(latest, sectionByDirection);
            if (latestSection != null) {
                SectionMetric metric = result.metricBySectionKey.get(latestSection.sectionKey);
                metric.currentVehicleCount++;
                Double speedKmh = calcSpeedKmh(latest);
                if (speedKmh != null) {
                    metric.speedSum += speedKmh;
                    metric.speedCount++;
                }
                result.latestMatchList.add(new LatestMatch(trajId, latest, latestSection, speedKmh));
            }
        }

        // 第二阶段：服务区进出/在内统计（按轨迹时序判定）
        for (SectionContext serviceArea : result.sectionContextList) {
            if (!serviceArea.serviceArea) {
                continue;
            }
            SectionMetric metric = result.metricBySectionKey.get(serviceArea.sectionKey);
            for (Map.Entry<Long, List<Traj>> entry : trajById.entrySet()) {
                Long trajId = entry.getKey();
                List<Traj> trajList = entry.getValue();
                boolean initialized = false;
                boolean prevInside = false;
                for (Traj point : trajList) {
                    boolean currentInside = isPointInSection(point, serviceArea);
                    if (!initialized) {
                        prevInside = currentInside;
                        initialized = true;
                        continue;
                    }
                    if (!prevInside && currentInside) {
                        metric.inboundVehicleCount++;
                    } else if (prevInside && !currentInside) {
                        metric.outboundVehicleCount++;
                    }
                    prevInside = currentInside;
                }
                if (initialized && prevInside) {
                    metric.insideVehicleSet.add(trajId);
                }
            }
        }

        return result;
    }

    /**
     * 加载指定时间窗轨迹点。
     *
     * 支持两种后缀格式：
     * 1. yyyyMMdd（项目内既有格式）
     * 2. yyyy_MM_dd（你当前说明的格式）
     */
    private List<Traj> loadTrajectoryWindow(long timestamp, long windowMs) {
        long windowStart = Math.max(0L, timestamp - windowMs);
        String primarySuffix = DateParamParseUtil.getDateTableStr(timestamp);
        String secondarySuffix = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
                .toLocalDate().format(TABLE_SUFFIX_UNDERSCORE);

        List<Traj> primary = queryTrajectoryWindow(primarySuffix, windowStart, timestamp);
        if (primary != null && !primary.isEmpty()) {
            return primary;
        }

        List<Traj> secondary = queryTrajectoryWindow(secondarySuffix, windowStart, timestamp);
        if (secondary != null && !secondary.isEmpty()) {
            return secondary;
        }

        if (primary != null) {
            return primary;
        }
        if (secondary != null) {
            return secondary;
        }
        log.warn("轨迹表查询失败：suffix={} 或 {}，timestamp={}", primarySuffix, secondarySuffix, timestamp);
        return Collections.emptyList();
    }

    /**
     * 执行单次轨迹查询。查询异常时返回 null，便于上层继续尝试其他后缀。
     */
    private List<Traj> queryTrajectoryWindow(String suffix, long startTimestamp, long endTimestamp) {
        try {
            List<Traj> rows = trajService.getListByTimestampRange(suffix, startTimestamp, endTimestamp);
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception ex) {
            log.debug("轨迹查询失败，suffix={}", suffix, ex);
            return null;
        }
    }

    /**
     * 按 trajId 分组并按时间升序排序。
     */
    private Map<Long, List<Traj>> groupAndSortByTrajId(List<Traj> trajList) {
        Map<Long, List<Traj>> grouped = new HashMap<>();
        for (Traj traj : trajList) {
            if (traj.getTrajId() == null || traj.getTimestamp() == null) {
                continue;
            }
            grouped.computeIfAbsent(traj.getTrajId(), key -> new ArrayList<>()).add(traj);
        }
        for (List<Traj> list : grouped.values()) {
            list.sort(Comparator.comparingLong(item -> item.getTimestamp() == null ? 0L : item.getTimestamp()));
        }
        return grouped;
    }

    /**
     * 根据状态路段构建可计算的区间上下文。
     */
    private List<SectionContext> buildSectionContexts(List<Map<String, Object>> sections) {
        List<SectionContext> contextList = new ArrayList<>();
        if (sections == null || sections.isEmpty()) {
            return contextList;
        }

        List<RoadSegmentStatic> staticSegmentList = roadSegmentStaticService.getEnabledSegments();
        Map<String, RoadSegmentStatic> staticById = new HashMap<>();
        Map<Integer, List<RoadSegmentStatic>> staticByDirection = new HashMap<>();
        for (RoadSegmentStatic segment : staticSegmentList) {
            if (segment.getId() != null) {
                staticById.put(String.valueOf(segment.getId()), segment);
            }
            int direction = normalizeDirection(segment.getDirection(), DIRECTION_HAMI);
            staticByDirection.computeIfAbsent(direction, key -> new ArrayList<>()).add(segment);
        }
        for (List<RoadSegmentStatic> list : staticByDirection.values()) {
            list.sort(Comparator.comparing(item -> item.getStartLocationM() == null ? Integer.MAX_VALUE : item.getStartLocationM()));
        }

        Map<Integer, Double> directionOffsetMap = buildDirectionOffsetMap(staticSegmentList);
        int sequence = 1;
        for (Map<String, Object> sectionRow : sections) {
            SectionContext ctx = new SectionContext();
            ctx.segmentId = stateService.stringValue(sectionRow.get("segmentId"));
            ctx.segmentName = stateService.stringValue(sectionRow.get("segmentName"));
            ctx.direction = stateService.intValue(sectionRow.get("direction"), DIRECTION_HAMI);
            ctx.realWindLevel = stateService.intValue(sectionRow.get("realWindLevel"), 6);
            ctx.sectionKey = (ctx.segmentId.isBlank() ? "AUTO-" + sequence : ctx.segmentId) + "#" + ctx.direction;

            RoadSegmentStatic matchedStatic = staticById.get(ctx.segmentId);
            if (matchedStatic != null && matchedStatic.getStartLocationM() != null) {
                ctx.startMeter = matchedStatic.getStartLocationM().doubleValue();
                ctx.endMeter = resolveSegmentEndMeter(matchedStatic, staticByDirection.getOrDefault(ctx.direction, Collections.emptyList()));
                ctx.serviceArea = isServiceAreaType(matchedStatic.getSegmentType()) || ctx.segmentName.contains("服务区");
            } else {
                Double[] range = parseStakeRangeToRelative(ctx.segmentName, directionOffsetMap.get(ctx.direction));
                if (range != null) {
                    ctx.startMeter = range[0];
                    ctx.endMeter = range[1];
                }
                ctx.serviceArea = ctx.segmentName.contains("服务区");
            }
            contextList.add(ctx);
            sequence++;
        }
        return contextList;
    }

    /**
     * 构建方向对应的“桩号绝对米值 - start_location_m”偏移。
     */
    private Map<Integer, Double> buildDirectionOffsetMap() {
        return buildDirectionOffsetMap(roadSegmentStaticService.getEnabledSegments());
    }

    /**
     * 基于静态路段构建偏移表（供 stakeRange 转换为 frenet 区间）。
     */
    private Map<Integer, Double> buildDirectionOffsetMap(List<RoadSegmentStatic> segmentList) {
        Map<Integer, Double> offsetMap = new HashMap<>();
        for (RoadSegmentStatic segment : segmentList) {
            if (segment.getStartLocationM() == null) {
                continue;
            }
            Double stakeAbsMeter = parseStakeToMeter(segment.getStartStake());
            if (stakeAbsMeter == null) {
                continue;
            }
            int direction = normalizeDirection(segment.getDirection(), DIRECTION_HAMI);
            offsetMap.putIfAbsent(direction, stakeAbsMeter - segment.getStartLocationM());
        }
        return offsetMap;
    }

    /**
     * 推断路段终点位置。
     *
     * 优先级：
     * 1. 由 startStake/endStake 解析长度；
     * 2. 同方向下一条路段的 startLocation；
     * 3. 默认 +1000m。
     */
    private double resolveSegmentEndMeter(RoadSegmentStatic current, List<RoadSegmentStatic> sameDirectionList) {
        double start = current.getStartLocationM() == null ? 0D : current.getStartLocationM();
        Double startStakeMeter = parseStakeToMeter(current.getStartStake());
        Double endStakeMeter = parseStakeToMeter(current.getEndStake());
        if (startStakeMeter != null && endStakeMeter != null && endStakeMeter > startStakeMeter) {
            return start + (endStakeMeter - startStakeMeter);
        }

        Integer nextStart = null;
        for (RoadSegmentStatic segment : sameDirectionList) {
            if (segment.getStartLocationM() == null) {
                continue;
            }
            if (segment.getStartLocationM() > start) {
                nextStart = segment.getStartLocationM();
                break;
            }
        }
        if (nextStart != null) {
            return nextStart.doubleValue();
        }
        return start + 1000D;
    }

    /**
     * 将文本中的前两个桩号提取并映射为 frenet 区间。
     */
    private Double[] parseStakeRangeToRelative(String text, Double offset) {
        if (text == null || text.isBlank() || offset == null) {
            return null;
        }
        Matcher matcher = STAKE_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
        List<Double> valueList = new ArrayList<>();
        while (matcher.find()) {
            String km = matcher.group(1);
            String meter = matcher.group(2);
            double absolute = Double.parseDouble(km) * 1000D + (meter == null ? 0D : Double.parseDouble(meter));
            valueList.add(absolute);
        }
        if (valueList.size() < 2) {
            return null;
        }

        double start = valueList.get(0) - offset;
        double end = valueList.get(1) - offset;
        if (end < start) {
            double t = start;
            start = end;
            end = t;
        }
        return new Double[]{start, end};
    }

    /**
     * 解析单个桩号为绝对米值。
     */
    private Double parseStakeToMeter(String stake) {
        if (stake == null || stake.isBlank()) {
            return null;
        }
        Matcher matcher = STAKE_PATTERN.matcher(stake.toUpperCase(Locale.ROOT));
        if (!matcher.find()) {
            return null;
        }
        double km = Double.parseDouble(matcher.group(1));
        String meterGroup = matcher.group(2);
        double meter = meterGroup == null ? 0D : Double.parseDouble(meterGroup);
        return km * 1000D + meter;
    }

    /**
     * 匹配轨迹点所属路段。
     */
    private SectionContext matchSection(Traj point, Map<Integer, List<SectionContext>> sectionByDirection) {
        int direction = normalizeDirection(point.getRoadDirect(), 0);
        if (direction == 0 || point.getFrenetX() == null) {
            return null;
        }
        List<SectionContext> candidateList = sectionByDirection.get(direction);
        if (candidateList == null || candidateList.isEmpty()) {
            return null;
        }
        for (SectionContext ctx : candidateList) {
            if (ctx.startMeter == null || ctx.endMeter == null || ctx.endMeter <= ctx.startMeter) {
                continue;
            }
            double x = point.getFrenetX();
            if (x >= ctx.startMeter && x < ctx.endMeter) {
                return ctx;
            }
        }
        return null;
    }

    /**
     * 判断点位是否在某个路段范围内。
     */
    private boolean isPointInSection(Traj point, SectionContext section) {
        if (point == null || section == null || point.getFrenetX() == null) {
            return false;
        }
        int pointDirection = normalizeDirection(point.getRoadDirect(), 0);
        if (pointDirection != section.direction) {
            return false;
        }
        if (section.startMeter == null || section.endMeter == null || section.endMeter <= section.startMeter) {
            return false;
        }
        double x = point.getFrenetX();
        return x >= section.startMeter && x < section.endMeter;
    }

    /**
     * 计算轨迹点速度（km/h）。
     */
    private Double calcSpeedKmh(Traj traj) {
        if (traj == null) {
            return null;
        }
        Double speedX = traj.getSpeedX();
        Double speedY = traj.getSpeedY();
        if (speedX == null && speedY == null) {
            return null;
        }
        double vx = speedX == null ? 0D : speedX;
        double vy = speedY == null ? 0D : speedY;
        double speed = Math.sqrt(vx * vx + vy * vy) * 3.6D;
        if (Double.isNaN(speed) || Double.isInfinite(speed)) {
            return null;
        }
        return Math.round(speed * 10D) / 10D;
    }

    /**
     * 统一拥堵状态映射。
     */
    private String mapCongestionStatus(Double avgSpeedKmh) {
        if (avgSpeedKmh == null) {
            return "NO_DATA";
        }
        if (avgSpeedKmh > 80D) {
            return "SMOOTH";
        }
        if (avgSpeedKmh >= 60D) {
            return "SLOW";
        }
        return "CONGESTED";
    }

    /**
     * 根据风级阈值表解析超速阈值。
     */
    private int resolveOverspeedThreshold(int windLevel, Map<Integer, Map<String, Object>> thresholdByWindLevel) {
        if (thresholdByWindLevel == null || thresholdByWindLevel.isEmpty()) {
            return 120;
        }
        Map<String, Object> row = thresholdByWindLevel.get(windLevel);
        if (row == null) {
            return 120;
        }
        int threshold = stateService.intValue(row.get("passengerSpeedLimit"), 120);
        return Math.max(0, threshold);
    }

    /**
     * 事件ID生成。
     */
    private String buildEventId(long timestamp, int sequence) {
        return "DET-" + (timestamp % 100000) + "-" + sequence;
    }

    /**
     * 路段类型是否可判定为服务区。
     */
    private boolean isServiceAreaType(String segmentType) {
        if (segmentType == null) {
            return false;
        }
        String value = segmentType.toUpperCase(Locale.ROOT);
        return segmentType.contains("服务区") || value.contains("SERVICE");
    }

    /**
     * 方向统一映射到 1/2。
     */
    private int normalizeDirection(Object rawDirection, int defaultValue) {
        if (rawDirection == null) {
            return defaultValue;
        }
        if (rawDirection instanceof Number number) {
            int value = number.intValue();
            return value == DIRECTION_TURPAN ? DIRECTION_TURPAN : (value == DIRECTION_HAMI ? DIRECTION_HAMI : defaultValue);
        }
        String text = String.valueOf(rawDirection).trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return defaultValue;
        }
        if ("1".equals(text) || "上行".equals(text) || "吐鲁番".equals(text) || "turpan".equals(text) || "toez".equals(text) || "to_ez".equals(text)) {
            return DIRECTION_TURPAN;
        }
        if ("2".equals(text) || "下行".equals(text) || "哈密".equals(text) || "hami".equals(text) || "towh".equals(text) || "to_wh".equals(text)) {
            return DIRECTION_HAMI;
        }
        return defaultValue;
    }

    /**
     * 路段上下文。
     */
    private static class SectionContext {
        private String sectionKey;
        private String segmentId;
        private String segmentName;
        private int direction;
        private int realWindLevel;
        private boolean serviceArea;
        private Double startMeter;
        private Double endMeter;
    }

    /**
     * 路段统计中间对象。
     */
    private static class SectionMetric {
        private int windowDistinctVehicleCount;
        private int currentVehicleCount;
        private double speedSum;
        private int speedCount;
        private int inboundVehicleCount;
        private int outboundVehicleCount;
        private final Set<Long> insideVehicleSet = new HashSet<>();

        private int toVehPerHour(int windowMinutes) {
            if (windowMinutes <= 0) {
                return 0;
            }
            return (int) Math.round(windowDistinctVehicleCount * 60D / windowMinutes);
        }

        private Double avgSpeedKmh() {
            if (speedCount <= 0) {
                return null;
            }
            return Math.round((speedSum / speedCount) * 10D) / 10D;
        }
    }

    /**
     * 末时刻匹配对象。
     */
    private static class LatestMatch {
        private final Long trajId;
        private final Traj traj;
        private final SectionContext section;
        private final Double speedKmh;

        private LatestMatch(Long trajId, Traj traj, SectionContext section, Double speedKmh) {
            this.trajId = trajId;
            this.traj = traj;
            this.section = section;
            this.speedKmh = speedKmh;
        }
    }

    /**
     * 聚合分析结果。
     */
    private static class AnalyzeResult {
        private boolean hasTrajectoryData;
        private List<SectionContext> sectionContextList = new ArrayList<>();
        private final Map<String, SectionMetric> metricBySectionKey = new LinkedHashMap<>();
        private final List<LatestMatch> latestMatchList = new ArrayList<>();
    }
}
