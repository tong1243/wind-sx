package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.WindDetectionEventMapper;
import com.wut.screendbmysqlsx.Model.WindDetectionEvent;
import com.wut.screendbmysqlsx.Service.WindDetectionEventService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Service
/**
 * 4.1 事件检测持久化服务实现。
 */
public class WindDetectionEventServiceImpl extends ServiceImpl<WindDetectionEventMapper, WindDetectionEvent>
        implements WindDetectionEventService {
    private static final String EVENT_TABLE_BASE = "wind_detection_event";
    private static final String EVENT_TABLE_PREFIX = EVENT_TABLE_BASE + "_";
    private static final DateTimeFormatter TABLE_SUFFIX_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final WindDetectionEventMapper windDetectionEventMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Set<String> createdTableSuffixCache = ConcurrentHashMap.newKeySet();

    public WindDetectionEventServiceImpl(WindDetectionEventMapper windDetectionEventMapper, JdbcTemplate jdbcTemplate) {
        this.windDetectionEventMapper = windDetectionEventMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsertByFingerprint(WindDetectionEvent event) {
        if (event == null) {
            return;
        }
        normalizeEvent(event);
        String suffix = toTableSuffix(event.getEventTimestamp());
        ensureDailyTable(suffix);
        withTableSuffix(suffix, () -> {
            LambdaQueryWrapper<WindDetectionEvent> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(WindDetectionEvent::getEventFingerprint, event.getEventFingerprint());
            WindDetectionEvent existing = windDetectionEventMapper.selectOne(wrapper);
            if (existing == null) {
                windDetectionEventMapper.insert(event);
                return null;
            }
            event.setId(existing.getId());
            windDetectionEventMapper.updateById(event);
            return null;
        });
    }

    @Override
    public List<WindDetectionEvent> getAllOrdered() {
        String suffix = toTableSuffix(System.currentTimeMillis());
        if (!dailyTableExists(suffix)) {
            return Collections.emptyList();
        }
        return withTableSuffix(suffix, () -> {
            LambdaQueryWrapper<WindDetectionEvent> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(WindDetectionEvent::getUpdatedAt);
            return windDetectionEventMapper.selectList(wrapper);
        });
    }

    private void normalizeEvent(WindDetectionEvent event) {
        long now = System.currentTimeMillis();
        long eventTimestamp = event.getEventTimestamp() == null ? 0L : event.getEventTimestamp();
        if (eventTimestamp <= 0L) {
            eventTimestamp = now;
        }
        event.setEventTimestamp(eventTimestamp);
        long updatedAt = event.getUpdatedAt() == null ? 0L : event.getUpdatedAt();
        if (updatedAt <= 0L) {
            event.setUpdatedAt(now);
        }
    }

    private String toTableSuffix(long epochMillis) {
        LocalDate date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
        return date.format(TABLE_SUFFIX_FMT);
    }

    private void ensureDailyTable(String suffix) {
        if (createdTableSuffixCache.contains(suffix)) {
            return;
        }
        String tableName = EVENT_TABLE_PREFIX + suffix;
        if (!tableName.matches("^wind_detection_event_\\d{8}$")) {
            throw new IllegalArgumentException("invalid event table name: " + tableName);
        }
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `" + tableName + "` LIKE `" + EVENT_TABLE_BASE + "`");
        createdTableSuffixCache.add(suffix);
    }

    private boolean dailyTableExists(String suffix) {
        if (createdTableSuffixCache.contains(suffix)) {
            return true;
        }
        String tableName = EVENT_TABLE_PREFIX + suffix;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        if (count != null && count > 0) {
            createdTableSuffixCache.add(suffix);
            return true;
        }
        return false;
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
