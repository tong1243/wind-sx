package com.wut.screenwebsx.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wut.screendbmysqlsx.Model.WindControlKv;
import com.wut.screendbmysqlsx.Model.WindControlPlan;
import com.wut.screendbmysqlsx.Model.WindEventRecord;
import com.wut.screendbmysqlsx.Service.WindControlKvService;
import com.wut.screendbmysqlsx.Service.WindControlPlanService;
import com.wut.screendbmysqlsx.Service.WindEventRecordService;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 风区管控持久化服务。
 *
 * 提供 4.1-4.5 相关快照数据的读写能力，覆盖 KV 分类快照、方案快照和事件快照。
 */
@Service
public class WindControlPersistenceService {
    public static final String CAT_WIND_SECTION = "WIND_SECTION";
    public static final String CAT_SPEED_THRESHOLD = "SPEED_THRESHOLD";
    public static final String CAT_PUBLISH_FACILITY = "PUBLISH_FACILITY";
    public static final String CAT_CLOSURE_DEVICE = "CLOSURE_DEVICE";
    public static final String CAT_STAFF = "STAFF";
    public static final String CAT_TEAM = "TEAM";
    public static final String CAT_CONTROL_PLAN = "CONTROL_PLAN";
    public static final String CAT_VMS_CONTENT = "VMS_CONTENT";
    public static final String CAT_DISPATCH_PLAN = "DISPATCH_PLAN";
    public static final String CAT_CONTROL_STATE = "CONTROL_STATE";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final WindControlKvService windControlKvService;
    private final WindControlPlanService windControlPlanService;
    private final WindEventRecordService windEventRecordService;

    public WindControlPersistenceService(JdbcTemplate jdbcTemplate,
                                         ObjectMapper objectMapper,
                                         WindControlKvService windControlKvService,
                                         WindControlPlanService windControlPlanService,
                                         WindEventRecordService windEventRecordService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.windControlKvService = windControlKvService;
        this.windControlPlanService = windControlPlanService;
        this.windEventRecordService = windEventRecordService;
    }

    /**
     * 服务启动时自动补齐持久化表结构，避免首次运行因缺表导致接口失败。
     */
    @PostConstruct
    public void initTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS wind_control_kv (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  category VARCHAR(64) NOT NULL,
                  item_key VARCHAR(128) NOT NULL,
                  content_json LONGTEXT NOT NULL,
                  updated_at BIGINT NOT NULL,
                  UNIQUE KEY uk_category_key (category, item_key)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS wind_control_plan (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  plan_id VARCHAR(64) NOT NULL,
                  segment VARCHAR(128) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  plan_timestamp BIGINT NOT NULL,
                  payload_json LONGTEXT NOT NULL,
                  updated_at BIGINT NOT NULL,
                  UNIQUE KEY uk_plan_id (plan_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS wind_event_record (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  event_id VARCHAR(64) NOT NULL,
                  start_time VARCHAR(64) NOT NULL,
                  segment VARCHAR(128) NOT NULL,
                  direction VARCHAR(16) NOT NULL,
                  max_wind_level INT NOT NULL,
                  control_level INT NOT NULL,
                  duration_min INT NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  payload_json LONGTEXT NOT NULL,
                  updated_at BIGINT NOT NULL,
                  UNIQUE KEY uk_event_id (event_id)
                )
                """);
    }

    /**
     * 按分类读取 KV 快照并反序列化为 Map 列表。
     */
    public List<Map<String, Object>> listByCategory(String category) {
        List<WindControlKv> rows = windControlKvService.getByCategory(category);
        List<Map<String, Object>> result = new ArrayList<>();
        for (WindControlKv row : rows) {
            result.add(readJsonMap(row.getContentJson()));
        }
        return result;
    }

    /**
     * 按分类与键读取单条 KV 快照；不存在返回 null。
     */
    public Map<String, Object> getByCategoryAndKey(String category, String key) {
        WindControlKv row = windControlKvService.getByCategoryAndKey(category, key);
        if (row == null) {
            return null;
        }
        return readJsonMap(row.getContentJson());
    }

    /**
     * 写入或更新某一分类下的单条快照记录。
     */
    public void upsertCategory(String category, String key, Map<String, Object> payload) {
        if (payload == null) {
            payload = new LinkedHashMap<>();
        }
        windControlKvService.upsert(category, key, writeJson(payload), System.currentTimeMillis());
    }

    /**
     * 删除指定分类下的单条 KV 快照。
     */
    public boolean deleteCategory(String category, String key) {
        return windControlKvService.deleteByCategoryAndKey(category, key);
    }

    /**
     * 按 planId 写入或更新方案快照。
     */
    public void upsertPlan(Map<String, Object> plan) {
        String planId = stringValue(plan.get("planId"));
        String segment = stringValue(plan.get("segment"));
        String status = stringValue(plan.get("status"));
        long timestamp = longValue(plan.get("timestamp"), System.currentTimeMillis());
        windControlPlanService.upsertByPlanId(planId, segment, status, timestamp, writeJson(plan), System.currentTimeMillis());
    }

    /**
     * 按 planId 读取方案快照并反序列化。
     */
    public Map<String, Object> getPlanById(String planId) {
        WindControlPlan plan = windControlPlanService.getByPlanId(planId);
        if (plan == null) {
            return null;
        }
        return readJsonMap(plan.getPayloadJson());
    }

    /**
     * 按 planId 删除方案快照。
     */
    public boolean deletePlan(String planId) {
        if (planId == null || planId.isBlank()) {
            return false;
        }
        return windControlPlanService.removeByPlanId(planId);
    }

    /**
     * 读取最近 N 条方案快照，用于服务重启时恢复内存态。
     */
    public List<Map<String, Object>> listLatestPlanPayloads(int limit) {
        List<WindControlPlan> plans = windControlPlanService.getLatestPlans(limit);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (WindControlPlan p : plans) {
            rows.add(readJsonMap(p.getPayloadJson()));
        }
        return rows;
    }

    /**
     * 按 eventId 写入或更新风事件快照。
     */
    public void upsertEvent(Map<String, Object> event) {
        WindEventRecord row = new WindEventRecord();
        row.setEventId(stringValue(event.get("eventId")));
        row.setStartTime(stringValue(event.get("startTime")));
        row.setSegment(stringValue(event.get("segment")));
        row.setDirection(stringValue(event.getOrDefault("direction", "")));
        row.setMaxWindLevel(intValue(event.get("maxWindLevel"), 0));
        row.setControlLevel(intValue(event.get("controlLevel"), 0));
        row.setDurationMin(intValue(event.get("durationMin"), 0));
        row.setStatus(stringValue(event.getOrDefault("status", "")));
        row.setPayloadJson(writeJson(event));
        row.setUpdatedAt(System.currentTimeMillis());
        windEventRecordService.upsertByEventId(row);
    }

    /**
     * 读取全部风事件记录并按更新时间倒序返回。
     */
    public List<Map<String, Object>> listAllEvents() {
        List<WindEventRecord> rows = windEventRecordService.getAllOrdered();
        List<Map<String, Object>> result = new ArrayList<>();
        for (WindEventRecord row : rows) {
            result.add(readJsonMap(row.getPayloadJson()));
        }
        return result;
    }

    /**
     * 将 Map 安全序列化为 JSON 字符串，失败时抛出明确异常。
     */
    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("serialize json failed", e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为 Map，失败时抛出明确异常。
     */
    private Map<String, Object> readJsonMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("parse json failed", e);
        }
    }

    /**
     * 统一字符串取值，空值转空串。
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 统一整数取值，支持 Number/字符串并提供默认值兜底。
     */
    private int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 统一长整型取值，支持 Number/字符串并提供默认值兜底。
     */
    private long longValue(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
