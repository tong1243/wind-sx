package com.wut.screenwebsx.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wut.screendbmysqlsx.Model.WindData;
import com.wut.screendbmysqlsx.Service.WindDataService;
import com.wut.screenwebsx.Mapper.WindRiskSectionHourlyMapper;
import com.wut.screenwebsx.Mapper.WindSpeedLimitHourlyMapper;
import com.wut.screenwebsx.Model.WindRiskSectionHourly;
import com.wut.screenwebsx.Model.WindSpeedLimitHourly;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WindRiskSpeedService {
    private static final int DIRECTION_TURPAN = 1;
    private static final int DIRECTION_HAMI = 2;

    private static final BigDecimal RISK_THRESHOLD = new BigDecimal("13.9");
    private static final BigDecimal L4_THRESHOLD = new BigDecimal("17.2");
    private static final BigDecimal L3_THRESHOLD = new BigDecimal("24.5");
    private static final BigDecimal L2_THRESHOLD = new BigDecimal("28.5");
    private static final BigDecimal MERGE_DISTANCE_KM = new BigDecimal("3.0");

    private static final String NO_RISK = "\u65e0";
    private static final String DEFAULT_OUTPUT_SOURCE = "MATLAB_RULE_V1";
    private static final Pattern STAKE_PATTERN = Pattern.compile("(?i)k(\\d+)(?:\\+(\\d+))?");

    private static final List<RoadDef> ROAD_DEFS = List.of(
            new RoadDef(DIRECTION_TURPAN, 1, 3178D, 3183D, "k3178-k3183"),
            new RoadDef(DIRECTION_TURPAN, 2, 3183D, 3188D, "k3183-k3188"),
            new RoadDef(DIRECTION_TURPAN, 3, 3188D, 3193D, "k3188-k3193"),
            new RoadDef(DIRECTION_TURPAN, 4, 3193D, 3198D, "k3193-k3198"),
            new RoadDef(DIRECTION_TURPAN, 5, 3198D, 3204D, "k3198-k3204"),
            new RoadDef(DIRECTION_HAMI, 1, 3204D, 3199D, "k3204-k3199"),
            new RoadDef(DIRECTION_HAMI, 2, 3199D, 3194D, "k3199-k3194"),
            new RoadDef(DIRECTION_HAMI, 3, 3194D, 3189D, "k3194-k3189"),
            new RoadDef(DIRECTION_HAMI, 4, 3189D, 3184D, "k3189-k3184"),
            new RoadDef(DIRECTION_HAMI, 5, 3184D, 3178D, "k3184-k3178")
    );

    private final WindDataService windDataService;
    private final WindRiskSectionHourlyMapper windRiskSectionHourlyMapper;
    private final WindSpeedLimitHourlyMapper windSpeedLimitHourlyMapper;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> calculateAndPersist(long startTimestamp,
                                                   long endTimestamp,
                                                   String inputDataSource,
                                                   String outputDataSource) {
        if (endTimestamp < startTimestamp) {
            throw new IllegalArgumentException("endTimestamp must be >= startTimestamp");
        }

        LocalDateTime start = truncateToHour(toLocalDateTime(startTimestamp));
        LocalDateTime end = truncateToHour(toLocalDateTime(endTimestamp));
        String finalOutputSource = hasText(outputDataSource) ? outputDataSource.trim() : DEFAULT_OUTPUT_SOURCE;
        String finalInputSource = hasText(inputDataSource) ? inputDataSource.trim() : null;

        List<WindData> sourceRows = windDataService.listByTimeRange(start, end);
        Map<HourDirectionKey, List<WindData>> rowByHourDirection = groupRowsByHourDirection(sourceRows, finalInputSource);

        Set<LocalDateTime> hourSet = new HashSet<>();
        List<WindRiskSectionHourly> riskRows = new ArrayList<>();
        List<WindSpeedLimitHourly> speedRows = new ArrayList<>();
        for (Map.Entry<HourDirectionKey, List<WindData>> entry : rowByHourDirection.entrySet()) {
            LocalDateTime timestamp = entry.getKey().timeStamp();
            Integer direction = entry.getKey().direction();
            hourSet.add(timestamp);

            List<SegmentWind> segments = collectSegments(entry.getValue());
            riskRows.add(buildRiskRow(timestamp, direction, segments, finalOutputSource));
            speedRows.addAll(buildSpeedRows(timestamp, direction, segments, finalOutputSource));
        }

        replaceResultRows(start, end, finalOutputSource, riskRows, speedRows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startTime", start);
        result.put("endTime", end);
        result.put("inputDataSource", finalInputSource);
        result.put("outputDataSource", finalOutputSource);
        result.put("hourCount", hourSet.size());
        result.put("hourDirectionCount", rowByHourDirection.size());
        result.put("riskRowCount", riskRows.size());
        result.put("speedRowCount", speedRows.size());
        return result;
    }

    public List<WindRiskSectionHourly> listRiskSections(long startTimestamp,
                                                        long endTimestamp,
                                                        String outputDataSource,
                                                        Integer direction) {
        if (endTimestamp < startTimestamp) {
            throw new IllegalArgumentException("endTimestamp must be >= startTimestamp");
        }
        LocalDateTime start = truncateToHour(toLocalDateTime(startTimestamp));
        LocalDateTime end = truncateToHour(toLocalDateTime(endTimestamp));
        String source = hasText(outputDataSource) ? outputDataSource.trim() : DEFAULT_OUTPUT_SOURCE;
        Integer normalizedDirection = normalizeQueryDirection(direction);

        LambdaQueryWrapper<WindRiskSectionHourly> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(WindRiskSectionHourly::getTimeStamp, start)
                .le(WindRiskSectionHourly::getTimeStamp, end)
                .eq(WindRiskSectionHourly::getDataSource, source);
        if (normalizedDirection != null) {
            wrapper.eq(WindRiskSectionHourly::getDirection, normalizedDirection);
        }
        wrapper.orderByAsc(WindRiskSectionHourly::getTimeStamp)
                .orderByAsc(WindRiskSectionHourly::getDirection)
                .orderByAsc(WindRiskSectionHourly::getId);
        return windRiskSectionHourlyMapper.selectList(wrapper);
    }

    public List<WindSpeedLimitHourly> listSpeedLimits(long startTimestamp,
                                                      long endTimestamp,
                                                      String outputDataSource,
                                                      Integer direction) {
        if (endTimestamp < startTimestamp) {
            throw new IllegalArgumentException("endTimestamp must be >= startTimestamp");
        }
        LocalDateTime start = truncateToHour(toLocalDateTime(startTimestamp));
        LocalDateTime end = truncateToHour(toLocalDateTime(endTimestamp));
        String source = hasText(outputDataSource) ? outputDataSource.trim() : DEFAULT_OUTPUT_SOURCE;
        Integer normalizedDirection = normalizeQueryDirection(direction);

        LambdaQueryWrapper<WindSpeedLimitHourly> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(WindSpeedLimitHourly::getTimeStamp, start)
                .le(WindSpeedLimitHourly::getTimeStamp, end)
                .eq(WindSpeedLimitHourly::getDataSource, source);
        if (normalizedDirection != null) {
            wrapper.eq(WindSpeedLimitHourly::getDirection, normalizedDirection);
        }
        wrapper.orderByAsc(WindSpeedLimitHourly::getTimeStamp)
                .orderByAsc(WindSpeedLimitHourly::getDirection)
                .orderByAsc(WindSpeedLimitHourly::getSectionOrder)
                .orderByAsc(WindSpeedLimitHourly::getId);
        return windSpeedLimitHourlyMapper.selectList(wrapper);
    }

    private Map<HourDirectionKey, List<WindData>> groupRowsByHourDirection(List<WindData> sourceRows,
                                                                            String inputDataSource) {
        Comparator<HourDirectionKey> comparator = Comparator
                .comparing(HourDirectionKey::timeStamp)
                .thenComparing(HourDirectionKey::direction);
        Map<HourDirectionKey, List<WindData>> grouped = new TreeMap<>(comparator);
        for (WindData row : sourceRows) {
            if (!matchesInputSource(row.getDataSource(), inputDataSource)) {
                continue;
            }
            LocalDateTime timeStamp = row.getTimeStamp();
            if (timeStamp == null) {
                continue;
            }
            Integer direction = normalizeRowDirection(row.getDirection());
            if (direction == null) {
                continue;
            }
            HourDirectionKey key = new HourDirectionKey(truncateToHour(timeStamp), direction);
            grouped.computeIfAbsent(key, unused -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private void replaceResultRows(LocalDateTime start,
                                   LocalDateTime end,
                                   String outputDataSource,
                                   List<WindRiskSectionHourly> riskRows,
                                   List<WindSpeedLimitHourly> speedRows) {
        LambdaQueryWrapper<WindRiskSectionHourly> riskDelete = new LambdaQueryWrapper<>();
        riskDelete.ge(WindRiskSectionHourly::getTimeStamp, start)
                .le(WindRiskSectionHourly::getTimeStamp, end)
                .eq(WindRiskSectionHourly::getDataSource, outputDataSource);
        windRiskSectionHourlyMapper.delete(riskDelete);

        LambdaQueryWrapper<WindSpeedLimitHourly> speedDelete = new LambdaQueryWrapper<>();
        speedDelete.ge(WindSpeedLimitHourly::getTimeStamp, start)
                .le(WindSpeedLimitHourly::getTimeStamp, end)
                .eq(WindSpeedLimitHourly::getDataSource, outputDataSource);
        windSpeedLimitHourlyMapper.delete(speedDelete);

        for (WindRiskSectionHourly row : riskRows) {
            windRiskSectionHourlyMapper.insert(row);
        }
        for (WindSpeedLimitHourly row : speedRows) {
            windSpeedLimitHourlyMapper.insert(row);
        }
    }

    private List<SegmentWind> collectSegments(List<WindData> rows) {
        Map<String, SegmentWind> temp = new HashMap<>();
        for (WindData row : rows) {
            Double startStake = parseStakeKm(row.getStartStake());
            Double endStake = parseStakeKm(row.getEndStake());
            SegmentWind segment = buildSegment(startStake, endStake, row.getWindSpeed());
            if (segment == null) {
                continue;
            }
            String key = String.format(Locale.ROOT, "%.3f|%.3f", segment.startKm(), segment.endKm());
            SegmentWind exists = temp.get(key);
            if (exists == null || segment.windSpeed() > exists.windSpeed()) {
                temp.put(key, segment);
            }
        }
        List<SegmentWind> result = new ArrayList<>(temp.values());
        result.sort(Comparator.comparingDouble(SegmentWind::startKm));
        return result;
    }

    private SegmentWind buildSegment(Double startStake, Double endStake, BigDecimal windSpeedDecimal) {
        if (startStake == null && endStake == null) {
            return null;
        }
        double segmentStart;
        double segmentEnd;
        double stationKm;
        if (startStake != null && endStake != null) {
            segmentStart = Math.min(startStake, endStake);
            segmentEnd = Math.max(startStake, endStake);
            stationKm = startStake;
        } else if (startStake != null) {
            segmentStart = startStake;
            segmentEnd = startStake + 1D;
            stationKm = startStake;
        } else {
            segmentStart = endStake;
            segmentEnd = endStake + 1D;
            stationKm = endStake;
        }

        double windSpeed = windSpeedDecimal == null ? 0D : windSpeedDecimal.doubleValue();
        return new SegmentWind(segmentStart, segmentEnd, stationKm, windSpeed);
    }

    private WindRiskSectionHourly buildRiskRow(LocalDateTime timestamp,
                                               int direction,
                                               List<SegmentWind> segments,
                                               String outputDataSource) {
        List<Range> riskRanges = segments.stream()
                .filter(segment -> BigDecimal.valueOf(segment.windSpeed()).compareTo(RISK_THRESHOLD) >= 0)
                .map(segment -> new Range(segment.startKm(), segment.endKm()))
                .sorted(Comparator.comparingDouble(Range::start))
                .collect(Collectors.toList());

        List<Range> merged = mergeRiskRanges(riskRanges);
        String riskSections = merged.isEmpty()
                ? NO_RISK
                : merged.stream()
                .map(range -> String.format(Locale.ROOT, "k%.3f-k%.3f", range.start(), range.end()))
                .collect(Collectors.joining(", "));

        LocalDateTime now = LocalDateTime.now();
        WindRiskSectionHourly row = new WindRiskSectionHourly();
        row.setTimeStamp(timestamp);
        row.setDirection(direction);
        row.setWindThreshold(RISK_THRESHOLD);
        row.setMergeDistanceKm(MERGE_DISTANCE_KM);
        row.setRiskSectionCount(merged.size());
        row.setRiskSections(riskSections);
        row.setDataSource(outputDataSource);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private List<WindSpeedLimitHourly> buildSpeedRows(LocalDateTime timestamp,
                                                      int direction,
                                                      List<SegmentWind> segments,
                                                      String outputDataSource) {
        List<WindSpeedLimitHourly> result = new ArrayList<>();
        List<RoadDef> directionDefs = ROAD_DEFS.stream()
                .filter(def -> def.direction() == direction)
                .sorted(Comparator.comparingInt(RoadDef::order))
                .collect(Collectors.toList());

        for (RoadDef def : directionDefs) {
            double maxWind = segments.stream()
                    .filter(segment -> def.contains(segment.stationKm()))
                    .mapToDouble(SegmentWind::windSpeed)
                    .max()
                    .orElse(0D);

            LevelLimit levelLimit = resolveLevelLimit(maxWind);

            LocalDateTime now = LocalDateTime.now();
            WindSpeedLimitHourly row = new WindSpeedLimitHourly();
            row.setTimeStamp(timestamp);
            row.setDirection(direction);
            row.setSectionOrder(def.order());
            row.setSectionName(def.sectionName());
            row.setSectionStartKm(toDecimal(def.startKm(), 3));
            row.setSectionEndKm(toDecimal(def.endKm(), 3));
            row.setMaxWindSpeed(toDecimal(maxWind, 1));
            row.setControlLevel(levelLimit.level());
            row.setLevelDesc("LEVEL " + levelLimit.level());
            row.setCarSpeedLimit(levelLimit.carSpeed());
            row.setTruckSpeedLimit(levelLimit.truckSpeed());
            row.setNote(levelLimit.level() == 1 ? "For stranded vehicles" : "");
            row.setDataSource(outputDataSource);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            result.add(row);
        }
        return result;
    }

    private List<Range> mergeRiskRanges(List<Range> sortedRanges) {
        if (sortedRanges.isEmpty()) {
            return List.of();
        }
        List<Range> merged = new ArrayList<>();
        double curStart = sortedRanges.get(0).start();
        double curEnd = sortedRanges.get(0).end();

        for (int i = 1; i < sortedRanges.size(); i++) {
            Range next = sortedRanges.get(i);
            double gap = next.start() - curEnd;
            if (BigDecimal.valueOf(gap).compareTo(MERGE_DISTANCE_KM) < 0) {
                curEnd = Math.max(curEnd, next.end());
            } else {
                merged.add(new Range(curStart, curEnd));
                curStart = next.start();
                curEnd = next.end();
            }
        }
        merged.add(new Range(curStart, curEnd));
        return merged;
    }

    private LevelLimit resolveLevelLimit(double maxWind) {
        BigDecimal wind = BigDecimal.valueOf(maxWind);
        if (wind.compareTo(RISK_THRESHOLD) < 0) {
            return new LevelLimit(5, 120, 80);
        }
        if (wind.compareTo(L4_THRESHOLD) < 0) {
            return new LevelLimit(4, 80, 60);
        }
        if (wind.compareTo(L3_THRESHOLD) < 0) {
            return new LevelLimit(3, 60, 40);
        }
        if (wind.compareTo(L2_THRESHOLD) < 0) {
            return new LevelLimit(2, 20, 20);
        }
        return new LevelLimit(1, 20, 20);
    }

    private Double parseStakeKm(String stake) {
        if (!hasText(stake)) {
            return null;
        }
        Matcher matcher = STAKE_PATTERN.matcher(stake.trim());
        if (matcher.find()) {
            double km = Double.parseDouble(matcher.group(1));
            String meterText = matcher.group(2);
            if (meterText != null && !meterText.isBlank()) {
                km += Double.parseDouble(meterText) / 1000D;
            }
            return km;
        }
        String normalized = stake.trim().replace("K", "").replace("k", "");
        try {
            return Double.parseDouble(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer normalizeRowDirection(Integer direction) {
        if (direction == null) {
            return null;
        }
        if (direction == DIRECTION_TURPAN || direction == DIRECTION_HAMI) {
            return direction;
        }
        return null;
    }

    private Integer normalizeQueryDirection(Integer direction) {
        if (direction == null) {
            return null;
        }
        if (direction == DIRECTION_TURPAN || direction == DIRECTION_HAMI) {
            return direction;
        }
        throw new IllegalArgumentException("direction must be 1(turpan) or 2(hami)");
    }

    private LocalDateTime toLocalDateTime(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    }

    private LocalDateTime truncateToHour(LocalDateTime time) {
        return time.withMinute(0).withSecond(0).withNano(0);
    }

    private BigDecimal toDecimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean matchesInputSource(String rowSource, String filter) {
        if (!hasText(filter)) {
            return true;
        }
        if (!hasText(rowSource)) {
            return false;
        }
        String f = filter.trim();
        if (f.endsWith("*")) {
            String prefix = f.substring(0, f.length() - 1);
            if (!hasText(prefix)) {
                return true;
            }
            return rowSource.regionMatches(true, 0, prefix, 0, prefix.length());
        }
        return f.equalsIgnoreCase(rowSource);
    }

    private record RoadDef(int direction, int order, double startKm, double endKm, String sectionName) {
        private boolean contains(double stationKm) {
            if (startKm < endKm) {
                return stationKm >= startKm && stationKm < endKm;
            }
            return stationKm <= startKm && stationKm > endKm;
        }
    }

    private record HourDirectionKey(LocalDateTime timeStamp, int direction) {
    }

    private record SegmentWind(double startKm, double endKm, double stationKm, double windSpeed) {
    }

    private record Range(double start, double end) {
    }

    private record LevelLimit(int level, int carSpeed, int truckSpeed) {
    }
}
