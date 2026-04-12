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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            new RoadDef(1, DIRECTION_TURPAN, 3178D, 3183D, "k3178-k3183"),
            new RoadDef(2, DIRECTION_TURPAN, 3183D, 3188D, "k3183-k3188"),
            new RoadDef(3, DIRECTION_TURPAN, 3188D, 3193D, "k3188-k3193"),
            new RoadDef(4, DIRECTION_TURPAN, 3193D, 3198D, "k3193-k3198"),
            new RoadDef(5, DIRECTION_TURPAN, 3198D, 3204D, "k3198-k3204"),
            new RoadDef(1, DIRECTION_HAMI, 3204D, 3199D, "k3204-k3199"),
            new RoadDef(2, DIRECTION_HAMI, 3199D, 3194D, "k3199-k3194"),
            new RoadDef(3, DIRECTION_HAMI, 3194D, 3189D, "k3194-k3189"),
            new RoadDef(4, DIRECTION_HAMI, 3189D, 3184D, "k3189-k3184"),
            new RoadDef(5, DIRECTION_HAMI, 3184D, 3178D, "k3184-k3178")
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
        List<WindData> rows = sourceRows.stream()
                .filter(row -> matchesInputSource(row.getDataSource(), finalInputSource))
                .collect(Collectors.toList());

        Map<LocalDateTime, List<WindData>> rowByHour = rows.stream()
                .filter(row -> row.getTimeStamp() != null)
                .collect(Collectors.groupingBy(
                        row -> truncateToHour(row.getTimeStamp()),
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<WindRiskSectionHourly> riskRows = new ArrayList<>();
        List<WindSpeedLimitHourly> speedRows = new ArrayList<>();
        for (Map.Entry<LocalDateTime, List<WindData>> entry : rowByHour.entrySet()) {
            LocalDateTime timestamp = entry.getKey();
            Map<Integer, List<SegmentWind>> segmentsByDirection = collectSegmentsByDirection(entry.getValue());
            for (Integer direction : List.of(DIRECTION_TURPAN, DIRECTION_HAMI)) {
                List<SegmentWind> segments = segmentsByDirection.getOrDefault(direction, List.of());
                riskRows.add(buildRiskRow(timestamp, direction, segments, finalOutputSource));
                speedRows.addAll(buildSpeedRows(timestamp, direction, segments, finalOutputSource));
            }
        }

        replaceResultRows(start, end, finalOutputSource, riskRows, speedRows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startTime", start);
        result.put("endTime", end);
        result.put("inputDataSource", finalInputSource);
        result.put("outputDataSource", finalOutputSource);
        result.put("hourCount", rowByHour.size());
        result.put("riskRowCount", riskRows.size());
        result.put("speedRowCount", speedRows.size());
        return result;
    }

    public List<WindRiskSectionHourly> listRiskSections(long startTimestamp,
                                                        long endTimestamp,
                                                        Integer direction,
                                                        String outputDataSource) {
        if (endTimestamp < startTimestamp) {
            throw new IllegalArgumentException("endTimestamp must be >= startTimestamp");
        }
        LocalDateTime start = truncateToHour(toLocalDateTime(startTimestamp));
        LocalDateTime end = truncateToHour(toLocalDateTime(endTimestamp));
        String source = hasText(outputDataSource) ? outputDataSource.trim() : DEFAULT_OUTPUT_SOURCE;

        LambdaQueryWrapper<WindRiskSectionHourly> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(WindRiskSectionHourly::getTimeStamp, start)
                .le(WindRiskSectionHourly::getTimeStamp, end)
                .eq(WindRiskSectionHourly::getDataSource, source)
                .orderByAsc(WindRiskSectionHourly::getTimeStamp)
                .orderByAsc(WindRiskSectionHourly::getDirection)
                .orderByAsc(WindRiskSectionHourly::getId);
        Integer normalizedDirection = normalizeDirection(direction);
        if (normalizedDirection != null) {
            wrapper.eq(WindRiskSectionHourly::getDirection, normalizedDirection);
        }
        return windRiskSectionHourlyMapper.selectList(wrapper);
    }

    public List<WindSpeedLimitHourly> listSpeedLimits(long startTimestamp,
                                                      long endTimestamp,
                                                      Integer direction,
                                                      String outputDataSource) {
        if (endTimestamp < startTimestamp) {
            throw new IllegalArgumentException("endTimestamp must be >= startTimestamp");
        }
        LocalDateTime start = truncateToHour(toLocalDateTime(startTimestamp));
        LocalDateTime end = truncateToHour(toLocalDateTime(endTimestamp));
        String source = hasText(outputDataSource) ? outputDataSource.trim() : DEFAULT_OUTPUT_SOURCE;

        LambdaQueryWrapper<WindSpeedLimitHourly> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(WindSpeedLimitHourly::getTimeStamp, start)
                .le(WindSpeedLimitHourly::getTimeStamp, end)
                .eq(WindSpeedLimitHourly::getDataSource, source)
                .orderByAsc(WindSpeedLimitHourly::getTimeStamp)
                .orderByAsc(WindSpeedLimitHourly::getDirection)
                .orderByAsc(WindSpeedLimitHourly::getSectionOrder)
                .orderByAsc(WindSpeedLimitHourly::getId);
        Integer normalizedDirection = normalizeDirection(direction);
        if (normalizedDirection != null) {
            wrapper.eq(WindSpeedLimitHourly::getDirection, normalizedDirection);
        }
        return windSpeedLimitHourlyMapper.selectList(wrapper);
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

    private Map<Integer, List<SegmentWind>> collectSegmentsByDirection(List<WindData> rows) {
        Map<Integer, Map<String, SegmentWind>> temp = new HashMap<>();
        for (WindData row : rows) {
            Integer direction = toDirectionCode(row.getDirection());
            if (direction == null) {
                continue;
            }
            Double startKm = parseStakeKm(row.getStartStake());
            Double endKm = parseStakeKm(row.getEndStake());
            if (startKm == null || endKm == null) {
                continue;
            }
            double windSpeed = row.getWindSpeed() == null ? 0D : row.getWindSpeed().doubleValue();
            SegmentWind segment = new SegmentWind(startKm, endKm, windSpeed);
            String key = String.format(Locale.ROOT, "%.3f|%.3f", startKm, endKm);
            temp.computeIfAbsent(direction, ignored -> new HashMap<>());
            SegmentWind exists = temp.get(direction).get(key);
            if (exists == null || windSpeed > exists.windSpeed()) {
                temp.get(direction).put(key, segment);
            }
        }

        Map<Integer, List<SegmentWind>> result = new HashMap<>();
        for (Map.Entry<Integer, Map<String, SegmentWind>> entry : temp.entrySet()) {
            List<SegmentWind> list = new ArrayList<>(entry.getValue().values());
            list.sort(Comparator.comparingDouble(SegmentWind::startKm));
            result.put(entry.getKey(), list);
        }
        return result;
    }

    private WindRiskSectionHourly buildRiskRow(LocalDateTime timestamp,
                                               Integer direction,
                                               List<SegmentWind> segments,
                                               String outputDataSource) {
        List<Range> riskRanges = segments.stream()
                .filter(segment -> BigDecimal.valueOf(segment.windSpeed()).compareTo(RISK_THRESHOLD) >= 0)
                .map(segment -> new Range(
                        Math.min(segment.startKm(), segment.endKm()),
                        Math.max(segment.startKm(), segment.endKm())
                ))
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
        row.setDirectionName(directionName(direction));
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
                                                      Integer direction,
                                                      List<SegmentWind> segments,
                                                      String outputDataSource) {
        List<WindSpeedLimitHourly> result = new ArrayList<>();
        for (RoadDef def : ROAD_DEFS) {
            if (def.direction() != direction) {
                continue;
            }
            double maxWind = segments.stream()
                    .filter(segment -> def.contains(segment.startKm()))
                    .mapToDouble(SegmentWind::windSpeed)
                    .max()
                    .orElse(0D);

            LevelLimit levelLimit = resolveLevelLimit(maxWind);

            LocalDateTime now = LocalDateTime.now();
            WindSpeedLimitHourly row = new WindSpeedLimitHourly();
            row.setTimeStamp(timestamp);
            row.setDirection(direction);
            row.setDirectionName(directionName(direction));
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

    private Integer toDirectionCode(Object rawDirection) {
        if (rawDirection == null) {
            return null;
        }
        if (rawDirection instanceof Number number) {
            int code = number.intValue();
            if (code == DIRECTION_TURPAN || code == DIRECTION_HAMI) {
                return code;
            }
            return null;
        }
        String text = String.valueOf(rawDirection).trim();
        if (text.isEmpty()) {
            return null;
        }
        String s = text.toLowerCase(Locale.ROOT);
        if ("1".equals(s)
                || "\u5410\u9c81\u756a".equals(s)
                || "\u4e0a\u884c".equals(s)
                || "turpan".equals(s)
                || "tulufan".equals(s)
                || "toez".equals(s)
                || "to_ez".equals(s)
                || "hami_to_turpan".equals(s)
                || "hamimi_to_tuyugou".equals(s)
                || "to_turpan".equals(s)) {
            return DIRECTION_TURPAN;
        }
        if ("2".equals(s)
                || "\u54c8\u5bc6".equals(s)
                || "\u4e0b\u884c".equals(s)
                || "hami".equals(s)
                || "towh".equals(s)
                || "to_wh".equals(s)
                || "turpan_to_hami".equals(s)
                || "tuyugou_to_hamimi".equals(s)
                || "to_hami".equals(s)) {
            return DIRECTION_HAMI;
        }
        return null;
    }

    private Integer normalizeDirection(Integer direction) {
        if (direction == null) {
            return null;
        }
        if (direction == DIRECTION_TURPAN || direction == DIRECTION_HAMI) {
            return direction;
        }
        throw new IllegalArgumentException("direction must be 1(turpan) or 2(hami)");
    }

    private String directionName(Integer direction) {
        return direction != null && direction == DIRECTION_TURPAN
                ? "\u5410\u9c81\u756a"
                : "\u54c8\u5bc6";
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

    private record RoadDef(int order, int direction, double startKm, double endKm, String sectionName) {
        private boolean contains(double stakeKm) {
            if (startKm < endKm) {
                return stakeKm >= startKm && stakeKm < endKm;
            }
            return stakeKm <= startKm && stakeKm > endKm;
        }
    }

    private record SegmentWind(double startKm, double endKm, double windSpeed) {
    }

    private record Range(double start, double end) {
    }

    private record LevelLimit(int level, int carSpeed, int truckSpeed) {
    }
}
