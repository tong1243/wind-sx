package com.wut.screenwebsx.Service;

import com.wut.screendbmysqlsx.Model.WindData;
import com.wut.screendbmysqlsx.Service.WindDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private static final long WINDOW_2H_MS = 2 * 3600_000L;

    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final Pattern stakePattern = Pattern.compile("K(\\d+(?:\\+\\d+)?)", Pattern.CASE_INSENSITIVE);

    private final WindControlStateService stateService;
    private final WindDataService windDataService;

    /**
     * 构造执行发布服务并注入共享状态服务；本服务负责 4.5 模块的方案生命周期管理。
     */
    public WindControlExecutionService(WindControlStateService stateService,
                                       WindDataService windDataService) {
        this.stateService = stateService;
        this.windDataService = windDataService;
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
        Double actualWindSpeed = toNullableDouble(body.get("actualWindSpeedMs"));
        Double forecastMaxWindSpeed = resolveForecastMaxWindSpeed(body);
        int realtimeWind = actualWindSpeed == null
                ? stateService.intValue(body.get("realtimeWindLevel"), 7)
                : stateService.mapWindSpeedToWindLevel(actualWindSpeed);
        int forecastWind = forecastMaxWindSpeed == null
                ? stateService.intValue(body.get("forecastMaxWindLevel"), realtimeWind)
                : stateService.mapWindSpeedToWindLevel(forecastMaxWindSpeed);

        int forecastLevel = stateService.mapWindToControlLevel(forecastWind);
        int actualLevel = stateService.mapWindToControlLevel(realtimeWind);
        int baseLevel = stateService.mapWindToControlLevel(Math.max(realtimeWind, forecastWind));
        int previousLevel = stateService.getCurrentControlLevelBySegment().getOrDefault(segment, stateService.getDefaultControlLevel());
        boolean forecastWindowUpdated = toNullableBoolean(body.get("forecastWindowUpdated")) == null
                || Boolean.TRUE.equals(toNullableBoolean(body.get("forecastWindowUpdated")));
        int level = baseLevel;
        String decisionSource = "FORECAST_MAX";
        if (actualWindSpeed != null && forecastMaxWindSpeed != null && actualWindSpeed > forecastMaxWindSpeed) {
            decisionSource = "REALTIME_SPIKE_UP";
        }
        if (!forecastWindowUpdated && previousLevel < baseLevel && actualLevel >= forecastLevel) {
            level = previousLevel;
            decisionSource = "REALTIME_SPIKE_DOWN_HOLD";
        }

        Map<String, Object> template = stateService.getControlPlanLibrary().get(level);
        if (template == null) {
            throw new IllegalStateException("control plan template missing: level=" + level);
        }

        String planId = UUID.randomUUID().toString().substring(0, 8);
        long endTimestamp = timestamp + durationHours * 3600_000L;
        String startStake = extractStake(segment, true);
        String endStake = extractStake(segment, false);
        String triggerStake = stateService.stringValue(body.get("triggerStake"));
        if (triggerStake.isBlank()) {
            triggerStake = startStake;
        }

        Map<String, Object> interval = resolveIntervalContext(segment, direction, startStake, endStake);
        String intervalName = stateService.stringValue(interval.get("intervalName"));
        String fixedSegmentText = resolveFixedSegmentText(interval, direction);
        String upstreamIntervalName = stateService.stringValue(interval.get("upstreamIntervalName"));
        boolean hasInterchange = Boolean.TRUE.equals(interval.get("hasInterchange"));
        String nearestInterchangeStake = stateService.stringValue(interval.get("nearestInterchangeStake"));
        int upstreamLevel = resolveUpstreamControlLevel(upstreamIntervalName, direction, stateService.getDefaultControlLevel());
        if (upstreamLevel <= 2 && upstreamLevel < level && !hasInterchange) {
            level = upstreamLevel;
            decisionSource = "UPSTREAM_INHERIT";
            template = stateService.getControlPlanLibrary().get(level);
            if (template == null) {
                throw new IllegalStateException("control plan template missing after upstream inherit: level=" + level);
            }
        }

        String expandedStartStake = startStake;
        String expandedEndStake = endStake;
        if (!nearestInterchangeStake.isBlank() && !triggerStake.isBlank()) {
            Double triggerValue = stateService.parseStakeValue(triggerStake);
            Double nodeValue = stateService.parseStakeValue(nearestInterchangeStake);
            if (triggerValue != null && nodeValue != null) {
                if (triggerValue <= nodeValue) {
                    expandedStartStake = triggerStake;
                    expandedEndStake = nearestInterchangeStake;
                } else {
                    expandedStartStake = nearestInterchangeStake;
                    expandedEndStake = triggerStake;
                }
            }
        }
        boolean scopeExpanded = !expandedStartStake.equals(startStake) || !expandedEndStake.equals(endStake);

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planId", planId);
        plan.put("timestamp", timestamp);
        plan.put("publishTime", dtf.format(Instant.ofEpochMilli(timestamp)));
        plan.put("endTimestamp", endTimestamp);
        plan.put("publishEndTime", dtf.format(Instant.ofEpochMilli(endTimestamp)));
        plan.put("durationHours", durationHours);
        plan.put("segment", segment);
        plan.put("segmentText", fixedSegmentText);
        plan.put("startStake", startStake);
        plan.put("endStake", endStake);
        plan.put("triggerStake", triggerStake);
        plan.put("expandedStartStake", expandedStartStake);
        plan.put("expandedEndStake", expandedEndStake);
        plan.put("scopeExpanded", scopeExpanded);
        plan.put("direction", direction);
        plan.put("directionText", directionToText(direction));
        plan.put("actualWindSpeedMs", actualWindSpeed);
        plan.put("forecastMaxWindSpeed2hMs", forecastMaxWindSpeed);
        plan.put("realtimeWindLevel", realtimeWind);
        plan.put("forecastMaxWindLevel", forecastWind);
        plan.put("decisionSource", decisionSource);
        plan.put("recommendedControlLevel", level);
        plan.put("recommendedControlLevelText", levelToText(level));
        plan.put("currentControlLevel", previousLevel);
        plan.put("currentControlLevelText", levelToText(previousLevel));
        plan.put("template", new LinkedHashMap<>(template));
        plan.put("managementPlan", "LEVEL-" + level);
        plan.put("intervalName", intervalName);
        plan.put("upstreamIntervalName", upstreamIntervalName);
        plan.put("hasInterchange", hasInterchange);
        plan.put("nearestUpstreamInterchangeStake", nearestInterchangeStake);
        plan.put("upstreamControlLevel", upstreamLevel);
        plan.put("upstreamControlLevelText", levelToText(upstreamLevel));
        plan.put("controlLevel", level);
        plan.put("controlLevelText", levelToText(level));
        plan.put("controlEventText", resolveControlEventText(template, level));
        Map<String, String> vmsTexts = resolvePlanVmsTexts(template, level, direction, nearestInterchangeStake);
        plan.put("vmsContent", vmsTexts.get("vmsContent"));
        plan.put("vmsInsideSegment", vmsTexts.get("vmsInsideSegment"));
        plan.put("vmsUpstreamExit", vmsTexts.get("vmsUpstreamExit"));
        plan.put("vmsUpstreamTollgate", vmsTexts.get("vmsUpstreamTollgate"));
        plan.put("vmsUpstreamServiceArea", vmsTexts.get("vmsUpstreamServiceArea"));
        plan.put("vmsPublishItems", buildVmsPublishItems(level, direction, nearestInterchangeStake, vmsTexts.get("vmsUpstreamExit")));
        plan.put("dispatch", new LinkedHashMap<>(interval));
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
        Double actualWindSpeed = body != null && body.containsKey("actualWindSpeedMs")
                ? toNullableDouble(body.get("actualWindSpeedMs"))
                : toNullableDouble(plan.get("actualWindSpeedMs"));
        Double forecastMaxWindSpeed = body != null
                ? resolveForecastMaxWindSpeed(body)
                : toNullableDouble(plan.get("forecastMaxWindSpeed2hMs"));
        if (actualWindSpeed != null) {
            realtimeWind = stateService.mapWindSpeedToWindLevel(actualWindSpeed);
        }
        if (forecastMaxWindSpeed != null) {
            forecastWind = stateService.mapWindSpeedToWindLevel(forecastMaxWindSpeed);
        }

        int computedLevel = stateService.mapWindToControlLevel(Math.max(realtimeWind, forecastWind));
        int recommendedLevel = body != null && body.containsKey("recommendedControlLevel")
                ? stateService.intValue(body.get("recommendedControlLevel"), computedLevel)
                : computedLevel;

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
        plan.put("directionText", directionToText(direction));
        plan.put("actualWindSpeedMs", actualWindSpeed);
        plan.put("forecastMaxWindSpeed2hMs", forecastMaxWindSpeed);
        plan.put("durationHours", durationHours);
        plan.put("endTimestamp", endTimestamp);
        plan.put("publishEndTime", dtf.format(Instant.ofEpochMilli(endTimestamp)));
        plan.put("realtimeWindLevel", realtimeWind);
        plan.put("forecastMaxWindLevel", forecastWind);
        plan.put("recommendedControlLevel", recommendedLevel);
        plan.put("recommendedControlLevelText", levelToText(recommendedLevel));
        plan.put("controlLevel", recommendedLevel);
        plan.put("controlLevelText", levelToText(recommendedLevel));
        plan.put("template", new LinkedHashMap<>(template));
        plan.put("managementPlan", body != null && body.containsKey("managementPlan")
                ? stateService.stringValue(body.get("managementPlan"))
                : "LEVEL-" + recommendedLevel);
        plan.put("controlEventText", resolveControlEventText(template, recommendedLevel));

        Map<String, Object> interval = resolveIntervalContext(
                segment,
                direction,
                stateService.stringValue(plan.get("startStake")),
                stateService.stringValue(plan.get("endStake"))
        );
        String fixedSegmentText = resolveFixedSegmentText(interval, direction);
        plan.put("segmentText", fixedSegmentText);
        String nearestInterchangeStake = stateService.stringValue(interval.get("nearestInterchangeStake"));
        Map<String, String> defaultVms = resolvePlanVmsTexts(template, recommendedLevel, direction, nearestInterchangeStake);

        String vmsInsideSegment = body != null && body.containsKey("vmsInsideSegment")
                ? stateService.stringValue(body.get("vmsInsideSegment"))
                : stateService.stringValue(plan.getOrDefault("vmsInsideSegment", defaultVms.get("vmsInsideSegment")));
        if (vmsInsideSegment.isBlank()) {
            vmsInsideSegment = defaultVms.get("vmsInsideSegment");
        }
        String vmsUpstreamExit = body != null && body.containsKey("vmsUpstreamExit")
                ? stateService.stringValue(body.get("vmsUpstreamExit"))
                : stateService.stringValue(plan.getOrDefault("vmsUpstreamExit", defaultVms.get("vmsUpstreamExit")));
        if (vmsUpstreamExit.isBlank()) {
            vmsUpstreamExit = defaultVms.get("vmsUpstreamExit");
        }
        String vmsUpstreamTollgate = body != null && body.containsKey("vmsUpstreamTollgate")
                ? stateService.stringValue(body.get("vmsUpstreamTollgate"))
                : stateService.stringValue(plan.getOrDefault("vmsUpstreamTollgate", defaultVms.get("vmsUpstreamTollgate")));
        if (vmsUpstreamTollgate.isBlank()) {
            vmsUpstreamTollgate = defaultVms.get("vmsUpstreamTollgate");
        }
        String vmsUpstreamServiceArea = body != null && body.containsKey("vmsUpstreamServiceArea")
                ? stateService.stringValue(body.get("vmsUpstreamServiceArea"))
                : stateService.stringValue(plan.getOrDefault("vmsUpstreamServiceArea", defaultVms.get("vmsUpstreamServiceArea")));
        if (vmsUpstreamServiceArea.isBlank()) {
            vmsUpstreamServiceArea = defaultVms.get("vmsUpstreamServiceArea");
        }
        String vmsContent = body != null && body.containsKey("vmsContent")
                ? stateService.stringValue(body.get("vmsContent"))
                : buildVmsContent(vmsInsideSegment, vmsUpstreamExit, vmsUpstreamTollgate, vmsUpstreamServiceArea);
        if (vmsContent.isBlank()) {
            vmsContent = buildVmsContent(vmsInsideSegment, vmsUpstreamExit, vmsUpstreamTollgate, vmsUpstreamServiceArea);
        }
        plan.put("vmsContent", vmsContent);
        plan.put("vmsInsideSegment", vmsInsideSegment);
        plan.put("vmsUpstreamExit", vmsUpstreamExit);
        plan.put("vmsUpstreamTollgate", vmsUpstreamTollgate);
        plan.put("vmsUpstreamServiceArea", vmsUpstreamServiceArea);
        plan.put("vmsPublishItems", buildVmsPublishItems(recommendedLevel, direction, nearestInterchangeStake, vmsUpstreamExit));

        plan.put("intervalName", stateService.stringValue(interval.get("intervalName")));
        plan.put("upstreamIntervalName", stateService.stringValue(interval.get("upstreamIntervalName")));
        plan.put("hasInterchange", Boolean.TRUE.equals(interval.get("hasInterchange")));
        plan.put("nearestUpstreamInterchangeStake", nearestInterchangeStake);
        plan.put("upstreamControlLevelText", levelToText(stateService.intValue(plan.get("upstreamControlLevel"), stateService.getDefaultControlLevel())));
        plan.put("dispatch", new LinkedHashMap<>(interval));

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
        LocalDateTime now = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        List<WindData> latestRows = windDataService.listLatestSnapshot(now);
        List<WindData> future2hRows = windDataService.listByTimeRange(
                now,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp + WINDOW_2H_MS), ZoneId.systemDefault())
        );
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Map<String, Object> row : stateService.getFullLineWindSections()) {
            String segment = stateService.stringValue(row.get("segmentName"));
            int recommendedWindLevel = resolveRecommendedWindLevel(row, latestRows, future2hRows);
            int recommended = stateService.mapWindToControlLevel(recommendedWindLevel);
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
     * 方向编码转展示文案。
     */
    private String directionToText(int direction) {
        return direction == DIRECTION_TURPAN ? "吐鲁番" : "哈密";
    }

    /**
     * 管控等级数字转展示文案。
     */
    private String levelToText(int level) {
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
     * segmentText 严格使用固定区间文案（双向共六个），来源 control_interval_static.interval_name。
     */
    private String resolveFixedSegmentText(Map<String, Object> interval, int direction) {
        String intervalName = stateService.stringValue(interval.get("intervalName"));
        if (!intervalName.isBlank()) {
            return intervalName;
        }
        String segment = stateService.stringValue(interval.get("segment"));
        if (!segment.isBlank()) {
            return segment;
        }
        String directionText = direction == DIRECTION_TURPAN ? "吐鲁番方向" : "哈密方向";
        throw new IllegalArgumentException("segmentText must map to one of the fixed 6 intervals in control_interval_static: " + directionText);
    }

    /**
     * 推导管控事件文案（预约/限速/限行/封路）。
     */
    private String resolveControlEventText(Map<String, Object> template, int level) {
        String riskSectionPlan = stateService.stringValue(template.get("riskSectionPlan"));
        String upstreamEntryPlan = stateService.stringValue(template.get("upstreamEntryPlan"));
        String allText = (riskSectionPlan + " " + upstreamEntryPlan).toLowerCase(Locale.ROOT);
        if (allText.contains("预约")) {
            return "预约";
        }
        if (allText.contains("封路") || level <= 1) {
            return "封路";
        }
        if (allText.contains("禁止") || level == 2) {
            return "限行";
        }
        return "限速";
    }

    /**
     * 解析可空布尔值。
     */
    private Boolean toNullableBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
            return false;
        }
        return null;
    }

    /**
     * 解析可空 double 值。
     */
    private Double toNullableDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 解析预测2小时最大风速（优先显式字段，其次取序列最大值）。
     */
    private Double resolveForecastMaxWindSpeed(Map<String, Object> body) {
        Double maxSpeed = toNullableDouble(body.get("forecastMaxWindSpeed2hMs"));
        Object seriesObj = body.get("forecastWindSpeedSeriesMs");
        if (seriesObj instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                Double value = toNullableDouble(item);
                if (value == null) {
                    continue;
                }
                if (maxSpeed == null || value > maxSpeed) {
                    maxSpeed = value;
                }
            }
        }
        return maxSpeed;
    }

    /**
     * 按方向+桩号范围解析当前区间上下文。
     */
    private Map<String, Object> resolveIntervalContext(String segment, int direction, String startStake, String endStake) {
        Map<String, Object> byName = stateService.getDispatchPlanLibrary().get(segment);
        if (byName != null && direction == stateService.intValue(byName.get("direction"), direction)) {
            return new LinkedHashMap<>(byName);
        }

        Double startValue = parseStakeValue(startStake);
        Double endValue = parseStakeValue(endStake);
        if (startValue == null || endValue == null) {
            return new LinkedHashMap<>(stateService.getDispatchPlanLibrary().getOrDefault(segment, Collections.emptyMap()));
        }
        double minValue = Math.min(startValue, endValue);
        double maxValue = Math.max(startValue, endValue);

        for (Map<String, Object> row : stateService.getDispatchPlanLibrary().values()) {
            int rowDirection = stateService.intValue(row.get("direction"), -1);
            if (rowDirection != direction) {
                continue;
            }
            Double rowStart = toNullableDouble(row.get("startStakeValue"));
            Double rowEnd = toNullableDouble(row.get("endStakeValue"));
            if (rowStart == null || rowEnd == null) {
                rowStart = parseStakeValue(stateService.stringValue(row.get("startStake")));
                rowEnd = parseStakeValue(stateService.stringValue(row.get("endStake")));
            }
            if (rowStart == null || rowEnd == null) {
                continue;
            }
            double rowMin = Math.min(rowStart, rowEnd);
            double rowMax = Math.max(rowStart, rowEnd);
            if (rowMax < minValue || rowMin > maxValue) {
                continue;
            }
            return new LinkedHashMap<>(row);
        }
        return new LinkedHashMap<>(stateService.getDispatchPlanLibrary().getOrDefault(segment, Collections.emptyMap()));
    }

    /**
     * 解析上游区间当前生效管控等级（默认兜底常规等级）。
     */
    private int resolveUpstreamControlLevel(String upstreamIntervalName, int direction, int defaultLevel) {
        if (upstreamIntervalName == null || upstreamIntervalName.isBlank()) {
            return defaultLevel;
        }
        int level = defaultLevel;
        long latestTimestamp = Long.MIN_VALUE;
        for (Map<String, Object> row : stateService.getGeneratedPlans().values()) {
            if (!"PUBLISHED".equalsIgnoreCase(stateService.stringValue(row.get("status")))) {
                continue;
            }
            if (!upstreamIntervalName.equalsIgnoreCase(stateService.stringValue(row.get("intervalName")))) {
                continue;
            }
            if (direction != stateService.intValue(row.get("direction"), direction)) {
                continue;
            }
            long ts = longValue(row.get("timestamp"), 0L);
            if (ts >= latestTimestamp) {
                latestTimestamp = ts;
                level = stateService.intValue(row.get("controlLevel"), defaultLevel);
            }
        }
        return level;
    }

    /**
     * 生成设施级 VMS 发布列表；当设施无专属文案时回退到等级模板文案。
     */
    private List<Map<String, Object>> buildVmsPublishItems(int level, int direction, String nearestInterchangeStake, String fallbackMessage) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> facility : stateService.getPublishFacilities()) {
            int facilityDirection = stateService.intValue(facility.get("direction"), -1);
            if (facilityDirection != direction) {
                continue;
            }
            String facilityInterchangeStake = stateService.stringValue(facility.get("interchangeStake"));
            if (!nearestInterchangeStake.isBlank() && !facilityInterchangeStake.isBlank()
                    && !nearestInterchangeStake.equalsIgnoreCase(facilityInterchangeStake)) {
                continue;
            }
            String message = level <= 1
                    ? stateService.stringValue(facility.get("redAlertMessage"))
                    : stateService.stringValue(facility.get("colorAlertMessage"));
            if (message.isBlank()) {
                message = fallbackMessage;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("facilityId", stateService.stringValue(facility.get("facilityId")));
            row.put("pileNo", stateService.stringValue(facility.get("pileNo")));
            row.put("segment", stateService.stringValue(facility.get("segment")));
            row.put("interchangeName", stateService.stringValue(facility.get("interchangeName")));
            row.put("publishMessage", message);
            rows.add(row);
        }
        return rows;
    }

    private Map<String, String> resolvePlanVmsTexts(Map<String, Object> template,
                                                     int level,
                                                     int direction,
                                                     String nearestInterchangeStake) {
        String insideFallback = stateService.stringValue(template.get("riskSectionPlan"));
        String upstreamExitFallback = stateService.stringValue(template.get("upstreamExitPlan"));
        String upstreamTollgateFallback = stateService.stringValue(template.get("upstreamEntryPlan"));
        String upstreamServiceArea = stateService.stringValue(template.get("upstreamServiceAreaPlan"));

        String vmsInsideSegment = resolveRoleVmsMessage(
                level, direction, nearestInterchangeStake, "区段", insideFallback
        );
        String vmsUpstreamExit = resolveRoleVmsMessage(
                level, direction, nearestInterchangeStake, "出口", upstreamExitFallback
        );
        String vmsUpstreamTollgate = resolveRoleVmsMessage(
                level, direction, nearestInterchangeStake, "入口", upstreamTollgateFallback
        );

        Map<String, String> result = new LinkedHashMap<>();
        result.put("vmsInsideSegment", vmsInsideSegment);
        result.put("vmsUpstreamExit", vmsUpstreamExit);
        result.put("vmsUpstreamTollgate", vmsUpstreamTollgate);
        result.put("vmsUpstreamServiceArea", upstreamServiceArea);
        result.put("vmsContent", buildVmsContent(vmsInsideSegment, vmsUpstreamExit, vmsUpstreamTollgate, upstreamServiceArea));
        return result;
    }

    private String resolveRoleVmsMessage(int level,
                                         int direction,
                                         String nearestInterchangeStake,
                                         String segmentKeyword,
                                         String fallbackMessage) {
        String fallback = fallbackMessage == null ? "" : fallbackMessage;
        for (Map<String, Object> facility : stateService.getPublishFacilities()) {
            int facilityDirection = stateService.intValue(facility.get("direction"), -1);
            if (facilityDirection != direction) {
                continue;
            }
            String segment = stateService.stringValue(facility.get("segment"));
            if (!segmentKeyword.isBlank() && !segment.contains(segmentKeyword)) {
                continue;
            }
            String facilityInterchangeStake = stateService.stringValue(facility.get("interchangeStake"));
            if (!nearestInterchangeStake.isBlank()
                    && !facilityInterchangeStake.isBlank()
                    && !nearestInterchangeStake.equalsIgnoreCase(facilityInterchangeStake)) {
                continue;
            }
            String message = level <= 1
                    ? stateService.stringValue(facility.get("redAlertMessage"))
                    : stateService.stringValue(facility.get("colorAlertMessage"));
            if (!message.isBlank()) {
                return message;
            }
        }
        return fallback;
    }

    private String buildVmsContent(String insideSegment,
                                   String upstreamExit,
                                   String upstreamTollgate,
                                   String upstreamServiceArea) {
        return "区段内：" + (insideSegment == null ? "" : insideSegment)
                + "；上游出口：" + (upstreamExit == null ? "" : upstreamExit)
                + "；上游入口：" + (upstreamTollgate == null ? "" : upstreamTollgate)
                + "；上游服务区：" + (upstreamServiceArea == null ? "" : upstreamServiceArea);
    }

    private int resolveRecommendedWindLevel(Map<String, Object> section,
                                            List<WindData> latestRows,
                                            List<WindData> future2hRows) {
        int direction = normalizeDirectionValue(stateService.intValue(section.get("direction"), DIRECTION_HAMI), DIRECTION_HAMI);
        String startStake = stateService.stringValue(section.get("startStake"));
        String endStake = stateService.stringValue(section.get("endStake"));

        int realtimeMax = resolveMaxWindLevelFromRows(direction, startStake, endStake, latestRows);
        int forecastMax = resolveMaxWindLevelFromRows(direction, startStake, endStake, future2hRows);
        if (realtimeMax <= 0 && forecastMax <= 0) {
            // wind_data 缺失时回退到现有快照字段，避免建议计算中断。
            realtimeMax = stateService.intValue(section.get("realWindLevel"), 0);
            forecastMax = stateService.intValue(section.get("forecastWindLevel"), 0);
        }
        return Math.max(realtimeMax, forecastMax);
    }

    private int resolveMaxWindLevelFromRows(int direction,
                                            String startStake,
                                            String endStake,
                                            List<WindData> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (WindData row : rows) {
            if (!isWindRowMatchSection(row, direction, startStake, endStake)) {
                continue;
            }
            int windLevel = windLevelFromSpeed(row.getWindSpeed());
            if (windLevel > max) {
                max = windLevel;
            }
        }
        return max;
    }

    private int windLevelFromSpeed(BigDecimal windSpeed) {
        if (windSpeed == null) {
            return 0;
        }
        return stateService.mapWindSpeedToWindLevel(windSpeed.doubleValue());
    }

    private boolean isWindRowMatchSection(WindData row,
                                          int direction,
                                          String startStake,
                                          String endStake) {
        if (row == null) {
            return false;
        }
        int rowDirection = normalizeDirectionValue(stateService.intValue(row.getDirection(), -1), -1);
        if (rowDirection != direction) {
            return false;
        }
        String rowStartStake = normalizeStakeToken(row.getStartStake());
        String rowEndStake = normalizeStakeToken(row.getEndStake());
        String sectionStartStake = normalizeStakeToken(startStake);
        String sectionEndStake = normalizeStakeToken(endStake);
        if (rowStartStake.isBlank() || rowEndStake.isBlank() || sectionStartStake.isBlank() || sectionEndStake.isBlank()) {
            return false;
        }
        return (sectionStartStake.equals(rowStartStake) && sectionEndStake.equals(rowEndStake))
                || (sectionStartStake.equals(rowEndStake) && sectionEndStake.equals(rowStartStake));
    }

    private String normalizeStakeToken(String stake) {
        return stake == null ? "" : stake.trim().toUpperCase(Locale.ROOT);
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
     * 校验并归一方向编码，仅允许 1（去往哈密）和 2（去往吐鲁番），非法值抛出异常。
     */
    private int normalizeDirectionValue(int direction, int defaultValue) {
        if (direction == DIRECTION_HAMI || direction == DIRECTION_TURPAN) {
            return direction;
        }
        if (defaultValue == DIRECTION_HAMI || defaultValue == DIRECTION_TURPAN) {
            return defaultValue;
        }
        throw new IllegalArgumentException("direction must be 1(hami) or 2(turpan)");
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

