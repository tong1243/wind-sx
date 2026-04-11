package com.wut.screenwebsx.Service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 4.5 执行与发布业务服务。
 */
@Service
public class WindControlExecutionService {
    private static final int DIRECTION_HAMI = 1;
    private static final int DIRECTION_TURPAN = 2;

    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final Pattern stakePattern = Pattern.compile("K(\\d+(?:\\+\\d+)?)", Pattern.CASE_INSENSITIVE);

    private final WindControlStateService stateService;

    /**
     * 构造执行发布服务并注入共享状态服务；本服务负责 4.5 模块的方案生命周期管理。
     */
    public WindControlExecutionService(WindControlStateService stateService) {
        this.stateService = stateService;
    }

    /**
     * 返回管控执行流程步骤说明，供前端展示标准化处置链路。
     */
    public List<String> getExecutionFlow() {
        return List.of(
                "根据实时风速判断风力等级。",
                "依据预案库将风力等级映射为管控等级。",
                "结合未来时段预测数据生成管控方案草稿。",
                "通过短信、电话、消息等方式发布管控方案。",
                "跟踪执行状态并沉淀大风事件记录。"
        );
    }

    /**
     * 查询已生成方案列表。
     *
     * @param status 可选状态过滤（DRAFT/PUBLISHED/CLOSED）
     * @return 方案列表（按时间倒序）
     */
    public List<Map<String, Object>> listGeneratedPlans(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> plan : stateService.getGeneratedPlans().values()) {
            String s = stateService.stringValue(plan.get("status")).toUpperCase(Locale.ROOT);
            if (!normalized.isBlank() && !normalized.equals(s)) {
                continue;
            }
            rows.add(new LinkedHashMap<>(plan));
        }
        rows.sort((a, b) -> Long.compare(
                longValue(b.get("timestamp"), 0L),
                longValue(a.get("timestamp"), 0L)
        ));
        return rows;
    }

    /**
     * 查询单个方案详情。
     *
     * @param planId 方案ID
     * @return 方案详情
     */
    public Map<String, Object> getGeneratedPlan(String planId) {
        Map<String, Object> plan = stateService.getGeneratedPlans().get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("plan not found: " + planId);
        }
        return new LinkedHashMap<>(plan);
    }

    /**
     * 根据实时/预测风力、路段与方向生成管控方案草案，计算推荐等级并写入方案快照。
     */
    public Map<String, Object> generateControlPlan(long timestamp, Map<String, Object> body) {
        String defaultSegment = stateService.getFullLineWindSections().isEmpty()
                ? "哈密 K3178-K3179（路段）"
                : stateService.stringValue(stateService.getFullLineWindSections().get(0).get("segmentName"));
        String segment = stateService.stringValue(body.getOrDefault("segment", defaultSegment));
        int direction = normalizeDirectionValue(stateService.intValue(body.get("direction"), DIRECTION_HAMI), DIRECTION_HAMI);
        int durationHours = stateService.intValue(body.get("durationHours"), 4);
        if (durationHours <= 0) {
            durationHours = 4;
        }
        int realtimeWind = stateService.intValue(body.get("realtimeWindLevel"), 7);
        int forecastWind = stateService.intValue(body.get("forecastMaxWindLevel"), realtimeWind);
        int maxWind = Math.max(realtimeWind, forecastWind);
        int level = stateService.mapWindToControlLevel(maxWind);

        Map<String, Object> template = stateService.getControlPlanLibrary().get(level);
        if (template == null) {
            throw new IllegalStateException("control plan template missing: level=" + level);
        }

        String planId = UUID.randomUUID().toString().substring(0, 8);
        long endTimestamp = timestamp + durationHours * 3600_000L;
        String startStake = extractStake(segment, true);
        String endStake = extractStake(segment, false);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planId", planId);
        plan.put("timestamp", timestamp);
        plan.put("publishTime", dtf.format(Instant.ofEpochMilli(timestamp)));
        plan.put("endTimestamp", endTimestamp);
        plan.put("publishEndTime", dtf.format(Instant.ofEpochMilli(endTimestamp)));
        plan.put("durationHours", durationHours);
        plan.put("segment", segment);
        plan.put("startStake", startStake);
        plan.put("endStake", endStake);
        plan.put("direction", direction);
        plan.put("realtimeWindLevel", realtimeWind);
        plan.put("forecastMaxWindLevel", forecastWind);
        plan.put("recommendedControlLevel", level);
        plan.put("currentControlLevel", stateService.getCurrentControlLevelBySegment().getOrDefault(segment, stateService.getDefaultControlLevel()));
        plan.put("template", new LinkedHashMap<>(template));
        plan.put("managementPlan", "LEVEL-" + level);
        plan.put("upstreamControlLevel", stateService.getCurrentControlLevelBySegment().getOrDefault(segment, stateService.getDefaultControlLevel()));
        plan.put("controlLevel", level);
        String vms = stateService.getVmsContentLibrary().getOrDefault(level, "");
        plan.put("vmsContent", stateService.getVmsContentLibrary().getOrDefault(level, ""));
        plan.put("vmsInsideSegment", vms);
        plan.put("vmsUpstreamExit", vms);
        plan.put("vmsUpstreamTollgate", vms);
        plan.put("dispatch", new LinkedHashMap<>(stateService.getDispatchPlanLibrary().getOrDefault(segment, Collections.emptyMap())));
        plan.put("status", "DRAFT");

        stateService.getGeneratedPlans().put(planId, plan);
        stateService.getPersistenceService().upsertPlan(plan);
        return plan;
    }

    /**
     * 编辑草稿方案。
     *
     * 仅允许编辑 DRAFT 方案；已发布和已关闭方案禁止编辑。
     *
     * @param planId 方案ID
     * @param body 编辑内容
     * @return 编辑后的方案
     */
    public Map<String, Object> updateDraftPlan(String planId, Map<String, Object> body) {
        Map<String, Object> plan = stateService.getGeneratedPlans().get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("plan not found: " + planId);
        }
        if (!"DRAFT".equalsIgnoreCase(stateService.stringValue(plan.get("status")))) {
            throw new IllegalArgumentException("only draft plan can be updated: " + planId);
        }

        String segment = body != null && body.containsKey("segment")
                ? stateService.stringValue(body.get("segment"))
                : stateService.stringValue(plan.get("segment"));
        int direction = normalizeDirectionValue(
                body != null && body.containsKey("direction")
                        ? stateService.intValue(body.get("direction"), DIRECTION_HAMI)
                        : stateService.intValue(plan.get("direction"), DIRECTION_HAMI),
                DIRECTION_HAMI
        );
        int durationHours = body != null && body.containsKey("durationHours")
                ? stateService.intValue(body.get("durationHours"), stateService.intValue(plan.get("durationHours"), 4))
                : stateService.intValue(plan.get("durationHours"), 4);
        if (durationHours <= 0) {
            durationHours = 4;
        }

        int realtimeWind = body != null && body.containsKey("realtimeWindLevel")
                ? stateService.intValue(body.get("realtimeWindLevel"), stateService.intValue(plan.get("realtimeWindLevel"), 7))
                : stateService.intValue(plan.get("realtimeWindLevel"), 7);
        int forecastWind = body != null && body.containsKey("forecastMaxWindLevel")
                ? stateService.intValue(body.get("forecastMaxWindLevel"), stateService.intValue(plan.get("forecastMaxWindLevel"), realtimeWind))
                : stateService.intValue(plan.get("forecastMaxWindLevel"), realtimeWind);
        int recommendedLevel = body != null && body.containsKey("recommendedControlLevel")
                ? stateService.intValue(body.get("recommendedControlLevel"), stateService.mapWindToControlLevel(Math.max(realtimeWind, forecastWind)))
                : stateService.mapWindToControlLevel(Math.max(realtimeWind, forecastWind));

        Map<String, Object> template = stateService.getControlPlanLibrary().get(recommendedLevel);
        if (template == null) {
            throw new IllegalArgumentException("recommendedControlLevel not found: " + recommendedLevel);
        }

        long timestamp = longValue(plan.get("timestamp"), System.currentTimeMillis());
        long endTimestamp = timestamp + durationHours * 3600_000L;

        plan.put("segment", segment);
        plan.put("startStake", extractStake(segment, true));
        plan.put("endStake", extractStake(segment, false));
        plan.put("direction", direction);
        plan.put("durationHours", durationHours);
        plan.put("endTimestamp", endTimestamp);
        plan.put("publishEndTime", dtf.format(Instant.ofEpochMilli(endTimestamp)));
        plan.put("realtimeWindLevel", realtimeWind);
        plan.put("forecastMaxWindLevel", forecastWind);
        plan.put("recommendedControlLevel", recommendedLevel);
        plan.put("controlLevel", recommendedLevel);
        plan.put("template", new LinkedHashMap<>(template));
        plan.put("managementPlan", body != null && body.containsKey("managementPlan")
                ? stateService.stringValue(body.get("managementPlan"))
                : "LEVEL-" + recommendedLevel);

        String vms = body != null && body.containsKey("vmsContent")
                ? stateService.stringValue(body.get("vmsContent"))
                : stateService.getVmsContentLibrary().getOrDefault(recommendedLevel, "");
        plan.put("vmsContent", vms);
        plan.put("vmsInsideSegment", vms);
        plan.put("vmsUpstreamExit", vms);
        plan.put("vmsUpstreamTollgate", vms);
        plan.put("dispatch", new LinkedHashMap<>(stateService.getDispatchPlanLibrary().getOrDefault(segment, Collections.emptyMap())));

        stateService.getPersistenceService().upsertPlan(plan);
        stateService.persistSnapshot();
        return new LinkedHashMap<>(plan);
    }

    /**
     * 删除草稿方案。
     *
     * 仅允许删除 DRAFT 方案。
     *
     * @param planId 方案ID
     * @return 是否删除成功
     */
    public boolean deleteDraftPlan(String planId) {
        Map<String, Object> plan = stateService.getGeneratedPlans().get(planId);
        if (plan == null) {
            return false;
        }
        if (!"DRAFT".equalsIgnoreCase(stateService.stringValue(plan.get("status")))) {
            throw new IllegalArgumentException("only draft plan can be deleted: " + planId);
        }
        stateService.getGeneratedPlans().remove(planId);
        stateService.getPersistenceService().deletePlan(planId);
        stateService.persistSnapshot();
        return true;
    }

    /**
     * 发布指定草案方案：更新路段生效等级，生成运行中风事件，并同步持久化。
     */
    public Map<String, Object> publishPlan(String planId) {
        Map<String, Object> plan = stateService.getGeneratedPlans().get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("plan not found: " + planId);
        }
        String planStatus = stateService.stringValue(plan.get("status"));
        if ("PUBLISHED".equalsIgnoreCase(planStatus)) {
            throw new IllegalArgumentException("plan already published: " + planId);
        }
        if ("CLOSED".equalsIgnoreCase(planStatus)) {
            throw new IllegalArgumentException("closed plan cannot be published again: " + planId);
        }
        plan.put("status", "PUBLISHED");
        String segment = stateService.stringValue(plan.get("segment"));
        int level = stateService.intValue(plan.get("recommendedControlLevel"), stateService.getDefaultControlLevel());
        stateService.getCurrentControlLevelBySegment().put(segment, level);

        Map<String, Object> record = new LinkedHashMap<>();
        String eventId = "EVT-" + UUID.randomUUID().toString().substring(0, 6);
        record.put("eventId", eventId);
        long publishTs = longValue(plan.get("timestamp"), System.currentTimeMillis());
        long endTs = longValue(plan.get("endTimestamp"), publishTs + 4 * 3600_000L);
        record.put("startTime", dtf.format(Instant.ofEpochMilli(publishTs)));
        record.put("endTime", dtf.format(Instant.ofEpochMilli(endTs)));
        record.put("segment", segment);
        record.put("startStake", stateService.stringValue(plan.get("startStake")));
        record.put("endStake", stateService.stringValue(plan.get("endStake")));
        record.put("direction", normalizeDirectionValue(stateService.intValue(plan.get("direction"), DIRECTION_HAMI), DIRECTION_HAMI));
        record.put("controlPlan", stateService.stringValue(plan.getOrDefault("managementPlan", "LEVEL-" + level)));
        record.put("maxWindLevel", Math.max(stateService.intValue(plan.get("realtimeWindLevel"), 0), stateService.intValue(plan.get("forecastMaxWindLevel"), 0)));
        record.put("controlLevel", level);
        record.put("durationMin", Math.max(0, (endTs - publishTs) / 60_000));
        record.put("status", "RUNNING");

        plan.put("eventId", eventId);
        stateService.getWindEventRecords().add(record);
        stateService.getPersistenceService().upsertPlan(plan);
        stateService.getPersistenceService().upsertEvent(record);
        stateService.persistSnapshot();
        return new LinkedHashMap<>(plan);
    }

    /**
     * 关闭已发布方案：恢复默认管控等级，结束对应事件并回写结束时间与持续时长。
     */
    public Map<String, Object> closePlan(String planId) {
        Map<String, Object> plan = stateService.getGeneratedPlans().get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("plan not found: " + planId);
        }
        String status = stateService.stringValue(plan.get("status"));
        if (!"PUBLISHED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("only published plan can be closed: " + planId);
        }

        long closeTs = System.currentTimeMillis();
        String segment = stateService.stringValue(plan.get("segment"));
        plan.put("status", "CLOSED");
        plan.put("closedTimestamp", closeTs);
        plan.put("closedTime", dtf.format(Instant.ofEpochMilli(closeTs)));

        // 管控解除后恢复默认等级（4级）。
        stateService.getCurrentControlLevelBySegment().put(segment, stateService.getDefaultControlLevel());

        String eventId = stateService.stringValue(plan.get("eventId"));
        Map<String, Object> eventRecord = findEventRecordById(eventId);
        if (eventRecord == null) {
            eventRecord = findLatestRunningRecordBySegment(segment);
        }
        if (eventRecord != null) {
            eventRecord.put("status", "FINISHED");
            eventRecord.put("endTime", dtf.format(Instant.ofEpochMilli(closeTs)));
            eventRecord.put("durationMin", calcDurationByStart(eventRecord.get("startTime"), closeTs));
            stateService.getPersistenceService().upsertEvent(eventRecord);
        }

        stateService.getPersistenceService().upsertPlan(plan);
        stateService.persistSnapshot();
        return new LinkedHashMap<>(plan);
    }

    /**
     * 按当前风况评估每个路段的推荐等级，输出“升级/降级”自动调级建议列表。
     */
    public Map<String, Object> autoUpdate(long timestamp) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Map<String, Object> row : stateService.getFullLineWindSections()) {
            String segment = stateService.stringValue(row.get("segmentName"));
            int recommended = stateService.mapWindToControlLevel(Math.max(
                    stateService.intValue(row.get("realWindLevel"), 0),
                    stateService.intValue(row.get("forecastWindLevel"), 0)
            ));
            int current = stateService.getCurrentControlLevelBySegment().getOrDefault(segment, stateService.getDefaultControlLevel());
            if (recommended != current) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("segment", segment);
                item.put("currentLevel", current);
                item.put("recommendedLevel", recommended);
                item.put("eventType", recommended < current ? "UPGRADE_CONTROL" : "DOWNGRADE_CONTROL");
                item.put("controlStartTime", stateService.findRunningStartTime(segment));
                item.put("controlDurationMin", stateService.estimateDurationMin(segment));
                suggestions.add(item);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("suggestions", suggestions);
        return data;
    }

    /**
     * 按多条件查询风事件记录。
     *
     * 支持路段、桩号区间、方向、方案、时间区间、管控等级和条数限制筛选，
     * 默认按事件开始时间倒序返回，便于前端直接展示“最新事件优先”列表。
     */
    public List<Map<String, Object>> listWindEventRecords(String segment,
                                                          String startStake,
                                                          String endStake,
                                                          Integer direction,
                                                          String controlPlan,
                                                          String startTime,
                                                          String endTime,
                                                          Integer controlLevel,
                                                          Integer limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Double qStartStake = parseStakeValue(startStake);
        Double qEndStake = parseStakeValue(endStake);
        LocalDateTime qStartTime = parseDateTime(startTime);
        LocalDateTime qEndTime = parseDateTime(endTime);
        for (Map<String, Object> record : stateService.getWindEventRecords()) {
            if (segment != null && !segment.isBlank() && !segment.equals(stateService.stringValue(record.get("segment")))) {
                continue;
            }
            if (qStartStake != null || qEndStake != null) {
                Double rStartStake = parseStakeValue(stateService.stringValue(record.get("startStake")));
                Double rEndStake = parseStakeValue(stateService.stringValue(record.get("endStake")));
                if (rStartStake == null || rEndStake == null) {
                    continue;
                }
                if (qStartStake != null && rEndStake < qStartStake) {
                    continue;
                }
                if (qEndStake != null && rStartStake > qEndStake) {
                    continue;
                }
            }
            if (direction != null
                    && stateService.intValue(record.get("direction"), -1) != normalizeDirectionValue(direction, -1)) {
                continue;
            }
            if (controlPlan != null && !controlPlan.isBlank() && !controlPlan.equalsIgnoreCase(stateService.stringValue(record.get("controlPlan")))) {
                continue;
            }
            LocalDateTime recordStart = parseDateTime(stateService.stringValue(record.get("startTime")));
            if (qStartTime != null && (recordStart == null || recordStart.isBefore(qStartTime))) {
                continue;
            }
            if (qEndTime != null && (recordStart == null || recordStart.isAfter(qEndTime))) {
                continue;
            }
            if (controlLevel != null && controlLevel != stateService.intValue(record.get("controlLevel"), -1)) {
                continue;
            }
            rows.add(new LinkedHashMap<>(record));
        }
        rows.sort((a, b) -> {
            LocalDateTime at = parseDateTime(stateService.stringValue(a.get("startTime")));
            LocalDateTime bt = parseDateTime(stateService.stringValue(b.get("startTime")));
            if (at == null && bt == null) {
                return 0;
            }
            if (at == null) {
                return 1;
            }
            if (bt == null) {
                return -1;
            }
            return bt.compareTo(at);
        });

        int finalLimit = normalizeLimit(limit);
        if (rows.size() > finalLimit) {
            return new ArrayList<>(rows.subList(0, finalLimit));
        }
        return rows;
    }

    /**
     * 将事件记录导出为 CSV 文本，字段顺序与接口文档保持一致。
     */
    public String exportWindEventRecordsCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("eventId,startStake,endStake,startTime,endTime,segment,direction,maxWindLevel,controlLevel,controlPlan,durationMin,status").append('\n');
        for (Map<String, Object> r : stateService.getWindEventRecords()) {
            sb.append(stateService.csv(r.get("eventId"))).append(',')
                    .append(stateService.csv(r.get("startStake"))).append(',')
                    .append(stateService.csv(r.get("endStake"))).append(',')
                    .append(stateService.csv(r.get("startTime"))).append(',')
                    .append(stateService.csv(r.get("endTime"))).append(',')
                    .append(stateService.csv(r.get("segment"))).append(',')
                    .append(stateService.csv(r.get("direction"))).append(',')
                    .append(stateService.csv(r.get("maxWindLevel"))).append(',')
                    .append(stateService.csv(r.get("controlLevel"))).append(',')
                    .append(stateService.csv(r.get("controlPlan"))).append(',')
                    .append(stateService.csv(r.get("durationMin"))).append(',')
                    .append(stateService.csv(r.get("status"))).append('\n');
        }
        return sb.toString();
    }

    /**
     * 从路段文本中提取起止桩号；当仅识别到一个桩号时按单值返回。
     */
    private String extractStake(String segment, boolean start) {
        Matcher matcher = stakePattern.matcher(segment == null ? "" : segment);
        List<String> stakes = new ArrayList<>();
        while (matcher.find()) {
            stakes.add("K" + matcher.group(1));
        }
        if (stakes.isEmpty()) {
            return "";
        }
        if (stakes.size() == 1) {
            return stakes.get(0);
        }
        return start ? stakes.get(0) : stakes.get(stakes.size() - 1);
    }

    /**
     * 将桩号文本转换为可比较数值（支持 K3020 与 K3020+300 两种格式）。
     */
    private Double parseStakeValue(String stake) {
        if (stake == null || stake.isBlank()) {
            return null;
        }
        Matcher matcher = stakePattern.matcher(stake.toUpperCase(Locale.ROOT));
        if (!matcher.find()) {
            return null;
        }
        String token = matcher.group(1);
        if (token.contains("+")) {
            String[] parts = token.split("\\+");
            return Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]) / 1000.0;
        }
        return Double.parseDouble(token);
    }

    /**
     * 按系统约定格式解析时间字符串（yyyy-MM-dd HH:mm:ss），解析失败返回 null。
     */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 安全解析 long 类型值；输入非法时回退默认值。
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

    /**
     * 校验并归一方向编码，仅允许 1（下行）和 2（上行），非法值抛出异常。
     */
    private int normalizeDirectionValue(int direction, int defaultValue) {
        if (direction == DIRECTION_HAMI || direction == DIRECTION_TURPAN) {
            return direction;
        }
        if (defaultValue == DIRECTION_HAMI || defaultValue == DIRECTION_TURPAN) {
            return defaultValue;
        }
        throw new IllegalArgumentException("direction must be 1(down) or 2(up)");
    }

    /**
     * 规范化查询条数：默认 20，最小 10，最大 20。
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 20;
        }
        if (limit < 10) {
            return 10;
        }
        return Math.min(limit, 20);
    }

    /**
     * 根据开始时间与结束时间戳计算持续分钟数，异常场景返回 0。
     */
    private int calcDurationByStart(Object startTimeObj, long endTs) {
        LocalDateTime startTime = parseDateTime(stateService.stringValue(startTimeObj));
        if (startTime == null) {
            return 0;
        }
        long startTs = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (endTs <= startTs) {
            return 0;
        }
        return (int) ((endTs - startTs) / 60_000L);
    }

    /**
     * 按事件 ID 精确查找事件记录，未命中返回 null。
     */
    private Map<String, Object> findEventRecordById(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        for (Map<String, Object> record : stateService.getWindEventRecords()) {
            if (eventId.equals(stateService.stringValue(record.get("eventId")))) {
                return record;
            }
        }
        return null;
    }

    /**
     * 按路段倒序查找最近一条 RUNNING 事件，用于关闭方案时兜底关联。
     */
    private Map<String, Object> findLatestRunningRecordBySegment(String segment) {
        for (int i = stateService.getWindEventRecords().size() - 1; i >= 0; i--) {
            Map<String, Object> record = stateService.getWindEventRecords().get(i);
            if (segment.equals(stateService.stringValue(record.get("segment")))
                    && "RUNNING".equalsIgnoreCase(stateService.stringValue(record.get("status")))) {
                return record;
            }
        }
        return null;
    }
}

