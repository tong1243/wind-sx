package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.WindDataMapper;
import com.wut.screendbmysqlsx.Model.WindData;
import com.wut.screendbmysqlsx.Service.WindDataService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Slf4j
@Service
public class WindDataServiceImpl extends ServiceImpl<WindDataMapper, WindData> implements WindDataService {
    private static final String WIND_TABLE_BASE = "wind_data";
    private static final String WIND_TABLE_PREFIX = WIND_TABLE_BASE + "_";
    private static final DateTimeFormatter TABLE_SUFFIX_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final WindDataMapper windDataMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Object[] keyLocks = createKeyLocks();
    private final Set<String> createdTableSuffixCache = ConcurrentHashMap.newKeySet();

    @Value("${wind.dynamic-table-retention-days:7}")
    private int retentionDays;

    public WindDataServiceImpl(WindDataMapper windDataMapper, JdbcTemplate jdbcTemplate) {
        this.windDataMapper = windDataMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        cleanExpiredWindTables();
    }

    @Scheduled(cron = "${wind.dynamic-table-clean-cron:0 30 3 * * ?}")
    public void cleanExpiredWindTables() {
        int keepDays = Math.max(retentionDays, 1);
        LocalDate cutoffDate = LocalDate.now().minusDays(keepDays - 1L);

        List<String> allTables = listWindTables();
        int dropCount = 0;
        for (String tableName : allTables) {
            LocalDate tableDate = parseTableDate(tableName);
            if (tableDate == null || !tableDate.isBefore(cutoffDate)) {
                continue;
            }
            jdbcTemplate.execute("DROP TABLE IF EXISTS `" + tableName + "`");
            createdTableSuffixCache.remove(tableName.substring(WIND_TABLE_PREFIX.length()));
            dropCount++;
            log.info("drop expired wind table: {}", tableName);
        }
        log.info("wind dynamic table retention finish, keepDays={}, cutoffDate={}, dropped={}",
                keepDays, cutoffDate, dropCount);
    }

    @Override
    public boolean upsert(WindData row) {
        if (row == null) {
            return false;
        }
        if (row.getTimeStamp() == null) {
            row.setTimeStamp(LocalDateTime.now());
        }
        normalizeRow(row);
        String suffix = toTableSuffix(row.getTimeStamp());
        ensureDailyTable(suffix);
        upsertOrUpdateByNaturalKey(row, suffix);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int upsertBatch(List<WindData> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        Map<String, WindData> deduped = new LinkedHashMap<>();
        List<WindData> passthrough = new java.util.ArrayList<>();
        for (WindData row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getTimeStamp() == null) {
                row.setTimeStamp(LocalDateTime.now());
            }
            normalizeRow(row);
            String key = buildNaturalKey(row);
            if (key == null) {
                passthrough.add(row);
                continue;
            }
            // 同一批次内重复自然键仅保留最后一条，避免批内重复写入。
            deduped.put(key, row);
        }
        int count = 0;
        for (WindData row : passthrough) {
            String suffix = toTableSuffix(row.getTimeStamp());
            ensureDailyTable(suffix);
            count += withTableSuffix(suffix, () -> windDataMapper.upsert(row));
        }
        for (WindData row : deduped.values()) {
            String suffix = toTableSuffix(row.getTimeStamp());
            ensureDailyTable(suffix);
            count += upsertOrUpdateByNaturalKey(row, suffix);
        }
        return count;
    }

    @Override
    public List<WindData> listLatestSnapshot(LocalDateTime timestamp) {
        if (timestamp == null) {
            return Collections.emptyList();
        }
        List<String> candidateSuffix = collectExistingSuffixBeforeOrEqual(timestamp.toLocalDate());
        if (candidateSuffix.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, WindData> latestByNaturalKey = new LinkedHashMap<>();
        for (String suffix : candidateSuffix) {
            List<WindData> rows = withTableSuffix(suffix, () -> windDataMapper.selectLatestSnapshot(timestamp));
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            for (WindData row : rows) {
                // 最新快照只按“方向+路段”归并，不应把时间戳作为去重维度。
                String key = buildSnapshotGroupKey(row);
                if (key == null) {
                    continue;
                }
                WindData existing = latestByNaturalKey.get(key);
                if (existing == null || compareTimestamp(row.getTimeStamp(), existing.getTimeStamp()) > 0) {
                    latestByNaturalKey.put(key, row);
                }
            }
        }

        List<WindData> merged = new ArrayList<>(latestByNaturalKey.values());
        merged.sort((a, b) -> {
            int c1 = compareNullable(a.getDirection(), b.getDirection());
            if (c1 != 0) {
                return c1;
            }
            int c2 = compareNullable(a.getStartStake(), b.getStartStake());
            if (c2 != 0) {
                return c2;
            }
            int c3 = compareNullable(a.getEndStake(), b.getEndStake());
            if (c3 != 0) {
                return c3;
            }
            return compareTimestamp(a.getTimeStamp(), b.getTimeStamp());
        });
        return merged;
    }

    @Override
    public List<WindData> listByTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return Collections.emptyList();
        }
        List<String> suffixes = collectExistingSuffixInRange(start.toLocalDate(), end.toLocalDate());
        if (suffixes.isEmpty()) {
            return Collections.emptyList();
        }

        List<WindData> allRows = new ArrayList<>();
        for (String suffix : suffixes) {
            List<WindData> rows = withTableSuffix(suffix, () -> {
                LambdaQueryWrapper<WindData> wrapper = new LambdaQueryWrapper<>();
                wrapper.ge(WindData::getTimeStamp, start)
                        .le(WindData::getTimeStamp, end)
                        .orderByAsc(WindData::getTimeStamp)
                        .orderByAsc(WindData::getDirection)
                        .orderByAsc(WindData::getStartStake)
                        .orderByAsc(WindData::getEndStake)
                        .orderByAsc(WindData::getId);
                return windDataMapper.selectList(wrapper);
            });
            if (rows != null && !rows.isEmpty()) {
                allRows.addAll(rows);
            }
        }
        return allRows;
    }

    private String buildSnapshotGroupKey(WindData row) {
        if (row == null || row.getDirection() == null) {
            return null;
        }
        String startStake = safeText(row.getStartStake());
        String endStake = safeText(row.getEndStake());
        if (startStake.isEmpty() || endStake.isEmpty()) {
            return null;
        }
        return row.getDirection() + "|" + startStake + "|" + endStake;
    }

    @Override
    public LocalDateTime findFirstTimestamp(String dataSourceFilter) {
        List<String> suffixes = collectAllExistingSuffixes();
        LocalDateTime first = null;
        for (String suffix : suffixes) {
            LocalDateTime one = withTableSuffix(suffix, () -> {
                LambdaQueryWrapper<WindData> wrapper = new LambdaQueryWrapper<>();
                wrapper.select(WindData::getTimeStamp)
                        .isNotNull(WindData::getTimeStamp)
                        .orderByAsc(WindData::getTimeStamp)
                        .last("limit 1");
                applyDataSourceFilter(wrapper, dataSourceFilter);
                WindData row = windDataMapper.selectOne(wrapper);
                return row == null ? null : row.getTimeStamp();
            });
            if (one != null && (first == null || one.isBefore(first))) {
                first = one;
            }
        }
        return first;
    }

    @Override
    public LocalDateTime findLastTimestamp(String dataSourceFilter) {
        List<String> suffixes = collectAllExistingSuffixes();
        LocalDateTime last = null;
        for (String suffix : suffixes) {
            LocalDateTime one = withTableSuffix(suffix, () -> {
                LambdaQueryWrapper<WindData> wrapper = new LambdaQueryWrapper<>();
                wrapper.select(WindData::getTimeStamp)
                        .isNotNull(WindData::getTimeStamp)
                        .orderByDesc(WindData::getTimeStamp)
                        .last("limit 1");
                applyDataSourceFilter(wrapper, dataSourceFilter);
                WindData row = windDataMapper.selectOne(wrapper);
                return row == null ? null : row.getTimeStamp();
            });
            if (one != null && (last == null || one.isAfter(last))) {
                last = one;
            }
        }
        return last;
    }

    private int upsertOrUpdateByNaturalKey(WindData row, String suffix) {
        String key = buildNaturalKey(row);
        if (key == null) {
            return withTableSuffix(suffix, () -> windDataMapper.upsert(row));
        }
        Object lock = keyLocks[Math.floorMod(key.hashCode(), keyLocks.length)];
        synchronized (lock) {
            Long existingId = withTableSuffix(suffix, () -> windDataMapper.selectFirstIdByNaturalKey(
                    row.getTimeStamp(),
                    row.getDirection(),
                    row.getStartStake(),
                    row.getEndStake()
            ));
            if (existingId != null) {
                return withTableSuffix(suffix, () -> windDataMapper.updateDataById(existingId, row));
            }
            return withTableSuffix(suffix, () -> windDataMapper.upsert(row));
        }
    }

    private String buildNaturalKey(WindData row) {
        if (row.getTimeStamp() == null || row.getDirection() == null) {
            return null;
        }
        String startStake = safeText(row.getStartStake());
        String endStake = safeText(row.getEndStake());
        if (startStake.isEmpty() || endStake.isEmpty()) {
            return null;
        }
        return row.getTimeStamp() + "|" + row.getDirection() + "|" + startStake + "|" + endStake;
    }

    private void normalizeRow(WindData row) {
        row.setStartStake(normalizeStake(row.getStartStake()));
        row.setEndStake(normalizeStake(row.getEndStake()));
        row.setSectionName(trimToNull(row.getSectionName()));
        row.setWindDirection(trimToNull(row.getWindDirection()));
        row.setDataSource(trimToNull(row.getDataSource()));
    }

    private String normalizeStake(String raw) {
        String text = safeText(raw);
        return text.isEmpty() ? null : text.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String raw) {
        String text = safeText(raw);
        return text.isEmpty() ? null : text;
    }

    private String safeText(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private Object[] createKeyLocks() {
        Object[] locks = new Object[256];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    private void applyDataSourceFilter(LambdaQueryWrapper<WindData> wrapper, String rawFilter) {
        if (!hasText(rawFilter)) {
            return;
        }
        String filter = rawFilter.trim();
        if (filter.endsWith("*")) {
            String prefix = filter.substring(0, filter.length() - 1);
            if (hasText(prefix)) {
                wrapper.likeRight(WindData::getDataSource, prefix);
            }
            return;
        }
        wrapper.eq(WindData::getDataSource, filter);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String toTableSuffix(LocalDateTime time) {
        return time.toLocalDate().format(TABLE_SUFFIX_FMT);
    }

    private String toDynamicTableName(String suffix) {
        return WIND_TABLE_PREFIX + suffix;
    }

    private void ensureDailyTable(String suffix) {
        if (createdTableSuffixCache.contains(suffix)) {
            return;
        }
        String tableName = toDynamicTableName(suffix);
        if (!tableName.matches("^wind_data_\\d{8}$")) {
            throw new IllegalArgumentException("invalid wind table name: " + tableName);
        }
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `" + tableName + "` LIKE `" + WIND_TABLE_BASE + "`");
        createdTableSuffixCache.add(suffix);
    }

    private List<String> listWindTables() {
        List<String> all = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'wind_data\\_%' ESCAPE '\\\\'",
                String.class
        );
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String table : all) {
            if (table != null && table.matches("^wind_data_\\d{8}$")) {
                result.add(table);
            }
        }
        return result;
    }

    private LocalDate parseTableDate(String tableName) {
        if (tableName == null || !tableName.startsWith(WIND_TABLE_PREFIX)) {
            return null;
        }
        String suffix = tableName.substring(WIND_TABLE_PREFIX.length());
        if (!suffix.matches("\\d{8}")) {
            return null;
        }
        try {
            return LocalDate.parse(suffix, TABLE_SUFFIX_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> collectExistingSuffixInRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            return Collections.emptyList();
        }
        Set<String> exists = new HashSet<>();
        for (String tableName : listWindTables()) {
            exists.add(tableName.substring(WIND_TABLE_PREFIX.length()));
        }
        List<String> suffixes = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            String suffix = d.format(TABLE_SUFFIX_FMT);
            if (exists.contains(suffix)) {
                suffixes.add(suffix);
            }
        }
        return suffixes;
    }

    private List<String> collectExistingSuffixBeforeOrEqual(LocalDate endDate) {
        List<String> suffixes = new ArrayList<>();
        for (String tableName : listWindTables()) {
            LocalDate tableDate = parseTableDate(tableName);
            if (tableDate != null && !tableDate.isAfter(endDate)) {
                suffixes.add(tableName.substring(WIND_TABLE_PREFIX.length()));
            }
        }
        suffixes.sort(String::compareTo);
        return suffixes;
    }

    private List<String> collectAllExistingSuffixes() {
        List<String> suffixes = new ArrayList<>();
        for (String tableName : listWindTables()) {
            LocalDate tableDate = parseTableDate(tableName);
            if (tableDate != null) {
                suffixes.add(tableName.substring(WIND_TABLE_PREFIX.length()));
            }
        }
        suffixes.sort(String::compareTo);
        return suffixes;
    }

    private int compareTimestamp(LocalDateTime a, LocalDateTime b) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    private <T extends Comparable<T>> int compareNullable(T a, T b) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    private <T> T withTableSuffix(String suffix, Supplier<T> supplier) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, suffix);
        try {
            return supplier.get();
        } finally {
            TableTimeContext.clearTime();
        }
    }
}
