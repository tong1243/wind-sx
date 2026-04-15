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
    private static final BigDecimal RISK_THRESHOLD = new BigDecimal("13.9");
    private static final BigDecimal L4_THRESHOLD = new BigDecimal("17.2");
    private static final BigDecimal L3_THRESHOLD = new BigDecimal("24.5");
    private static final BigDecimal L2_THRESHOLD = new BigDecimal("28.5");
    private static final BigDecimal MERGE_DISTANCE_KM = new BigDecimal("3.0");

    private static final String NO_RISK = "无";
    private static final String DEFAULT_OUTPUT_SOURCE = "MATLAB_RULE_V1";
    private static final Pattern STAKE_PATTERN = Pattern.compile("(?i)k(\\d+)(?:\\+(\\d+))?");

    private static final List<RoadDef> ROAD_DEFS = List.of(
            new RoadDef(1, 3178D, 3183D, "k3178-k3183"),
            new RoadDef(2, 3183D, 3188D, "k3183-k3188"),
            new RoadDef(3, 3188D, 3193D, "k3188-k3193"),
            new RoadDef(4, 3193D, 3198D, "k3193-k3198"),
            new RoadDef(5, 3198D, 3204D, "k3198-k3204")
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
            List<SegmentWind> segments = collectSegments(entry.getValue());
            riskRows.add(buildRiskRow(timestamp, segments, finalOutputSource));
            speedRows.addAll(buildSpeedRows(timestamp, segments, finalOutputSource));
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
                .orderByAsc(WindRiskSectionHourly::getId);
        return windRiskSectionHourlyMapper.selectList(wrapper);
    }

    public List<WindSpeedLimitHourly> listSpeedLimits(long startTimestamp,
                                                      long endTimestamp,
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
                .orderByAsc(WindSpeedLimitHourly::getSectionOrder)
                .orderByAsc(WindSpeedLimitHourly::getId);
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

    private List<SegmentWind> collectSegments(List<WindData> rows) {
        Map<String, SegmentWind> temp = new HashMap<>();
        for (WindData row : rows) {
            Double startKm = parseStakeKm(row.getStartStake());
            Double endKm = parseStakeKm(row.getEndStake());
            if (startKm == null || endKm == null) {
                continue;
            }
            double normalizedStart = Math.min(startKm, endKm);
            double normalizedEnd = Math.max(startKm, endKm);
            double windSpeed = row.getWindSpeed() == null ? 0D : row.getWindSpeed().doubleValue();
            String key = String.format(Locale.ROOT, "%.3f|%.3f", normalizedStart, normalizedEnd);
            SegmentWind exists = temp.get(key);
            if (exists == null || windSpeed > exists.windSpeed()) {
                temp.put(key, new SegmentWind(normalizedStart, normalizedEnd, windSpeed));
            }
        }
        List<SegmentWind> result = new ArrayList<>(temp.values());
        result.sort(Comparator.comparingDouble(SegmentWind::startKm));
        return result;
    }

    private WindRiskSectionHourly buildRiskRow(LocalDateTime timestamp,
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
                                                      List<SegmentWind> segments,
                                                      String outputDataSource) {
        List<WindSpeedLimitHourly> result = new ArrayList<>();
        for (RoadDef def : ROAD_DEFS) {
            double maxWind = segments.stream()
                    .filter(segment -> def.contains(segment.startKm()))
                    .mapToDouble(SegmentWind::windSpeed)
                    .max()
                    .orElse(0D);

            LevelLimit levelLimit = resolveLevelLimit(maxWind);

            LocalDateTime now = LocalDateTime.now();
            WindSpeedLimitHourly row = new WindSpeedLimitHourly();
            row.setTimeStamp(timestamp);
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

    private record RoadDef(int order, double startKm, double endKm, String sectionName) {
        private boolean contains(double stakeKm) {
            return stakeKm >= startKm && stakeKm < endKm;
        }
    }

    private record SegmentWind(double startKm, double endKm, double windSpeed) {
    }

    private record Range(double start, double end) {
    }

    private record LevelLimit(int level, int carSpeed, int truckSpeed) {
    }
}
