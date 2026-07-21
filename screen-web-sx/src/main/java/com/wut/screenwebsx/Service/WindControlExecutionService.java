package com.wut.screenwebsx.Service;

import com.wut.screendbmysqlsx.Model.WindData;
import com.wut.screendbmysqlsx.Model.VmsContentTemplateStatic;
import com.wut.screendbmysqlsx.Model.PublishFacilityStatic;
import com.wut.screendbmysqlsx.Service.PublishFacilityStaticService;
import com.wut.screendbmysqlsx.Service.VmsContentTemplateStaticService;
import com.wut.screendbmysqlsx.Service.WindDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
@Slf4j
public class WindControlExecutionService {
    private static final int DIRECTION_HAMI = 1;
    private static final int DIRECTION_TURPAN = 2;
    private static final int DEFAULT_PLAN_WINDOW_HOURS = 2;
    private static final long WINDOW_2H_MS = 2 * 3600_000L;
    private static final String SERVICE_AREA_WELCOME_SUFFIX = "欢迎您";
    private static final String SAME_RISK_PLACEHOLDER = "同风险区段内方案";
    private static final String CAT_AUTO_UPDATE_RECOMMENDATION = "AUTO_UPDATE_RECOMMENDATION";
    private static final String KEY_AUTO_UPDATE_LATEST = "LATEST";
    private static final String EVENT_REPORT_EXPORT_DIR = "事件报告CSV导出";
    private static final String GREEN_ALERT_FIXED_VMS_CONTENT = "连霍高速欢迎您，请遵循指引安全驾驶。\n温馨提示：小型车限速120，大型车限速80。";
    private static final String VMS_KIND_INSIDE_SEGMENT = "INSIDE_SEGMENT";
    private static final String VMS_KIND_UPSTREAM_EXIT = "UPSTREAM_EXIT";
    private static final String VMS_KIND_UPSTREAM_TOLLGATE = "UPSTREAM_TOLLGATE";
    private static final String VMS_KIND_UPSTREAM_SERVICE_AREA = "UPSTREAM_SERVICE_AREA";
    private static final String VMS_LINE_TURPAN = "T";
    private static final String VMS_LINE_HAMI = "H";
    private static final List<ExecutionVmsDevice> EXECUTION_VMS_DEVICES = List.of(
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10013R", "K3282+300", VMS_KIND_UPSTREAM_TOLLGATE, "", ""),
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10012C", "K3281+370", VMS_KIND_UPSTREAM_EXIT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10011", "K3263+200", VMS_KIND_INSIDE_SEGMENT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10010F", "K3243+100", VMS_KIND_UPSTREAM_SERVICE_AREA, "", ""),
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10009", "K3234+000", VMS_KIND_INSIDE_SEGMENT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10008R", "K3198+000", VMS_KIND_UPSTREAM_TOLLGATE, "", ""),
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10007C", "K3196+450", VMS_KIND_UPSTREAM_EXIT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10006F", "K3191+800", VMS_KIND_UPSTREAM_SERVICE_AREA, "", ""),
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10005", "K3180+000", VMS_KIND_INSIDE_SEGMENT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10004F", "K3150+000", VMS_KIND_UPSTREAM_SERVICE_AREA, "", ""),
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10003", "K3134+000", VMS_KIND_INSIDE_SEGMENT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10002R", "K3117+850", VMS_KIND_UPSTREAM_TOLLGATE, "", ""),
            new ExecutionVmsDevice(VMS_LINE_TURPAN, "T10001C", "K3110+500", VMS_KIND_UPSTREAM_EXIT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10013C", "K3283+900", VMS_KIND_UPSTREAM_EXIT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10012R", "K3283+000", VMS_KIND_UPSTREAM_TOLLGATE, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10011", "K3261+200", VMS_KIND_INSIDE_SEGMENT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10010F", "K3245+200", VMS_KIND_UPSTREAM_SERVICE_AREA, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10009", "K3232+000", VMS_KIND_INSIDE_SEGMENT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10008C", "K3199+500", VMS_KIND_UPSTREAM_EXIT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10007R", "K3197+000", VMS_KIND_UPSTREAM_TOLLGATE, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10006F", "K3194+515", VMS_KIND_UPSTREAM_SERVICE_AREA, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10005", "K3180+000", VMS_KIND_INSIDE_SEGMENT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10004F", "K3152+900", VMS_KIND_UPSTREAM_SERVICE_AREA, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10003", "K3132+000", VMS_KIND_INSIDE_SEGMENT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10002C", "K3119+300", VMS_KIND_UPSTREAM_EXIT, "", ""),
            new ExecutionVmsDevice(VMS_LINE_HAMI, "H10001R", "K3117+800", VMS_KIND_UPSTREAM_TOLLGATE, "", "")
    );

    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final DateTimeFormatter exportFileDtf = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final Pattern stakePattern = Pattern.compile("K(\\d+(?:\\+\\d+)?)", Pattern.CASE_INSENSITIVE);
    private final long serviceStartTimestamp = System.currentTimeMillis();

    private final WindControlStateService stateService;
    private final WindDataService windDataService;
    private final WindControlWindImpactService windImpactService;
    private final PublishFacilityStaticService publishFacilityStaticService;
    private final VmsContentTemplateStaticService vmsContentTemplateStaticService;
    private final WindControlResourceService resourceService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造执行发布服务并注入共享状态服务；本服务负责 4.5 模块的方案生命周期管理。
     */
    public WindControlExecutionService(WindControlStateService stateService,
                                       WindDataService windDataService,
                                       WindControlWindImpactService windImpactService,
                                       PublishFacilityStaticService publishFacilityStaticService,
                                       VmsContentTemplateStaticService vmsContentTemplateStaticService,
                                       WindControlResourceService resourceService,
                                       JdbcTemplate jdbcTemplate) {
        this.stateService = stateService;
        this.windDataService = windDataService;
        this.windImpactService = windImpactService;
        this.publishFacilityStaticService = publishFacilityStaticService;
        this.vmsContentTemplateStaticService = vmsContentTemplateStaticService;
        this.resourceService = resourceService;
        this.jdbcTemplate = jdbcTemplate;
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
        autoCloseExpiredPublishedPlans();
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
        autoCloseExpiredPublishedPlans();
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
        long currentTs = System.currentTimeMillis();
        String defaultSegment = stateService.getFullLineWindSections().isEmpty()
                ? "哈密 K3178-K3179（路段）"
                : stateService.stringValue(stateService.getFullLineWindSections().get(0).get("segmentName"));
        String segment = stateService.stringValue(body.getOrDefault("segment", defaultSegment));
        int direction = normalizeDirectionValue(stateService.intValue(body.get("direction"), DIRECTION_HAMI), DIRECTION_HAMI);
        int durationHours = DEFAULT_PLAN_WINDOW_HOURS;
        Double actualWindSpeed = toNullableDouble(body.get("actualWindSpeedMs"));
        Double forecastMaxWindSpeed = resolveForecastMaxWindSpeed(body);
        int realtimeWind = actualWindSpeed == null
                ? stateService.intValue(body.get("realtimeWindLevel"), 7)
                : stateService.mapWindSpeedToWindLevel(actualWindSpeed);
        int forecastWind = forecastMaxWindSpeed == null
                ? stateService.intValue(body.get("forecastMaxWindLevel"), realtimeWind)
                : stateService.mapWindSpeedToWindLevel(forecastMaxWindSpeed);

        int forecastLevel = resolveConfiguredControlLevel(forecastWind);
        int actualLevel = resolveConfiguredControlLevel(realtimeWind);
        int baseLevel = resolveConfiguredControlLevel(Math.max(realtimeWind, forecastWind));
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
        long endTimestamp = currentTs + WINDOW_2H_MS;
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
        boolean upstreamInterchangeFirst = isUpstreamInterchangeFirst(interval, fixedSegmentText, direction);
        boolean includeUpstreamServiceArea = shouldIncludeUpstreamServiceArea(upstreamInterchangeFirst);
        String nearestInterchangeStake = stateService.stringValue(interval.get("nearestInterchangeStake"));
        int upstreamLevel = resolveUpstreamControlLevel(upstreamIntervalName, direction, stateService.getDefaultControlLevel());
        if (upstreamLevel <= 2 && upstreamLevel < level && !upstreamInterchangeFirst) {
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
        plan.put("timestamp", currentTs);
        plan.put("publishTime", dtf.format(Instant.ofEpochMilli(currentTs)));
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
        plan.put("hasInterchange", upstreamInterchangeFirst);
        plan.put("nearestUpstreamInterchangeStake", nearestInterchangeStake);
        plan.put("upstreamControlLevel", upstreamLevel);
        plan.put("upstreamControlLevelText", levelToText(upstreamLevel));
        plan.put("controlLevel", level);
        plan.put("controlLevelText", levelToText(level));
        plan.put("controlEventText", resolveControlEventText(template, level));
        Map<String, String> vmsTexts = resolvePlanVmsTexts(template, fixedSegmentText, includeUpstreamServiceArea);
        plan.put("vmsContent", vmsTexts.get("vmsContent"));
        plan.put("vmsInsideSegment", vmsTexts.get("vmsInsideSegment"));
        plan.put("vmsUpstreamExit", vmsTexts.get("vmsUpstreamExit"));
        plan.put("vmsUpstreamTollgate", vmsTexts.get("vmsUpstreamTollgate"));
        plan.put("vmsUpstreamServiceArea", vmsTexts.get("vmsUpstreamServiceArea"));
        plan.put("vmsPublishItems", buildVmsPublishItems(
                direction,
                nearestInterchangeStake,
                fixedSegmentText,
                stateService.stringValue(interval.get("intervalName")),
                stateService.stringValue(interval.get("startStake")),
                stateService.stringValue(interval.get("endStake")),
                vmsTexts.get("vmsInsideSegment"),
                vmsTexts.get("vmsUpstreamExit"),
                vmsTexts.get("vmsUpstreamTollgate"),
                vmsTexts.get("vmsUpstreamServiceArea"),
                includeUpstreamServiceArea
        ));
        plan.put("dispatch", resolveExecutionDispatch(interval, fixedSegmentText, direction));
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
        int durationHours = DEFAULT_PLAN_WINDOW_HOURS;

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

        boolean levelInputsChanged = body != null && (body.containsKey("realtimeWindLevel")
                || body.containsKey("forecastMaxWindLevel")
                || body.containsKey("actualWindSpeedMs")
                || body.containsKey("forecastMaxWindSpeed2hMs")
                || body.containsKey("forecastWindSpeedSeriesMs"));
        int computedLevel = resolveConfiguredControlLevel(forecastWind);
        int recommendedLevel = body != null && body.containsKey("recommendedControlLevel")
                ? stateService.intValue(body.get("recommendedControlLevel"), computedLevel)
                : levelInputsChanged
                ? computedLevel
                : stateService.intValue(plan.get("recommendedControlLevel"), computedLevel);
        int currentLevel = body != null && body.containsKey("controlLevel")
                ? stateService.intValue(body.get("controlLevel"), stateService.intValue(plan.get("controlLevel"), stateService.getDefaultControlLevel()))
                : levelInputsChanged
                ? resolveConfiguredControlLevel(realtimeWind)
                : stateService.intValue(plan.get("controlLevel"),
                stateService.intValue(plan.get("currentControlLevel"), stateService.getDefaultControlLevel()));

        Map<String, Object> template = stateService.getControlPlanLibrary().get(recommendedLevel);
        if (template == null) {
            throw new IllegalArgumentException("recommendedControlLevel not found: " + recommendedLevel);
        }

        long timestamp = System.currentTimeMillis();
        long endTimestamp = timestamp + WINDOW_2H_MS;

        plan.put("segment", segment);
        plan.put("startStake", extractStake(segment, true));
        plan.put("endStake", extractStake(segment, false));
        plan.put("direction", direction);
        plan.put("directionText", directionToText(direction));
        plan.put("actualWindSpeedMs", actualWindSpeed);
        plan.put("forecastMaxWindSpeed2hMs", forecastMaxWindSpeed);
        plan.put("durationHours", durationHours);
        plan.put("timestamp", timestamp);
        plan.put("publishTime", dtf.format(Instant.ofEpochMilli(timestamp)));
        plan.put("endTimestamp", endTimestamp);
        plan.put("publishEndTime", dtf.format(Instant.ofEpochMilli(endTimestamp)));
        plan.put("realtimeWindLevel", realtimeWind);
        plan.put("forecastMaxWindLevel", forecastWind);
        plan.put("recommendedControlLevel", recommendedLevel);
        plan.put("recommendedControlLevelText", levelToText(recommendedLevel));
        plan.put("currentControlLevel", currentLevel);
        plan.put("currentControlLevelText", levelToText(currentLevel));
        plan.put("controlLevel", currentLevel);
        plan.put("controlLevelText", levelToText(currentLevel));
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
        boolean upstreamInterchangeFirst = isUpstreamInterchangeFirst(interval, fixedSegmentText, direction);
        boolean includeUpstreamServiceArea = shouldIncludeUpstreamServiceArea(upstreamInterchangeFirst);
        String nearestInterchangeStake = stateService.stringValue(interval.get("nearestInterchangeStake"));
        Map<String, String> defaultVms = resolvePlanVmsTexts(template, fixedSegmentText, includeUpstreamServiceArea);

        String vmsInsideSegment = defaultVms.get("vmsInsideSegment");
        String vmsUpstreamExit = defaultVms.get("vmsUpstreamExit");
        String vmsUpstreamTollgate = defaultVms.get("vmsUpstreamTollgate");
        String vmsUpstreamServiceArea = defaultVms.get("vmsUpstreamServiceArea");
        if (body != null && body.containsKey("vmsInsideSegment")) {
            String text = stateService.stringValue(body.get("vmsInsideSegment"));
            if (!text.isBlank()) {
                vmsInsideSegment = text;
            }
        }
        if (body != null && body.containsKey("vmsUpstreamExit")) {
            String text = stateService.stringValue(body.get("vmsUpstreamExit"));
            if (!text.isBlank()) {
                vmsUpstreamExit = text;
            }
        }
        if (body != null && body.containsKey("vmsUpstreamTollgate")) {
            String text = stateService.stringValue(body.get("vmsUpstreamTollgate"));
            if (!text.isBlank()) {
                vmsUpstreamTollgate = text;
            }
        }
        if (body != null && body.containsKey("vmsUpstreamServiceArea")) {
            String text = stateService.stringValue(body.get("vmsUpstreamServiceArea"));
            if (!text.isBlank()) {
                vmsUpstreamServiceArea = text;
            }
        }
        if (!includeUpstreamServiceArea) {
            vmsUpstreamServiceArea = buildServiceAreaWelcomeText(fixedSegmentText);
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
        plan.put("vmsPublishItems", buildVmsPublishItems(
                direction,
                nearestInterchangeStake,
                fixedSegmentText,
                stateService.stringValue(interval.get("intervalName")),
                stateService.stringValue(interval.get("startStake")),
                stateService.stringValue(interval.get("endStake")),
                vmsInsideSegment,
                vmsUpstreamExit,
                vmsUpstreamTollgate,
                vmsUpstreamServiceArea,
                includeUpstreamServiceArea
        ));

        plan.put("intervalName", stateService.stringValue(interval.get("intervalName")));
        plan.put("upstreamIntervalName", stateService.stringValue(interval.get("upstreamIntervalName")));
        plan.put("hasInterchange", upstreamInterchangeFirst);
        plan.put("nearestUpstreamInterchangeStake", nearestInterchangeStake);
        plan.put("upstreamControlLevelText", levelToText(stateService.intValue(plan.get("upstreamControlLevel"), stateService.getDefaultControlLevel())));
        plan.put("dispatch", resolveExecutionDispatch(interval, fixedSegmentText, direction));

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
     * 发布指定草案方案：仅更新方案状态与路段生效等级。
     */
    public Map<String, Object> publishPlan(String planId) {
        Map<String, Object> plan = stateService.getGeneratedPlans().get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("plan not found: " + planId);
        }
        String planStatus = stateService.stringValue(plan.get("status"));
        log.info("wind control publish start: planId={}, status={}, segment={}, direction={}",
                planId,
                planStatus,
                stateService.stringValue(plan.get("segment")),
                stateService.stringValue(plan.get("direction")));
        if ("PUBLISHED".equalsIgnoreCase(planStatus)) {
            throw new IllegalArgumentException("plan already published: " + planId);
        }
        if ("CLOSED".equalsIgnoreCase(planStatus)) {
            throw new IllegalArgumentException("closed plan cannot be published again: " + planId);
        }
        int level = stateService.intValue(plan.get("recommendedControlLevel"), stateService.getDefaultControlLevel());
        int beforeLevel = stateService.intValue(plan.get("controlLevel"), stateService.getDefaultControlLevel());
        applyExecutedControlLevel(plan, level);

        plan.put("status", "PUBLISHED");
        plan.put("executionApplied", true);
        stateService.getPersistenceService().upsertPlan(plan);
        stateService.persistSnapshot();
        log.info("wind control publish done: planId={}, status={}, beforeControlLevel={}, appliedControlLevel={}, recommendedControlLevel={}, eventId={}, dispatchRecordId={}",
                planId,
                stateService.stringValue(plan.get("status")),
                beforeLevel,
                stateService.intValue(plan.get("controlLevel"), stateService.getDefaultControlLevel()),
                stateService.intValue(plan.get("recommendedControlLevel"), stateService.getDefaultControlLevel()),
                stateService.stringValue(plan.get("eventId")),
                stateService.stringValue(plan.get("dispatchRecordId")));
        return new LinkedHashMap<>(plan);
    }

    /**
     * 为已发布方案单独新增 RUNNING 事件报告。
     */
    public Map<String, Object> createRunningEventReportForPlan(String planId) {
        Map<String, Object> plan = requirePublishedPlan(planId, "event report");
        long publishTs = longValue(plan.get("timestamp"), System.currentTimeMillis());
        Map<String, Object> eventRecord = ensureRunningEventRecord(plan, publishTs);
        stateService.getPersistenceService().upsertPlan(plan);
        stateService.persistSnapshot();
        return new LinkedHashMap<>(eventRecord);
    }

    /**
     * 为已发布方案单独新增中队出警记录。
     */
    public Map<String, Object> createDispatchRecordForPlan(String planId) {
        Map<String, Object> plan = requirePublishedPlan(planId, "dispatch record");
        long publishTs = longValue(plan.get("timestamp"), System.currentTimeMillis());
        Map<String, Object> dispatchRecord = ensureDispatchRecord(plan, publishTs);
        stateService.getPersistenceService().upsertPlan(plan);
        stateService.persistSnapshot();
        return new LinkedHashMap<>(dispatchRecord);
    }

    private Map<String, Object> requirePublishedPlan(String planId, String target) {
        Map<String, Object> plan = stateService.getGeneratedPlans().get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("plan not found: " + planId);
        }
        String status = stateService.stringValue(plan.get("status"));
        if (!"PUBLISHED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("only published plan can create " + target + ": " + planId);
        }
        return plan;
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
        applyExecutedControlLevel(plan, stateService.getDefaultControlLevel());

        String eventId = stateService.stringValue(plan.get("eventId"));
        Map<String, Object> eventRecord = findEventRecordById(eventId);
        if (eventRecord == null) {
            eventRecord = findLatestRunningRecordBySegment(segment);
        }
        if (eventRecord != null) {
            String conclusionTime = plannedEventConclusionTime(eventRecord.get("startTime"), closeTs);
            eventRecord.put("status", "FINISHED");
            eventRecord.put("endTime", conclusionTime);
            eventRecord.put("conclusionTime", conclusionTime);
            eventRecord.put("durationMin", (int) (WINDOW_2H_MS / 60_000L));
            stateService.getPersistenceService().upsertEvent(eventRecord);
        }

        stateService.getPersistenceService().upsertPlan(plan);
        stateService.persistSnapshot();
        return new LinkedHashMap<>(plan);
    }

    private Map<String, Object> createDispatchRecordByPlan(Map<String, Object> plan, long publishTs) {
        if (plan == null) {
            return null;
        }
        Map<String, Object> dispatch = safeMap(plan.get("dispatch"));
        String teamId = firstNonBlank(dispatch.get("teamId"), plan.get("teamId"));
        String teamName = firstNonBlank(dispatch.get("teamName"), dispatch.get("team"), dispatch.get("name"), plan.get("team"));
        if (teamName.isBlank() && !teamId.isBlank()) {
            Map<String, Object> team = findTeamById(teamId, getDutyTeamsForExecution());
            teamName = team == null ? "" : firstNonBlank(team.get("name"), team.get("teamName"), team.get("team"));
        }
        if (!teamName.isBlank()) {
            teamName = teamName.replace("班组", "中队");
        }
        String dispatchReason = normalizeManagementPlanText(
                firstNonBlank(plan.get("controlEventText"), plan.get("managementPlan")),
                stateService.intValue(plan.get("controlLevel"),
                        stateService.intValue(plan.get("recommendedControlLevel"), stateService.getDefaultControlLevel()))
        );
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("teamId", teamId);
        record.put("team", teamName);
        record.put("dispatchReason", dispatchReason);
        record.put("dispatchTime", publishTs);
        record.put("returnTime", publishTs + WINDOW_2H_MS);
        record.put("dispatchStatus", "DISPATCHED");
        record.put("planId", stateService.stringValue(plan.get("planId")));
        record.put("segment", firstNonBlank(plan.get("segmentText"), plan.get("segment")));
        record.put("direction", stateService.intValue(plan.get("direction"), DIRECTION_HAMI));
        Map<String, Object> created = resourceService.createDispatchRecord(record);
        log.info("wind control dispatch record created: planId={}, recordId={}, team={}, reason={}",
                stateService.stringValue(plan.get("planId")),
                stateService.stringValue(created == null ? null : created.get("recordId")),
                teamName,
                dispatchReason);
        return created;
    }

    private Map<String, Object> ensureDispatchRecord(Map<String, Object> plan, long publishTs) {
        if (plan == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> dispatchRecord = createDispatchRecordByPlan(plan, publishTs);
        if (dispatchRecord != null && !dispatchRecord.isEmpty()) {
            plan.put("dispatchRecordId", stateService.stringValue(dispatchRecord.get("recordId")));
            return new LinkedHashMap<>(dispatchRecord);
        }
        return new LinkedHashMap<>();
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
        for (Map<String, Object> interval : collectDashboardIntervals()) {
            int direction = stateService.intValue(interval.get("direction"), DIRECTION_HAMI);
            String segment = resolveDashboardSegmentText(interval, direction);
            String[] stakes = resolveSpatiotemporalStakes(Collections.emptyMap(), interval);
            String startStake = firstNonBlank(stakes[0], interval.get("startStake"));
            String endStake = firstNonBlank(stakes[1], interval.get("endStake"));
            int currentWindLevel = resolveMaxWindLevelFromRows(direction, startStake, endStake, latestRows);
            int recommendedWindLevel = resolveMaxWindLevelFromRows(direction, startStake, endStake, future2hRows);
            Integer cachedCurrentLevel = findCurrentControlLevelForInterval(segment, interval);
            int current = cachedCurrentLevel != null
                    ? cachedCurrentLevel
                    : currentWindLevel > 0
                    ? resolveConfiguredControlLevel(currentWindLevel)
                    : stateService.getDefaultControlLevel();
            int recommended = resolveConfiguredControlLevel(recommendedWindLevel);
            if (recommended != current) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("segment", segment);
                item.put("direction", direction);
                item.put("directionText", directionToText(direction));
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
        persistAutoUpdateSuggestions(data);
        return data;
    }

    /**
     * 4.5.3 自动更新定时任务：周期执行并自动落库。
     */
    @Scheduled(fixedDelayString = "${wind.control.auto-update.fixed-delay-ms:30000}")
    public void autoUpdatePersistJob() {
        long now = System.currentTimeMillis();
        try {
            autoUpdate(now);
        } catch (Exception ignored) {
            // 定时建议计算失败不阻断主流程，下次周期重试。
        }
    }

    private void persistAutoUpdateSuggestions(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>(data);
        payload.put("persistedAt", dtf.format(Instant.ofEpochMilli(System.currentTimeMillis())));
        stateService.getPersistenceService().upsertCategory(
                CAT_AUTO_UPDATE_RECOMMENDATION,
                KEY_AUTO_UPDATE_LATEST,
                payload
        );
    }

    private int resolveCurrentControlLevelForInterval(String segmentText, Map<String, Object> interval) {
        Integer level = findCurrentControlLevelForInterval(segmentText, interval);
        return level == null ? stateService.getDefaultControlLevel() : level;
    }

    private Integer findCurrentControlLevelForInterval(String segmentText, Map<String, Object> interval) {
        if (segmentText != null && !segmentText.isBlank()) {
            Integer level = stateService.getCurrentControlLevelBySegment().get(segmentText);
            if (level != null) {
                return level;
            }
        }
        String intervalName = stateService.stringValue(interval.get("intervalName"));
        if (!intervalName.isBlank()) {
            Integer level = stateService.getCurrentControlLevelBySegment().get(intervalName);
            if (level != null) {
                return level;
            }
        }
        String segment = stateService.stringValue(interval.get("segment"));
        if (!segment.isBlank()) {
            Integer level = stateService.getCurrentControlLevelBySegment().get(segment);
            if (level != null) {
                return level;
            }
        }
        return null;
    }

    private void applyExecutedControlLevel(Map<String, Object> plan, int level) {
        if (plan == null) {
            return;
        }
        plan.put("currentControlLevel", level);
        plan.put("currentControlLevelText", levelToText(level));
        plan.put("controlLevel", level);
        plan.put("controlLevelText", levelToText(level));
        for (Object key : Arrays.asList(
                plan.get("segment"),
                plan.get("segmentText"),
                plan.get("intervalName"),
                plan.get("controlInterval")
        )) {
            String text = stateService.stringValue(key).trim();
            if (!text.isBlank()) {
                stateService.getCurrentControlLevelBySegment().put(text, level);
            }
        }
    }

    private boolean isPublishedPlan(Map<String, Object> plan) {
        return plan != null && "PUBLISHED".equalsIgnoreCase(stateService.stringValue(plan.get("status")));
    }

    private boolean hasExecutedControlLevel(Map<String, Object> plan) {
        return plan != null
                && (isPublishedPlan(plan) || Boolean.TRUE.equals(plan.get("executionApplied")));
    }

    /**
     * 按多条件查询风事件记录。
     *
     * 支持路段、桩号区间、方向、方案、时间区间、管控等级和条数限制筛选，
     * 默认按事件开始时间倒序返回，便于前端直接展示“最新事件优先”列表。
     */
    public List<Map<String, Object>> listWindEventRecords(String segment,
                                                          String incidentLocation,
                                                          String startStake,
                                                          String endStake,
                                                          Integer direction,
                                                          String controlPlan,
                                                          String managementPlan,
                                                          String startTime,
                                                          String endTime,
                                                          Integer controlLevel,
                                                          Integer limit) {
        autoCloseExpiredPublishedPlans();
        List<Map<String, Object>> rows = new ArrayList<>();
        String[] parsedLocation = parseIncidentLocationRange(incidentLocation);
        String effectiveStartStake = firstNonBlank(startStake, parsedLocation[0]);
        String effectiveEndStake = firstNonBlank(endStake, parsedLocation[1]);
        String effectivePlan = firstNonBlank(managementPlan, controlPlan);
        Double qStartStake = parseStakeValue(effectiveStartStake);
        Double qEndStake = parseStakeValue(effectiveEndStake);
        LocalDateTime qStartTime = parseDateTime(startTime);
        LocalDateTime qEndTime = parseDateTime(endTime);
        for (Map<String, Object> record : stateService.getWindEventRecords()) {
            Map<String, Object> row = toWindEventViewRow(record);
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
            if (effectivePlan != null && !effectivePlan.isBlank()
                    && !effectivePlan.equalsIgnoreCase(stateService.stringValue(row.get("managementPlan")))) {
                continue;
            }
            LocalDateTime recordStart = parseDateTime(stateService.stringValue(row.get("timeOfOccurrence")));
            LocalDateTime recordEnd = parseDateTime(stateService.stringValue(row.get("conclusionTime")));
            if (qStartTime != null && (recordStart == null || recordStart.isBefore(qStartTime))) {
                continue;
            }
            if (qEndTime != null) {
                if (recordStart == null || recordStart.isAfter(qEndTime)) {
                    continue;
                }
                if (recordEnd != null && recordEnd.isAfter(qEndTime)) {
                    continue;
                }
            }
            if (qStartTime != null && recordEnd != null && recordEnd.isBefore(qStartTime)) {
                continue;
            }
            if (controlLevel != null && controlLevel != stateService.intValue(record.get("controlLevel"), -1)) {
                continue;
            }
            rows.add(row);
        }
        rows.sort((a, b) -> {
            LocalDateTime at = parseDateTime(stateService.stringValue(a.get("timeOfOccurrence")));
            LocalDateTime bt = parseDateTime(stateService.stringValue(b.get("timeOfOccurrence")));
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
        autoCloseExpiredPublishedPlans();
        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff');
        sb.append("事件地点,风力等级,管控方案,发生时间,结束时间,管控范围,执勤人员").append("\r\n");
        for (Map<String, Object> source : stateService.getWindEventRecords()) {
            Map<String, Object> r = toWindEventViewRow(source);
            sb.append(stateService.csv(r.get("incidentLocation"))).append(',')
                    .append(stateService.csv(r.get("windSpeedScale"))).append(',')
                    .append(stateService.csv(r.get("managementPlan"))).append(',')
                    .append(stateService.csv(r.get("timeOfOccurrence"))).append(',')
                    .append(stateService.csv(r.get("conclusionTime"))).append(',')
                    .append(stateService.csv(r.get("controlPerimeter"))).append(',')
                    .append(stateService.csv(r.get("onDutyPersonnel"))).append("\r\n");
        }
        String csv = sb.toString();
        persistWindEventCsvToDesktop(csv);
        return csv;
    }

    private Map<String, Object> toWindEventViewRow(Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>();
        String incidentLocation = firstNonBlank(source.get("incidentLocation"), buildEventLocation(
                stateService.stringValue(source.get("startStake")),
                stateService.stringValue(source.get("endStake"))
        ));
        String windSpeedScale = firstNonBlank(source.get("windSpeedScale"), formatWindSpeedScale(source.get("maxWindLevel")));
        String managementPlan = normalizeManagementPlanText(
                firstNonBlank(source.get("managementPlan"), source.get("controlPlan"), source.get("controlEventText")),
                stateService.intValue(source.get("controlLevel"), stateService.getDefaultControlLevel())
        );
        String timeOfOccurrence = firstNonBlank(source.get("timeOfOccurrence"), source.get("startTime"));
        String conclusionTime = timeOfOccurrence.isBlank()
                ? firstNonBlank(source.get("conclusionTime"), source.get("endTime"))
                : plannedEventConclusionTime(timeOfOccurrence, System.currentTimeMillis());
        String controlPerimeter = firstNonBlank(source.get("controlPerimeter"), source.get("segmentText"), source.get("segment"));
        String onDutyPersonnel = firstNonBlank(source.get("onDutyPersonnel"), source.get("contactStaff"));

        row.put("incidentLocation", incidentLocation);
        row.put("windSpeedScale", windSpeedScale);
        row.put("managementPlan", managementPlan);
        row.put("timeOfOccurrence", timeOfOccurrence);
        row.put("conclusionTime", conclusionTime);
        row.put("controlPerimeter", controlPerimeter);
        row.put("onDutyPersonnel", onDutyPersonnel);

        // 保留兼容字段，避免旧前端页面断裂。
        row.put("eventId", stateService.stringValue(source.get("eventId")));
        row.put("segment", stateService.stringValue(source.get("segment")));
        row.put("direction", stateService.intValue(source.get("direction"), DIRECTION_HAMI));
        row.put("directionText", directionToText(stateService.intValue(source.get("direction"), DIRECTION_HAMI)));
        row.put("controlLevel", stateService.intValue(source.get("controlLevel"), stateService.getDefaultControlLevel()));
        row.put("incidentLocation", resolveWindEventStakeLocation(source, row));
        return row;
    }

    private String formatWindSpeedScale(Object levelObj) {
        int level = stateService.intValue(levelObj, 0);
        if (level <= 0) {
            return "";
        }
        return level + "级大风";
    }

    private String normalizeManagementPlanText(String text, int controlLevel) {
        String raw = text == null ? "" : text.trim();
        if (raw.isBlank()) {
            return mapControlLevelToManagementPlan(controlLevel);
        }
        if (raw.contains("预约")) {
            return "预约";
        }
        if (raw.contains("封")) {
            return "封路";
        }
        if (raw.contains("限速")) {
            return "限速";
        }
        if (raw.contains("禁") || raw.contains("限行")) {
            return "封路";
        }
        if (raw.startsWith("LEVEL-")) {
            return mapControlLevelToManagementPlan(controlLevel);
        }
        if ("预约".equals(raw) || "封路".equals(raw) || "限速".equals(raw)) {
            return raw;
        }
        return mapControlLevelToManagementPlan(controlLevel);
    }

    private String mapControlLevelToManagementPlan(int controlLevel) {
        if (controlLevel <= 2) {
            return "封路";
        }
        if (controlLevel == 3) {
            return "预约";
        }
        return "限速";
    }

    private void persistWindEventCsvToDesktop(String csv) {
        String homeDir = System.getProperty("user.home");
        if (homeDir == null || homeDir.isBlank()) {
            return;
        }
        try {
            Path exportDir = Path.of(homeDir, "Desktop", EVENT_REPORT_EXPORT_DIR);
            Files.createDirectories(exportDir);
            String fileName = "wind-events-" + exportFileDtf.format(LocalDateTime.now()) + ".csv";
            Path target = exportDir.resolve(fileName);
            Files.writeString(
                    target,
                    csv == null ? "" : csv,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception ex) {
            throw new IllegalStateException("事件报告CSV自动落盘失败", ex);
        }
    }

    /**
     * 大屏图一：管控方案自动生成与调整表格。
     */
    public List<Map<String, Object>> listAutoGenerationTableRows(String status) {
        return listAutoGenerationTableRows(status, null);
    }

    public List<Map<String, Object>> listAutoGenerationTableRows(String status, Long timestamp) {
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> plans = listGeneratedPlans(normalizedStatus);
        Map<String, Map<String, Object>> latestPlanByIntervalAndDirection = new LinkedHashMap<>();
        for (Map<String, Object> plan : plans) {
            String key = intervalDirectionKey(
                    firstNonBlank(plan.get("intervalName"), plan.get("segmentText"), plan.get("segment")),
                    stateService.intValue(plan.get("direction"), DIRECTION_HAMI)
            );
            latestPlanByIntervalAndDirection.putIfAbsent(key, plan);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> intervals = collectDashboardIntervals();
        long baseTimestamp = timestamp == null || timestamp <= 0 ? System.currentTimeMillis() : timestamp;
        LocalDateTime baseTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(baseTimestamp), ZoneId.systemDefault());
        List<WindData> future2hRows = windDataService.listByTimeRange(
                baseTime,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(baseTimestamp + WINDOW_2H_MS), ZoneId.systemDefault())
        );
        List<WindData> latestRows = windDataService.listLatestSnapshot(baseTime);
        Map<String, Map<String, Object>> future2hImpactByStakeAndDirection =
                loadFuture2hImpactByStakeAndDirection(baseTimestamp);

        for (Map<String, Object> interval : intervals) {
            String intervalName = firstNonBlank(interval.get("intervalName"), interval.get("segment"));
            int direction = stateService.intValue(interval.get("direction"), DIRECTION_HAMI);
            String key = intervalDirectionKey(intervalName, direction);
            Map<String, Object> plan = latestPlanByIntervalAndDirection.get(key);
            if (plan == null && (normalizedStatus.isBlank() || "DRAFT".equals(normalizedStatus))) {
                plan = createDefaultDraftPlanForInterval(interval, direction);
                latestPlanByIntervalAndDirection.put(key, plan);
            } else if (plan != null && "DRAFT".equalsIgnoreCase(stateService.stringValue(plan.get("status")))) {
                refreshDraftPlanRecommendation(plan, interval);
            }
            if (plan != null) {
                alignPlanLevelsWithWindRows(plan, interval, latestRows, future2hRows, future2hImpactByStakeAndDirection);
                stateService.getPersistenceService().upsertPlan(plan);
            }
            Map<String, Object> row = plan == null
                    ? buildEmptyAutoGenerationRow(resolveDashboardSegmentText(interval, direction), direction)
                    : buildAutoGenerationRowFromPlan(plan, interval);
            alignAutoGenerationLevelsWithWindRows(row, interval, latestRows, future2hRows, future2hImpactByStakeAndDirection);

            if (!normalizedStatus.isBlank()) {
                String rowStatus = stateService.stringValue(row.get("status")).toUpperCase(Locale.ROOT);
                if (!normalizedStatus.equals(rowStatus) && !"NONE".equals(rowStatus)) {
                    continue;
                }
            }
            rows.add(row);
        }
        stateService.persistSnapshot();
        return rows;
    }

    private List<Map<String, Object>> collectDashboardIntervals() {
        Map<String, List<Map<String, Object>>> byDirection = new LinkedHashMap<>();
        byDirection.put(String.valueOf(DIRECTION_HAMI), new ArrayList<>());
        byDirection.put(String.valueOf(DIRECTION_TURPAN), new ArrayList<>());
        for (Map<String, Object> row : stateService.getDispatchPlanLibrary().values()) {
            int direction = stateService.intValue(row.get("direction"), -1);
            if (direction != DIRECTION_HAMI && direction != DIRECTION_TURPAN) {
                continue;
            }
            if (stateService.stringValue(row.get("startStake")).isBlank()
                    || stateService.stringValue(row.get("endStake")).isBlank()) {
                continue;
            }
            byDirection.computeIfAbsent(String.valueOf(direction), k -> new ArrayList<>())
                    .add(new LinkedHashMap<>(row));
        }
        Comparator<Map<String, Object>> comparator = (a, b) -> {
            double startA = resolveIntervalSortStakeValue(a);
            double startB = resolveIntervalSortStakeValue(b);
            if (Double.compare(startA, startB) != 0) {
                return Double.compare(startA, startB);
            }
            double endA = resolveIntervalEndStakeValue(a);
            double endB = resolveIntervalEndStakeValue(b);
            if (Double.compare(endA, endB) != 0) {
                return Double.compare(endA, endB);
            }
            int sortA = stateService.intValue(a.get("sortNo"), Integer.MAX_VALUE);
            int sortB = stateService.intValue(b.get("sortNo"), Integer.MAX_VALUE);
            if (sortA != sortB) {
                return Integer.compare(sortA, sortB);
            }
            String intervalA = stateService.stringValue(a.get("intervalName"));
            String intervalB = stateService.stringValue(b.get("intervalName"));
            return intervalA.compareToIgnoreCase(intervalB);
        };
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int direction : List.of(DIRECTION_HAMI, DIRECTION_TURPAN)) {
            List<Map<String, Object>> items = byDirection.getOrDefault(String.valueOf(direction), new ArrayList<>());
            items.sort(comparator);
            for (int i = 0; i < items.size() && i < 3; i++) {
                rows.add(items.get(i));
            }
        }
        return rows;
    }

    private double resolveIntervalSortStakeValue(Map<String, Object> interval) {
        Double start = toNullableDouble(interval.get("startStakeValue"));
        Double end = toNullableDouble(interval.get("endStakeValue"));
        if (start == null) {
            start = parseStakeValue(stateService.stringValue(interval.get("startStake")));
        }
        if (end == null) {
            end = parseStakeValue(stateService.stringValue(interval.get("endStake")));
        }
        if (start == null && end == null) {
            return Double.MAX_VALUE;
        }
        if (start == null) {
            return end;
        }
        if (end == null) {
            return start;
        }
        return Math.min(start, end);
    }

    private double resolveIntervalEndStakeValue(Map<String, Object> interval) {
        Double start = toNullableDouble(interval.get("startStakeValue"));
        Double end = toNullableDouble(interval.get("endStakeValue"));
        if (start == null) {
            start = parseStakeValue(stateService.stringValue(interval.get("startStake")));
        }
        if (end == null) {
            end = parseStakeValue(stateService.stringValue(interval.get("endStake")));
        }
        if (start == null && end == null) {
            return Double.MAX_VALUE;
        }
        if (start == null) {
            return end;
        }
        if (end == null) {
            return start;
        }
        return Math.max(start, end);
    }

    private Map<String, Object> buildAutoGenerationRowFromPlan(Map<String, Object> plan, Map<String, Object> interval) {
        Map<String, Object> row = new LinkedHashMap<>();
        int direction = stateService.intValue(plan.get("direction"), DIRECTION_HAMI);
        int controlLevel = stateService.intValue(plan.get("controlLevel"), stateService.getDefaultControlLevel());
        int recommendedLevel = stateService.intValue(plan.get("recommendedControlLevel"), controlLevel);
        row.put("planId", stateService.stringValue(plan.get("planId")));
        row.put("segmentText", firstNonBlank(
                plan.get("segmentText"),
                resolveDashboardSegmentText(interval, direction),
                plan.get("intervalName"),
                plan.get("segment")
        ));
        row.put("direction", direction);
        row.put("directionText", firstNonBlank(plan.get("directionText"), directionToText(direction)));
        row.put("controlLevel", controlLevel);
        row.put("controlLevelText", firstNonBlank(plan.get("controlLevelText"), levelToText(controlLevel)));
        row.put("recommendedControlLevel", recommendedLevel);
        row.put("recommendedControlLevelText", firstNonBlank(plan.get("recommendedControlLevelText"), levelToText(recommendedLevel)));
        String controlEventText = stateService.stringValue(plan.get("controlEventText"));
        if (controlEventText.isBlank() && controlLevel == stateService.getDefaultControlLevel()) {
            controlEventText = "限速";
        }
        row.put("controlEventText", controlEventText);
        row.put("publishTime", stateService.stringValue(plan.get("publishTime")));
        row.put("publishEndTime", stateService.stringValue(plan.get("publishEndTime")));
        row.put("status", stateService.stringValue(plan.get("status")));
        row.put("executionApplied", Boolean.TRUE.equals(plan.get("executionApplied")));
        return row;
    }

    private void refreshDraftPlanRecommendation(Map<String, Object> plan, Map<String, Object> interval) {
        int realtimeWind = stateService.intValue(plan.get("realtimeWindLevel"), 7);
        int forecastWind = stateService.intValue(plan.get("forecastMaxWindLevel"), realtimeWind);
        int recommendedLevel = resolveConfiguredControlLevel(Math.max(realtimeWind, forecastWind));
        int currentLevel = resolveCurrentControlLevelForInterval(
                firstNonBlank(plan.get("segmentText"), plan.get("segment"), resolveDashboardSegmentText(interval, stateService.intValue(plan.get("direction"), DIRECTION_HAMI))),
                interval
        );
        Map<String, Object> template = stateService.getControlPlanLibrary().get(recommendedLevel);
        if (template == null) {
            return;
        }
        plan.put("recommendedControlLevel", recommendedLevel);
        plan.put("recommendedControlLevelText", levelToText(recommendedLevel));
        plan.put("currentControlLevel", currentLevel);
        plan.put("currentControlLevelText", levelToText(currentLevel));
        plan.put("controlLevel", currentLevel);
        plan.put("controlLevelText", levelToText(currentLevel));
        plan.put("template", new LinkedHashMap<>(template));
        plan.put("managementPlan", "LEVEL-" + recommendedLevel);
        plan.put("controlEventText", resolveControlEventText(template, recommendedLevel));
    }

    private Map<String, Object> buildEmptyAutoGenerationRow(String intervalName, int direction) {
        int defaultLevel = stateService.getDefaultControlLevel();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("planId", "");
        row.put("segmentText", intervalName);
        row.put("direction", direction);
        row.put("directionText", directionToText(direction));
        row.put("controlLevel", defaultLevel);
        row.put("controlLevelText", levelToText(defaultLevel));
        row.put("recommendedControlLevel", defaultLevel);
        row.put("recommendedControlLevelText", levelToText(defaultLevel));
        row.put("controlEventText", "限速");
        row.put("publishTime", "");
        row.put("publishEndTime", "");
        row.put("status", "NONE");
        return row;
    }

    private Map<String, Object> createDefaultDraftPlanForInterval(Map<String, Object> interval, int direction) {
        int defaultLevel = stateService.getDefaultControlLevel();
        Map<String, Object> template = stateService.getControlPlanLibrary().get(defaultLevel);
        if (template == null) {
            throw new IllegalStateException("control plan template missing: level=" + defaultLevel);
        }
        long now = System.currentTimeMillis();
        long endTimestamp = now + WINDOW_2H_MS;
        String segmentText = resolveDashboardSegmentText(interval, direction);
        String startStake = stateService.stringValue(interval.get("startStake"));
        String endStake = stateService.stringValue(interval.get("endStake"));
        boolean upstreamInterchangeFirst = isUpstreamInterchangeFirst(interval, segmentText, direction);
        boolean includeUpstreamServiceArea = shouldIncludeUpstreamServiceArea(upstreamInterchangeFirst);
        String nearestInterchangeStake = stateService.stringValue(interval.get("nearestInterchangeStake"));
        Map<String, String> vmsTexts = resolvePlanVmsTexts(template, segmentText, includeUpstreamServiceArea);

        Map<String, Object> plan = new LinkedHashMap<>();
        String planId = UUID.randomUUID().toString().substring(0, 8);
        plan.put("planId", planId);
        plan.put("timestamp", now);
        plan.put("publishTime", dtf.format(Instant.ofEpochMilli(now)));
        plan.put("endTimestamp", endTimestamp);
        plan.put("publishEndTime", dtf.format(Instant.ofEpochMilli(endTimestamp)));
        plan.put("durationHours", DEFAULT_PLAN_WINDOW_HOURS);
        plan.put("segment", segmentText);
        plan.put("segmentText", segmentText);
        plan.put("startStake", startStake);
        plan.put("endStake", endStake);
        plan.put("triggerStake", startStake);
        plan.put("expandedStartStake", startStake);
        plan.put("expandedEndStake", endStake);
        plan.put("scopeExpanded", false);
        plan.put("direction", direction);
        plan.put("directionText", directionToText(direction));
        plan.put("actualWindSpeedMs", null);
        plan.put("forecastMaxWindSpeed2hMs", null);
        plan.put("realtimeWindLevel", 7);
        plan.put("forecastMaxWindLevel", 7);
        plan.put("decisionSource", "DEFAULT_NORMAL");
        plan.put("recommendedControlLevel", defaultLevel);
        plan.put("recommendedControlLevelText", levelToText(defaultLevel));
        plan.put("currentControlLevel", defaultLevel);
        plan.put("currentControlLevelText", levelToText(defaultLevel));
        plan.put("template", new LinkedHashMap<>(template));
        plan.put("managementPlan", "LEVEL-" + defaultLevel);
        plan.put("intervalName", stateService.stringValue(interval.get("intervalName")));
        plan.put("upstreamIntervalName", stateService.stringValue(interval.get("upstreamIntervalName")));
        plan.put("hasInterchange", upstreamInterchangeFirst);
        plan.put("nearestUpstreamInterchangeStake", nearestInterchangeStake);
        plan.put("upstreamControlLevel", defaultLevel);
        plan.put("upstreamControlLevelText", levelToText(defaultLevel));
        plan.put("controlLevel", defaultLevel);
        plan.put("controlLevelText", levelToText(defaultLevel));
        plan.put("controlEventText", "限速");
        plan.put("vmsContent", vmsTexts.get("vmsContent"));
        plan.put("vmsInsideSegment", vmsTexts.get("vmsInsideSegment"));
        plan.put("vmsUpstreamExit", vmsTexts.get("vmsUpstreamExit"));
        plan.put("vmsUpstreamTollgate", vmsTexts.get("vmsUpstreamTollgate"));
        plan.put("vmsUpstreamServiceArea", vmsTexts.get("vmsUpstreamServiceArea"));
        plan.put("vmsPublishItems", buildVmsPublishItems(
                direction,
                nearestInterchangeStake,
                segmentText,
                stateService.stringValue(interval.get("intervalName")),
                stateService.stringValue(interval.get("startStake")),
                stateService.stringValue(interval.get("endStake")),
                vmsTexts.get("vmsInsideSegment"),
                vmsTexts.get("vmsUpstreamExit"),
                vmsTexts.get("vmsUpstreamTollgate"),
                vmsTexts.get("vmsUpstreamServiceArea"),
                includeUpstreamServiceArea
        ));
        plan.put("dispatch", resolveExecutionDispatch(interval, segmentText, direction));
        plan.put("status", "DRAFT");

        stateService.getGeneratedPlans().put(planId, plan);
        stateService.getPersistenceService().upsertPlan(plan);
        stateService.persistSnapshot();
        return plan;
    }

    private String intervalDirectionKey(String intervalName, int direction) {
        return stateService.stringValue(intervalName) + "#" + direction;
    }

    /**
     * 大屏图二：点击图一“编辑”后，自动生成并填充执行确认表。
     *
     * 规则：
     * 1) 开始时间重置为当前时间；
     * 2) 结束时间重置为当前+2小时；
     * 3) 仅 DRAFT 允许编辑；非 DRAFT 直接返回当前详情。
     */
    public Map<String, Object> buildExecutionTableByEdit(String planId) {
        return buildExecutionTableByEdit(planId, null);
    }

    public Map<String, Object> buildExecutionTableByEdit(String planId, Long timestamp) {
        autoCloseExpiredPublishedPlans();
        Map<String, Object> current = stateService.getGeneratedPlans().get(planId);
        if (current == null) {
            return buildMissingExecutionTable(planId, timestamp);
        }
        Long recommendationTimestamp = timestamp != null && timestamp > 0
                ? timestamp
                : longValue(current.get("timestamp"), 0L);
        String status = stateService.stringValue(current.get("status"));
        Map<String, Object> plan = "DRAFT".equalsIgnoreCase(status)
                ? updateDraftPlan(planId, new LinkedHashMap<>())
                : new LinkedHashMap<>(current);
        alignPlanRecommendationWithFuture2h(plan, recommendationTimestamp);

        Map<String, Object> table = new LinkedHashMap<>();
        int recommendedLevel = stateService.intValue(plan.get("recommendedControlLevel"),
                stateService.intValue(plan.get("controlLevel"), stateService.getDefaultControlLevel()));
        int currentLevel = stateService.intValue(plan.get("controlLevel"),
                stateService.intValue(plan.get("currentControlLevel"), stateService.getDefaultControlLevel()));
        table.put("planId", stateService.stringValue(plan.get("planId")));
        table.put("timestamp", recommendationTimestamp);
        table.put("segment", stateService.stringValue(plan.get("segment")));
        table.put("segmentText", firstNonBlank(plan.get("segmentText"), plan.get("segment")));
        table.put("intervalName", stateService.stringValue(plan.get("intervalName")));
        table.put("controlInterval", stateService.stringValue(plan.get("intervalName")));
        table.put("startStake", stateService.stringValue(plan.get("startStake")));
        table.put("endStake", stateService.stringValue(plan.get("endStake")));
        table.put("direction", stateService.intValue(plan.get("direction"), DIRECTION_HAMI));
        table.put("directionText", firstNonBlank(plan.get("directionText"), directionToText(stateService.intValue(plan.get("direction"), DIRECTION_HAMI))));
        table.put("controlLevel", recommendedLevel);
        table.put("controlLevelText", levelToText(recommendedLevel));
        table.put("currentControlLevel", recommendedLevel);
        table.put("currentControlLevelText", levelToText(recommendedLevel));
        table.put("recommendedControlLevel", recommendedLevel);
        table.put("recommendedControlLevelText", levelToText(recommendedLevel));
        table.put("publishControlLevel", recommendedLevel);
        table.put("publishControlLevelText", levelToText(recommendedLevel));
        table.put("publishTime", stateService.stringValue(plan.get("publishTime")));
        table.put("publishEndTime", stateService.stringValue(plan.get("publishEndTime")));
        table.put("durationHours", stateService.intValue(plan.get("durationHours"), DEFAULT_PLAN_WINDOW_HOURS));
        table.put("status", stateService.stringValue(plan.get("status")));
        table.put("executionApplied", Boolean.TRUE.equals(plan.get("executionApplied")));
        // 固定值：服务区/主线解封目标风力
        table.put("serviceAreaUnsealTargetWindLevel", "11级");
        table.put("mainlineUnsealTargetWindLevel", "9级");

        Map<String, Object> template = resolveTemplateByRecommendedLevel(plan, recommendedLevel);
        boolean hasInterchange = Boolean.TRUE.equals(plan.get("hasInterchange"));
        String riskSectionPlan = materializePlanText(
                stateService.stringValue(template.get("riskSectionPlan")),
                stateService.stringValue(template.get("riskSectionPlan"))
        );
        boolean includeUpstreamServiceArea = shouldIncludeUpstreamServiceArea(hasInterchange);
        Map<String, String> recommendedVmsTexts = resolvePlanVmsTexts(
                template,
                stateService.stringValue(table.get("segmentText")),
                includeUpstreamServiceArea
        );
        String vmsInsideSegment = materializePlanText(recommendedVmsTexts.get("vmsInsideSegment"), riskSectionPlan);
        String vmsUpstreamExit = materializePlanText(recommendedVmsTexts.get("vmsUpstreamExit"), riskSectionPlan);
        String vmsUpstreamTollgate = materializePlanText(recommendedVmsTexts.get("vmsUpstreamTollgate"), riskSectionPlan);
        String vmsUpstreamServiceArea = hasInterchange
                ? buildServiceAreaWelcomeText(table.get("segmentText"))
                : materializePlanText(recommendedVmsTexts.get("vmsUpstreamServiceArea"), riskSectionPlan);
        table.put("vmsInsideSegment", vmsInsideSegment);
        table.put("vmsUpstreamExit", vmsUpstreamExit);
        table.put("vmsUpstreamTollgate", vmsUpstreamTollgate);
        table.put("vmsUpstreamServiceArea", vmsUpstreamServiceArea);
        table.put("controlEventText", stateService.stringValue(plan.get("controlEventText")));
        table.put("managementPlan", stateService.stringValue(plan.get("managementPlan")));
        table.put("vmsContent", buildVmsContent(vmsInsideSegment, vmsUpstreamExit, vmsUpstreamTollgate, vmsUpstreamServiceArea));

        String upstreamExitControl = materializePlanText(stateService.stringValue(template.get("upstreamExitPlan")), riskSectionPlan);
        String upstreamTollgateControl = materializePlanText(stateService.stringValue(template.get("upstreamEntryPlan")), riskSectionPlan);
        table.put("upstreamExitControl", upstreamExitControl);
        table.put("upstreamTollgateControl", upstreamTollgateControl);
        plan.put("vmsInsideSegment", vmsInsideSegment);
        plan.put("vmsUpstreamExit", vmsUpstreamExit);
        plan.put("vmsUpstreamTollgate", vmsUpstreamTollgate);
        plan.put("vmsUpstreamServiceArea", vmsUpstreamServiceArea);
        plan.put("vmsContent", buildVmsContent(vmsInsideSegment, vmsUpstreamExit, vmsUpstreamTollgate, vmsUpstreamServiceArea));
        plan.put("vmsPublishItems", buildVmsPublishItems(
                stateService.intValue(table.get("direction"), DIRECTION_HAMI),
                stateService.stringValue(plan.get("nearestUpstreamInterchangeStake")),
                stateService.stringValue(table.get("segmentText")),
                stateService.stringValue(plan.get("intervalName")),
                stateService.stringValue(plan.get("startStake")),
                stateService.stringValue(plan.get("endStake")),
                vmsInsideSegment,
                vmsUpstreamExit,
                vmsUpstreamTollgate,
                vmsUpstreamServiceArea,
                includeUpstreamServiceArea
        ));
        List<Map<String, Object>> vmsFacilityItems = normalizeVmsPublishItems(plan.get("vmsPublishItems"));
        List<Map<String, Object>> publishRows = buildExecutionPublishRows(
                vmsFacilityItems,
                vmsInsideSegment,
                vmsUpstreamExit,
                vmsUpstreamTollgate,
                vmsUpstreamServiceArea,
                upstreamExitControl,
                upstreamTollgateControl,
                recommendedLevel
        );
        table.put("publishRows", publishRows);
        table.put("vmsPublishRows", filterVmsPublishRows(publishRows));
        table.put("vmsInsideSegmentDeviceId", resolveFirstDeviceIdByType(vmsFacilityItems, VMS_KIND_INSIDE_SEGMENT, "区段", "区间内"));
        table.put("vmsUpstreamExitDeviceId", resolveFirstDeviceIdByType(vmsFacilityItems, VMS_KIND_UPSTREAM_EXIT, "出口"));
        table.put("vmsUpstreamTollgateDeviceId", resolveFirstDeviceIdByType(vmsFacilityItems, VMS_KIND_UPSTREAM_TOLLGATE, "入口", "收费站"));
        table.put("vmsUpstreamServiceAreaDeviceId", resolveFirstDeviceIdByType(vmsFacilityItems, VMS_KIND_UPSTREAM_SERVICE_AREA, "服务区"));
        Map<String, Object> latestInterval = resolveIntervalContext(
                stateService.stringValue(plan.get("segment")),
                stateService.intValue(table.get("direction"), DIRECTION_HAMI),
                stateService.stringValue(plan.get("startStake")),
                stateService.stringValue(plan.get("endStake"))
        );
        Map<String, Object> dispatch = resolveExecutionDispatch(
                latestInterval,
                stateService.stringValue(table.get("segmentText")),
                stateService.intValue(table.get("direction"), DIRECTION_HAMI)
        );
        plan.put("dispatch", dispatch);
        stateService.getGeneratedPlans().put(stateService.stringValue(plan.get("planId")), plan);
        stateService.getPersistenceService().upsertPlan(plan);
        stateService.persistSnapshot();
        table.put("contactStaff", stateService.stringValue(dispatch.get("contactStaff")));
        table.put("teamId", stateService.stringValue(dispatch.get("teamId")));
        table.put("status", stateService.stringValue(plan.get("status")));
        table.put("executionApplied", Boolean.TRUE.equals(plan.get("executionApplied")));
        String eventId = stateService.stringValue(plan.get("eventId"));
        if (!eventId.isBlank()) {
            table.put("eventId", eventId);
        }
        String dispatchRecordId = stateService.stringValue(plan.get("dispatchRecordId"));
        if (!dispatchRecordId.isBlank()) {
            table.put("dispatchRecordId", dispatchRecordId);
        }
        return table;
    }

    private Map<String, Object> buildMissingExecutionTable(String planId, Long timestamp) {
        long responseTimestamp = timestamp != null && timestamp > 0
                ? timestamp
                : System.currentTimeMillis();
        int defaultLevel = stateService.getDefaultControlLevel();
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("planId", "");
        table.put("requestedPlanId", planId);
        table.put("timestamp", responseTimestamp);
        table.put("segment", "");
        table.put("segmentText", "");
        table.put("intervalName", "");
        table.put("controlInterval", "");
        table.put("startStake", "");
        table.put("endStake", "");
        table.put("direction", DIRECTION_HAMI);
        table.put("directionText", directionToText(DIRECTION_HAMI));
        table.put("controlLevel", defaultLevel);
        table.put("controlLevelText", levelToText(defaultLevel));
        table.put("currentControlLevel", defaultLevel);
        table.put("currentControlLevelText", levelToText(defaultLevel));
        table.put("recommendedControlLevel", defaultLevel);
        table.put("recommendedControlLevelText", levelToText(defaultLevel));
        table.put("publishControlLevel", defaultLevel);
        table.put("publishControlLevelText", levelToText(defaultLevel));
        table.put("publishTime", "");
        table.put("publishEndTime", "");
        table.put("durationHours", DEFAULT_PLAN_WINDOW_HOURS);
        table.put("status", "MISSING");
        table.put("planMissing", true);
        table.put("needRefresh", true);
        table.put("message", "plan not found, please regenerate after restart");
        table.put("serviceAreaUnsealTargetWindLevel", "11级");
        table.put("mainlineUnsealTargetWindLevel", "9级");
        table.put("vmsInsideSegment", "");
        table.put("vmsUpstreamExit", "");
        table.put("vmsUpstreamTollgate", "");
        table.put("vmsUpstreamServiceArea", "");
        table.put("controlEventText", "");
        table.put("managementPlan", "");
        table.put("vmsContent", buildVmsContent("", "", "", ""));
        table.put("upstreamExitControl", "");
        table.put("upstreamTollgateControl", "");
        table.put("publishRows", buildEmptyExecutionPublishRows());
        table.put("vmsPublishRows", filterVmsPublishRows(buildEmptyExecutionPublishRows()));
        table.put("vmsInsideSegmentDeviceId", "");
        table.put("vmsUpstreamExitDeviceId", "");
        table.put("vmsUpstreamTollgateDeviceId", "");
        table.put("vmsUpstreamServiceAreaDeviceId", "");
        table.put("contactStaff", "");
        table.put("teamId", "");
        return table;
    }

    private List<Map<String, Object>> buildEmptyExecutionPublishRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(executionPublishRow("VMS", "管控区间内VMS", "", ""));
        rows.add(executionPublishRow("VMS", "管控区间上游互通出口VMS", "", ""));
        rows.add(executionPublishRow("VMS", "管控区间上游互通入口收费站VMS", "", ""));
        rows.add(executionPublishRow("VMS", "服务区前VMS", "", ""));
        rows.add(executionPublishRow("CONTROL", "管控区间上游互通出口", "", ""));
        rows.add(executionPublishRow("CONTROL", "管控区间上游互通入口收费站", "", ""));
        return rows;
    }

    /**
     * 大屏图二：单独查询设备发布信息（vmsPublishItems）。
     *
     * 每次按两个方向所有未关闭方案现场重新生成，同一设备命中的多条方案内容以后执行的覆盖先执行的。
     * 未被后续方案命中的设备保留之前方案生成的发布内容，不重置为欢迎语。
     */
    public List<Map<String, Object>> listExecutionVmsPublishItems(String planId) {
        autoCloseExpiredPublishedPlans();
        Map<String, Object> plan = stateService.getGeneratedPlans().get(planId);
        if (plan == null) {
            return buildResetDevicePublishInfoRows();
        }
        List<Map<String, Object>> activePlans = stateService.getGeneratedPlans().values().stream()
                .filter(item -> !"CLOSED".equalsIgnoreCase(stateService.stringValue(item.get("status"))))
                .filter(item -> Boolean.TRUE.equals(item.get("executionApplied")))
                .filter(item -> longValue(item.get("timestamp"), 0L) >= serviceStartTimestamp)
                .sorted((a, b) -> Long.compare(
                        longValue(a.get("timestamp"), 0L),
                        longValue(b.get("timestamp"), 0L)
                ))
                .toList();
        if (activePlans.isEmpty()) {
            return buildResetDevicePublishInfoRows();
        }
        return buildMergedDevicePublishInfoRows(activePlans);
    }

    private List<Map<String, Object>> buildResetDevicePublishInfoRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> facility : getPublishFacilitiesForExecution()) {
            int facilityDirection = stateService.intValue(facility.get("direction"), -1);
            String deviceId = stateService.stringValue(facility.get("facilityId"));
            if (deviceId.isBlank()) {
                continue;
            }
            String currentPublishInfo = buildFacilityWelcomeText(
                    stateService.stringValue(facility.get("segment")),
                    stateService.stringValue(facility.get("segment"))
            );
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deviceId", deviceId);
            row.put("direction", facilityDirection);
            row.put("directionText", directionToText(facilityDirection));
            row.put("currentPublishInfo", currentPublishInfo.isBlank() ? "" : normalizePublishPrefix(currentPublishInfo));
            rows.add(row);
        }
        return rows;
    }

    private void alignAutoGenerationLevelsWithWindRows(Map<String, Object> row,
                                                       Map<String, Object> interval,
                                                       List<WindData> latestRows,
                                                       List<WindData> future2hRows,
                                                       Map<String, Map<String, Object>> future2hImpactByStakeAndDirection) {
        int direction = stateService.intValue(row.get("direction"), stateService.intValue(interval.get("direction"), DIRECTION_HAMI));
        String[] stakes = resolveSpatiotemporalStakes(row, interval);
        String startStake = firstNonBlank(stakes[0], row.get("startStake"), interval.get("startStake"));
        String endStake = firstNonBlank(stakes[1], row.get("endStake"), interval.get("endStake"));
        String stakeRange = firstNonBlank(
                resolveSpatiotemporalStakeRange(row, interval),
                buildStakeRange(startStake, endStake)
        );
        int currentWindLevel = resolveMaxWindLevelFromRows(direction, startStake, endStake, latestRows);
        String future2hImpactStakeRange = firstNonBlank(
                resolveFuture2hImpactStakeRange(row, interval),
                normalizeFixedImpactStakeRange(stakeRange),
                stakeRange
        );
        Map<String, Object> future2hImpact = future2hImpactByStakeAndDirection.get(
                stakeDirectionKey(future2hImpactStakeRange, direction)
        );
        int future2hWindLevel = future2hImpact == null
                ? resolveMaxWindLevelFromRows(direction, startStake, endStake, future2hRows)
                : stateService.intValue(future2hImpact.get("maxWindLevel"), 0);
        Integer cachedCurrentLevel = findCurrentControlLevelForInterval(
                firstNonBlank(row.get("segmentText"), row.get("segment"), interval.get("segmentText"), interval.get("segment")),
                interval
        );
        if (currentWindLevel > 0) {
            row.put("realtimeWindLevel", currentWindLevel);
        }
        if (cachedCurrentLevel != null) {
            row.put("controlLevel", cachedCurrentLevel);
            row.put("controlLevelText", levelToText(cachedCurrentLevel));
        } else if (currentWindLevel > 0 && !hasExecutedControlLevel(row)) {
            int controlLevel = resolveConfiguredControlLevel(currentWindLevel);
            row.put("controlLevel", controlLevel);
            row.put("controlLevelText", levelToText(controlLevel));
        }
        Integer recommendedLevel = future2hImpact == null
                ? (future2hWindLevel > 0 ? resolveConfiguredControlLevel(future2hWindLevel) : null)
                : toNullableInt(future2hImpact.get("recommendedControlLevel"));
        if (future2hWindLevel <= 0 || recommendedLevel == null) {
            row.put("forecastMaxWindLevel", null);
            row.put("recommendedControlLevel", null);
            row.put("recommendedControlLevelText", "");
            return;
        }
        row.put("forecastMaxWindLevel", future2hWindLevel);
        row.put("recommendedControlLevel", recommendedLevel);
        row.put("recommendedControlLevelText", levelToText(recommendedLevel));
    }

    /**
     * 图二固定六行数据：四条 VMS 发布内容 + 两条管控措施。
     */
    private void alignPlanRecommendationWithFuture2h(Map<String, Object> plan, Long timestamp) {
        if (plan == null || plan.isEmpty()) {
            return;
        }
        int direction = stateService.intValue(plan.get("direction"), DIRECTION_HAMI);
        String[] stakes = resolveSpatiotemporalStakes(plan, Collections.emptyMap());
        String startStake = firstNonBlank(stakes[0], plan.get("startStake"));
        String endStake = firstNonBlank(stakes[1], plan.get("endStake"));
        String stakeRange = firstNonBlank(resolveSpatiotemporalStakeRange(plan), buildStakeRange(startStake, endStake));
        int future2hWindLevel = 0;
        int currentWindLevel = 0;
        Map<String, Map<String, Object>> future2hImpactByStakeAndDirection =
                loadFuture2hImpactByStakeAndDirection(timestamp == null || timestamp <= 0
                        ? longValue(plan.get("timestamp"), System.currentTimeMillis())
                        : timestamp);
        String future2hImpactStakeRange = firstNonBlank(
                resolveFuture2hImpactStakeRange(plan, Collections.emptyMap()),
                normalizeFixedImpactStakeRange(stakeRange),
                stakeRange
        );
        Map<String, Object> future2hImpact = future2hImpactByStakeAndDirection.get(
                stakeDirectionKey(future2hImpactStakeRange, direction)
        );
        if (!startStake.isBlank() && !endStake.isBlank()) {
            long baseTimestamp = timestamp == null || timestamp <= 0
                    ? longValue(plan.get("timestamp"), System.currentTimeMillis())
                    : timestamp;
            LocalDateTime baseTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(baseTimestamp), ZoneId.systemDefault());
            List<WindData> latestRows = windDataService.listLatestSnapshot(baseTime);
            List<WindData> future2hRows = windDataService.listByTimeRange(
                    baseTime,
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(baseTimestamp + WINDOW_2H_MS), ZoneId.systemDefault())
            );
            currentWindLevel = resolveMaxWindLevelFromRows(direction, startStake, endStake, latestRows);
            future2hWindLevel = future2hImpact == null
                    ? resolveMaxWindLevelFromRows(direction, startStake, endStake, future2hRows)
                    : stateService.intValue(future2hImpact.get("maxWindLevel"), 0);
        }
        int fallbackWindLevel = Math.max(
                stateService.intValue(plan.get("forecastMaxWindLevel"), 0),
                stateService.intValue(plan.get("realtimeWindLevel"), 0)
        );
        Integer impactRecommendedLevel = future2hImpact == null
                ? null
                : toNullableInt(future2hImpact.get("recommendedControlLevel"));
        int recommendedLevel = impactRecommendedLevel != null
                ? impactRecommendedLevel
                : future2hWindLevel > 0
                ? resolveConfiguredControlLevel(future2hWindLevel)
                : fallbackWindLevel > 0
                ? resolveConfiguredControlLevel(fallbackWindLevel)
                : stateService.intValue(plan.get("recommendedControlLevel"),
                stateService.intValue(plan.get("controlLevel"), stateService.getDefaultControlLevel()));
        int currentLevel = currentWindLevel > 0
                ? resolveConfiguredControlLevel(currentWindLevel)
                : stateService.intValue(plan.get("controlLevel"),
                stateService.intValue(plan.get("currentControlLevel"), stateService.getDefaultControlLevel()));
        Map<String, Object> template = resolveTemplateByRecommendedLevel(plan, recommendedLevel);
        applyRecommendedTemplateToPlan(plan, currentLevel, recommendedLevel, template, future2hWindLevel);
    }

    private void alignPlanLevelsWithWindRows(Map<String, Object> plan,
                                             Map<String, Object> interval,
                                             List<WindData> latestRows,
                                             List<WindData> future2hRows,
                                             Map<String, Map<String, Object>> future2hImpactByStakeAndDirection) {
        if (plan == null || plan.isEmpty()) {
            return;
        }
        int direction = stateService.intValue(plan.get("direction"), stateService.intValue(interval.get("direction"), DIRECTION_HAMI));
        boolean executed = hasExecutedControlLevel(plan);
        String[] stakes = resolveSpatiotemporalStakes(plan, interval);
        String startStake = firstNonBlank(stakes[0], plan.get("startStake"), interval.get("startStake"));
        String endStake = firstNonBlank(stakes[1], plan.get("endStake"), interval.get("endStake"));
        String stakeRange = firstNonBlank(
                resolveSpatiotemporalStakeRange(plan, interval),
                buildStakeRange(startStake, endStake)
        );
        int currentWindLevel = resolveMaxWindLevelFromRows(direction, startStake, endStake, latestRows);
        String future2hImpactStakeRange = firstNonBlank(
                resolveFuture2hImpactStakeRange(plan, interval),
                normalizeFixedImpactStakeRange(stakeRange),
                stakeRange
        );
        Map<String, Object> future2hImpact = future2hImpactByStakeAndDirection.get(
                stakeDirectionKey(future2hImpactStakeRange, direction)
        );
        int future2hWindLevel = future2hImpact == null
                ? resolveMaxWindLevelFromRows(direction, startStake, endStake, future2hRows)
                : stateService.intValue(future2hImpact.get("maxWindLevel"), 0);
        if (currentWindLevel <= 0 && future2hWindLevel <= 0) {
            return;
        }
        Integer cachedCurrentLevel = findCurrentControlLevelForInterval(
                firstNonBlank(plan.get("segmentText"), plan.get("segment"), interval.get("segmentText"), interval.get("segment")),
                interval
        );
        int currentLevel = cachedCurrentLevel != null
                ? cachedCurrentLevel
                : executed
                ? stateService.intValue(plan.get("controlLevel"),
                stateService.intValue(plan.get("currentControlLevel"), stateService.getDefaultControlLevel()))
                : currentWindLevel > 0
                ? resolveConfiguredControlLevel(currentWindLevel)
                : stateService.intValue(plan.get("controlLevel"),
                stateService.intValue(plan.get("currentControlLevel"), stateService.getDefaultControlLevel()));
        Integer impactRecommendedLevel = future2hImpact == null
                ? null
                : toNullableInt(future2hImpact.get("recommendedControlLevel"));
        int recommendedLevel = impactRecommendedLevel != null
                ? impactRecommendedLevel
                : future2hWindLevel > 0
                ? resolveConfiguredControlLevel(future2hWindLevel)
                : stateService.intValue(plan.get("recommendedControlLevel"), currentLevel);
        Map<String, Object> template = resolveTemplateByRecommendedLevel(plan, recommendedLevel);
        applyRecommendedTemplateToPlan(plan, currentLevel, recommendedLevel, template, future2hWindLevel);
    }

    private Map<String, Map<String, Object>> loadFuture2hImpactByStakeAndDirection(long timestamp) {
        Map<String, Map<String, Object>> records = new LinkedHashMap<>();
        Map<String, Object> result = windImpactService.evaluateSpatiotemporalImpactFuture2h(timestamp);
        Object rawRecords = result.get("records");
        if (!(rawRecords instanceof List<?> list)) {
            return records;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> record = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                record.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            String stakeRange = stateService.stringValue(record.get("stakeRange"));
            int direction = stateService.intValue(record.get("direction"), 0);
            if (stakeRange.isBlank() || direction == 0) {
                continue;
            }
            records.put(stakeDirectionKey(stakeRange, direction), record);
        }
        return records;
    }

    private String stakeDirectionKey(String stakeRange, int direction) {
        return stateService.stringValue(stakeRange) + "#" + direction;
    }

    private String buildStakeRange(String startStake, String endStake) {
        if (startStake == null || endStake == null || startStake.isBlank() || endStake.isBlank()) {
            return "";
        }
        return startStake + "-" + endStake;
    }

    private Map<String, Object> resolveTemplateByRecommendedLevel(Map<String, Object> plan, int recommendedLevel) {
        Map<String, Object> template = stateService.getControlPlanLibrary().get(recommendedLevel);
        if (template != null) {
            return template;
        }
        return safeMap(plan == null ? null : plan.get("template"));
    }

    private String resolveSpatiotemporalStakeRange(Map<String, Object> plan) {
        return resolveSpatiotemporalStakeRange(plan, Collections.emptyMap());
    }

    private String resolveSpatiotemporalStakeRange(Map<String, Object> plan, Map<String, Object> interval) {
        String currentIntervalRange = resolveCurrentIntervalStakeRange(plan, interval);
        if (!currentIntervalRange.isBlank()) {
            return currentIntervalRange;
        }

        int direction = stateService.intValue(
                firstNonBlank(
                        plan == null ? null : plan.get("direction"),
                        interval == null ? null : interval.get("direction")
                ),
                DIRECTION_HAMI
        );
        String intervalRange = resolveFixedImpactStakeRangeByIntervalName(firstNonBlank(
                plan == null ? null : plan.get("intervalName"),
                interval == null ? null : interval.get("intervalName")
        ));
        if (!intervalRange.isBlank()) {
            return intervalRange;
        }

        String segmentText = normalizeSegmentLookupKey(firstNonBlank(
                plan == null ? null : plan.get("segmentText"),
                plan == null ? null : plan.get("segment"),
                interval == null ? null : interval.get("segmentText"),
                interval == null ? null : interval.get("segment")
        ));
        String mappedRange = resolveFixedStakeRangeBySegmentText(segmentText, direction);
        if (!mappedRange.isBlank()) {
            return mappedRange;
        }
        return "";
    }

    private String resolveFuture2hImpactStakeRange(Map<String, Object> plan, Map<String, Object> interval) {
        int direction = stateService.intValue(
                firstNonBlank(
                        plan == null ? null : plan.get("direction"),
                        interval == null ? null : interval.get("direction")
                ),
                DIRECTION_HAMI
        );
        String intervalRange = resolveFixedImpactStakeRangeByIntervalName(firstNonBlank(
                plan == null ? null : plan.get("intervalName"),
                interval == null ? null : interval.get("intervalName")
        ));
        if (!intervalRange.isBlank()) {
            return intervalRange;
        }

        String segmentText = firstNonBlank(
                plan == null ? null : plan.get("segmentText"),
                plan == null ? null : plan.get("segment"),
                interval == null ? null : interval.get("segmentText"),
                interval == null ? null : interval.get("segment")
        );
        String mappedByDirection = resolveFixedStakeRangeBySegmentText(segmentText, direction);
        if (!mappedByDirection.isBlank()) {
            return mappedByDirection;
        }
        String mappedRange = mapSegmentTextToStakeRange(segmentText);
        if (!mappedRange.isBlank()) {
            return mappedRange;
        }

        String planRange = normalizeFixedImpactStakeRange(buildStakeRange(
                plan == null ? "" : stateService.stringValue(plan.get("startStake")),
                plan == null ? "" : stateService.stringValue(plan.get("endStake"))
        ));
        if (!planRange.isBlank()) {
            return planRange;
        }
        String intervalMappedRange = normalizeFixedImpactStakeRange(buildStakeRange(
                interval == null ? "" : stateService.stringValue(interval.get("startStake")),
                interval == null ? "" : stateService.stringValue(interval.get("endStake"))
        ));
        if (!intervalMappedRange.isBlank()) {
            return intervalMappedRange;
        }
        return normalizeFixedImpactStakeRange(resolveSpatiotemporalStakeRange(plan, interval));
    }

    private String resolveFixedImpactStakeRangeByIntervalName(Object intervalNameObj) {
        String intervalName = stateService.stringValue(intervalNameObj).trim();
        if ("1-1".equals(intervalName) || "2-1".equals(intervalName)) {
            return "K3178-K3192";
        }
        if ("1-2".equals(intervalName) || "2-2".equals(intervalName)) {
            return "K3192-K3197";
        }
        if ("1-3".equals(intervalName) || "2-3".equals(intervalName)) {
            return "K3197-K3204";
        }
        return "";
    }

    private String normalizeFixedImpactStakeRange(String stakeRange) {
        double[] range = parseStakeRange(stakeRange);
        if (range == null) {
            return "";
        }
        if (isSameStakeRange(range, 3178D, 3192D)) {
            return "K3178-K3192";
        }
        if (isSameStakeRange(range, 3192D, 3197D)) {
            return "K3192-K3197";
        }
        if (isSameStakeRange(range, 3197D, 3204D)) {
            return "K3197-K3204";
        }
        return "";
    }

    private boolean isSameStakeRange(double[] range, double start, double end) {
        return range != null
                && range.length >= 2
                && Math.abs(range[0] - start) < 0.001D
                && Math.abs(range[1] - end) < 0.001D;
    }

    private String resolveFixedStakeRangeBySegmentText(String segmentText, int direction) {
        if (segmentText == null || segmentText.isBlank()) {
            return "";
        }
        String normalized = normalizeSegmentLookupKey(segmentText);
        if (direction == DIRECTION_HAMI) {
            if (normalized.contains("红山口服务区-一碗泉服务区")) {
                return "K3178-K3192";
            }
            if (normalized.contains("红山口互通-红山口服务区")) {
                return "K3192-K3197";
            }
            if (normalized.contains("沙尔湖服务区-红山口互通")) {
                return "K3197-K3204";
            }
        }
        if (direction == DIRECTION_TURPAN) {
            if (normalized.contains("一碗泉服务区-红山口服务区")) {
                return "K3178-K3192";
            }
            if (normalized.contains("红山口服务区-红山口互通")) {
                return "K3192-K3197";
            }
            if (normalized.contains("红山口互通-沙尔湖服务区")) {
                return "K3197-K3204";
            }
        }
        return "";
    }

    private String resolveCurrentIntervalStakeRange(Map<String, Object> plan, Map<String, Object> interval) {
        String intervalStart = interval == null ? "" : stateService.stringValue(interval.get("startStake"));
        String intervalEnd = interval == null ? "" : stateService.stringValue(interval.get("endStake"));
        String intervalRange = buildStakeRange(intervalStart, intervalEnd);
        if (!intervalRange.isBlank()) {
            return intervalRange;
        }

        int direction = stateService.intValue(
                firstNonBlank(
                        plan == null ? null : plan.get("direction"),
                        interval == null ? null : interval.get("direction")
                ),
                DIRECTION_HAMI
        );
        for (Object candidate : Arrays.asList(
                plan == null ? null : plan.get("intervalName"),
                plan == null ? null : plan.get("segmentText"),
                plan == null ? null : plan.get("segment"),
                interval == null ? null : interval.get("intervalName"),
                interval == null ? null : interval.get("segmentText"),
                interval == null ? null : interval.get("segment")
        )) {
            String key = stateService.stringValue(candidate);
            if (key.isBlank()) {
                continue;
            }
            Map<String, Object> matched = stateService.getDispatchPlanLibrary().get(key);
            if (matched == null) {
                continue;
            }
            if (direction != stateService.intValue(matched.get("direction"), direction)) {
                continue;
            }
            String matchedRange = buildStakeRange(
                    stateService.stringValue(matched.get("startStake")),
                    stateService.stringValue(matched.get("endStake"))
            );
            if (!matchedRange.isBlank()) {
                return matchedRange;
            }
        }

        return buildStakeRange(
                plan == null ? "" : stateService.stringValue(plan.get("startStake")),
                plan == null ? "" : stateService.stringValue(plan.get("endStake"))
        );
    }

    private String[] resolveSpatiotemporalStakes(Map<String, Object> plan, Map<String, Object> interval) {
        String stakeRange = resolveSpatiotemporalStakeRange(plan, interval);
        if (stakeRange.isBlank()) {
            return new String[]{"", ""};
        }
        return new String[]{extractStake(stakeRange, true), extractStake(stakeRange, false)};
    }

    private void applyRecommendedTemplateToPlan(Map<String, Object> plan,
                                                int currentLevel,
                                                int recommendedLevel,
                                                Map<String, Object> template,
                                                int future2hWindLevel) {
        if (plan == null || template == null || template.isEmpty()) {
            return;
        }
        int direction = stateService.intValue(plan.get("direction"), DIRECTION_HAMI);
        String segmentText = firstNonBlank(plan.get("segmentText"), plan.get("segment"));
        boolean hasInterchange = Boolean.TRUE.equals(plan.get("hasInterchange"));
        boolean includeUpstreamServiceArea = shouldIncludeUpstreamServiceArea(hasInterchange);
        Map<String, String> vmsTexts = resolvePlanVmsTexts(template, segmentText, includeUpstreamServiceArea);
        plan.put("recommendedControlLevel", recommendedLevel);
        plan.put("recommendedControlLevelText", levelToText(recommendedLevel));
        plan.put("currentControlLevel", currentLevel);
        plan.put("currentControlLevelText", levelToText(currentLevel));
        int effectiveControlLevel = currentLevel;
        plan.put("controlLevel", effectiveControlLevel);
        plan.put("controlLevelText", levelToText(effectiveControlLevel));
        if (future2hWindLevel > 0) {
            plan.put("forecastMaxWindLevel", future2hWindLevel);
        }
        plan.put("template", new LinkedHashMap<>(template));
        plan.put("managementPlan", "LEVEL-" + recommendedLevel);
        plan.put("controlEventText", resolveControlEventText(template, recommendedLevel));
        plan.put("vmsContent", vmsTexts.get("vmsContent"));
        plan.put("vmsInsideSegment", vmsTexts.get("vmsInsideSegment"));
        plan.put("vmsUpstreamExit", vmsTexts.get("vmsUpstreamExit"));
        plan.put("vmsUpstreamTollgate", vmsTexts.get("vmsUpstreamTollgate"));
        plan.put("vmsUpstreamServiceArea", vmsTexts.get("vmsUpstreamServiceArea"));
        plan.put("vmsPublishItems", buildVmsPublishItems(
                direction,
                stateService.stringValue(plan.get("nearestUpstreamInterchangeStake")),
                segmentText,
                stateService.stringValue(plan.get("intervalName")),
                stateService.stringValue(plan.get("startStake")),
                stateService.stringValue(plan.get("endStake")),
                vmsTexts.get("vmsInsideSegment"),
                vmsTexts.get("vmsUpstreamExit"),
                vmsTexts.get("vmsUpstreamTollgate"),
                vmsTexts.get("vmsUpstreamServiceArea"),
                includeUpstreamServiceArea
        ));
    }

    private List<Map<String, Object>> buildExecutionPublishRows(List<Map<String, Object>> vmsFacilityItems,
                                                                String vmsInsideSegment,
                                                                String vmsUpstreamExit,
                                                                String vmsUpstreamTollgate,
                                                                String vmsUpstreamServiceArea,
                                                                String upstreamExitControl,
                                                                String upstreamTollgateControl,
                                                                int controlLevel) {
        String insideDeviceIds = resolveFirstDeviceIdByType(vmsFacilityItems, VMS_KIND_INSIDE_SEGMENT, "区段", "区间内");
        String exitDeviceIds = resolveFirstDeviceIdByType(vmsFacilityItems, VMS_KIND_UPSTREAM_EXIT, "出口");
        String tollgateDeviceIds = resolveFirstDeviceIdByType(vmsFacilityItems, VMS_KIND_UPSTREAM_TOLLGATE, "入口", "收费站");
        String serviceAreaDeviceIds = resolveFirstDeviceIdByType(vmsFacilityItems, VMS_KIND_UPSTREAM_SERVICE_AREA, "服务区");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(executionPublishRow("VMS", "管控区间内VMS", insideDeviceIds, vmsInsideSegment,
                resolveFixedVmsContent(controlLevel, VMS_KIND_INSIDE_SEGMENT, vmsInsideSegment)));
        rows.add(executionPublishRow("VMS", "管控区间上游互通出口VMS", exitDeviceIds, vmsUpstreamExit,
                resolveFixedVmsContent(controlLevel, VMS_KIND_UPSTREAM_EXIT, vmsUpstreamExit)));
        rows.add(executionPublishRow("VMS", "管控区间上游互通入口收费站VMS", tollgateDeviceIds, vmsUpstreamTollgate,
                resolveFixedVmsContent(controlLevel, VMS_KIND_UPSTREAM_TOLLGATE, vmsUpstreamTollgate)));
        rows.add(executionPublishRow("VMS", "服务区前VMS", serviceAreaDeviceIds, vmsUpstreamServiceArea,
                resolveFixedVmsContent(controlLevel, VMS_KIND_UPSTREAM_SERVICE_AREA, vmsUpstreamServiceArea)));
        rows.add(executionPublishRow("CONTROL", "管控区间上游互通出口", "", upstreamExitControl));
        rows.add(executionPublishRow("CONTROL", "管控区间上游互通入口收费站", "", upstreamTollgateControl));
        return rows;
    }

    private Map<String, Object> executionPublishRow(String itemType, String target, String deviceId, String content) {
        return executionPublishRow(itemType, target, deviceId, content, new FixedVmsContent("", ""));
    }

    private Map<String, Object> executionPublishRow(String itemType, String target, String deviceId, String content, FixedVmsContent fixedContent) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemType", itemType);
        row.put("target", target);
        row.put("deviceId", deviceId == null ? "" : deviceId);
        row.put("content", content == null ? "" : content);
        if ("VMS".equalsIgnoreCase(itemType)) {
            putFixedVmsContent(row, fixedContent);
        }
        return row;
    }

    private void putFixedVmsContent(Map<String, Object> row, FixedVmsContent fixedContent) {
        if (fixedContent == null) {
            row.put("fixedMainContent", "");
            row.put("fixedTipContent", "");
            return;
        }
        row.put("fixedMainContent", fixedContent.mainContent());
        row.put("fixedTipContent", fixedContent.tipContent());
    }

    private List<Map<String, Object>> filterVmsPublishRows(List<Map<String, Object>> publishRows) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (publishRows == null) {
            return rows;
        }
        for (Map<String, Object> row : publishRows) {
            if ("VMS".equalsIgnoreCase(stateService.stringValue(row.get("itemType")))) {
                rows.add(new LinkedHashMap<>(row));
            }
        }
        return rows;
    }

    /**
     * 图二“当前发布信息”表：返回该方向下所有设备的发布内容。
     * 命中本次管控自动发布的设备返回对应文案；未命中设备返回“xxx欢迎您”。
     */
    private List<Map<String, Object>> buildAllDevicePublishInfoRows(int direction,
                                                                    String segmentText,
                                                                    String riskSectionPlan,
                                                                    List<Map<String, Object>> selectedPublishItems) {
        Map<String, String> selectedMessageByDeviceId = new LinkedHashMap<>();
        for (Map<String, Object> item : selectedPublishItems) {
            String deviceId = firstNonBlank(item.get("deviceId"), item.get("facilityId"));
            String message = stateService.stringValue(item.get("publishMessage"));
            if (!deviceId.isBlank() && !message.isBlank()) {
                selectedMessageByDeviceId.put(deviceId, materializePlanText(message, riskSectionPlan));
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> facility : getPublishFacilitiesForExecution()) {
            int facilityDirection = stateService.intValue(facility.get("direction"), -1);
            String deviceId = stateService.stringValue(facility.get("facilityId"));
            if (facilityDirection != direction || deviceId.isBlank()) {
                continue;
            }
            String publishMessage = selectedMessageByDeviceId.get(deviceId);
            if (publishMessage == null || publishMessage.isBlank()) {
                publishMessage = buildFacilityWelcomeText(
                        stateService.stringValue(facility.get("segment")),
                        segmentText
                );
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deviceId", deviceId);
            row.put("direction", facilityDirection);
            row.put("directionText", directionToText(facilityDirection));
            row.put("currentPublishInfo", publishMessage.isBlank() ? "" : normalizePublishPrefix(publishMessage));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> buildMergedDevicePublishInfoRows(List<Map<String, Object>> plans) {
        Map<String, String> messagesByDeviceId = new LinkedHashMap<>();
        for (Map<String, Object> item : plans == null ? List.<Map<String, Object>>of() : plans) {
            Map<String, Object> template = safeMap(item.get("template"));
            String riskSectionPlan = stateService.stringValue(template.get("riskSectionPlan"));
            List<Map<String, Object>> selectedPublishItems = normalizeVmsPublishItems(item.get("vmsPublishItems"));
            for (Map<String, Object> publishItem : selectedPublishItems) {
                String deviceId = firstNonBlank(publishItem.get("deviceId"), publishItem.get("facilityId"));
                String message = stateService.stringValue(publishItem.get("publishMessage"));
                if (deviceId.isBlank() || message.isBlank()) {
                    continue;
                }
                messagesByDeviceId.put(deviceId, normalizePublishPrefix(materializePlanText(message, riskSectionPlan)));
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> facility : getPublishFacilitiesForExecution()) {
            int facilityDirection = stateService.intValue(facility.get("direction"), -1);
            String deviceId = stateService.stringValue(facility.get("facilityId"));
            if (deviceId.isBlank()) {
                continue;
            }
            String currentPublishInfo = messagesByDeviceId.get(deviceId);
            if (currentPublishInfo == null || currentPublishInfo.isBlank()) {
                currentPublishInfo = buildFacilityWelcomeText(
                        stateService.stringValue(facility.get("segment")),
                        firstNonBlank(facility.get("segment"), facility.get("segment"))
                );
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deviceId", deviceId);
            row.put("direction", facilityDirection);
            row.put("directionText", directionToText(facilityDirection));
            row.put("currentPublishInfo", currentPublishInfo.isBlank() ? "" : normalizePublishPrefix(currentPublishInfo));
            rows.add(row);
        }
        return rows;
    }

    private String buildFacilityWelcomeText(String facilitySegment, String segmentText) {
        String text = firstNonBlank(facilitySegment, segmentText).trim();
        if (text.isBlank()) {
            return "欢迎您";
        }
        String[] tokens = text.split("-");
        for (String token : tokens) {
            String value = token.trim();
            if (value.contains("服务区")) {
                return value + SERVICE_AREA_WELCOME_SUFFIX;
            }
        }
        for (String token : tokens) {
            String value = token.trim();
            if (value.contains("互通")) {
                return value + SERVICE_AREA_WELCOME_SUFFIX;
            }
        }
        if (text.endsWith("前")) {
            text = text.substring(0, text.length() - 1);
        }
        return text + SERVICE_AREA_WELCOME_SUFFIX;
    }

    private String normalizePublishPrefix(String message) {
        String text = message == null ? "" : message.trim();
        if (text.isBlank()) {
            return "";
        }
        if (text.startsWith("发布内容：")) {
            return text;
        }
        return "发布内容：" + text;
    }

    /**
     * 执行落库：仅更新当前方向设备的 postInformation，另一方向保持不变。
     */
    private void persistCurrentDirectionPublishInfo(int direction, List<Map<String, Object>> allDevicePublishRows) {
        if (allDevicePublishRows == null || allDevicePublishRows.isEmpty()) {
            return;
        }
        Map<String, String> publishInfoByDeviceId = new LinkedHashMap<>();
        for (Map<String, Object> row : allDevicePublishRows) {
            String deviceId = stateService.stringValue(row.get("deviceId"));
            if (deviceId.isBlank()) {
                continue;
            }
            String currentPublishInfo = stripPublishPrefix(stateService.stringValue(row.get("currentPublishInfo")));
            publishInfoByDeviceId.put(deviceId, currentPublishInfo);
        }

        Map<String, Map<String, Object>> stateFacilityById = new LinkedHashMap<>();
        for (Map<String, Object> facility : stateService.getPublishFacilities()) {
            String facilityId = stateService.stringValue(facility.get("facilityId"));
            if (!facilityId.isBlank()) {
                stateFacilityById.put(facilityId, facility);
            }
        }

        // 先更新已有快照设备。
        for (Map<String, Object> facility : stateFacilityById.values()) {
            int facilityDirection = stateService.intValue(facility.get("direction"), -1);
            if (facilityDirection != direction) {
                continue;
            }
            String deviceId = stateService.stringValue(facility.get("facilityId"));
            if (deviceId.isBlank()) {
                continue;
            }
            if (publishInfoByDeviceId.containsKey(deviceId)) {
                facility.put("postInformation", publishInfoByDeviceId.get(deviceId));
            }
        }

        // 若快照中缺少当前静态设备，则补齐设备行并写入发布内容，确保 GET 能直接读到。
        for (Map<String, Object> facility : getPublishFacilitiesForExecution()) {
            int facilityDirection = stateService.intValue(facility.get("direction"), -1);
            if (facilityDirection != direction) {
                continue;
            }
            String deviceId = stateService.stringValue(facility.get("facilityId"));
            if (deviceId.isBlank() || !publishInfoByDeviceId.containsKey(deviceId)) {
                continue;
            }
            Map<String, Object> stateRow = stateFacilityById.get(deviceId);
            if (stateRow == null) {
                stateRow = new LinkedHashMap<>();
                stateRow.put("facilityId", deviceId);
                stateRow.put("pileNo", stateService.stringValue(facility.get("pileNo")));
                stateRow.put("direction", facilityDirection);
                stateRow.put("type", stateService.stringValue(facility.get("type")));
                stateRow.put("segment", stateService.stringValue(facility.get("segment")));
                stateRow.put("interchangeName", stateService.stringValue(facility.get("interchangeName")));
                stateRow.put("interchangeStake", stateService.stringValue(facility.get("interchangeStake")));
                stateRow.put("redAlertMessage", stateService.stringValue(facility.get("redAlertMessage")));
                stateRow.put("colorAlertMessage", stateService.stringValue(facility.get("colorAlertMessage")));
                stateService.getPublishFacilities().add(stateRow);
                stateFacilityById.put(deviceId, stateRow);
            }
            stateRow.put("postInformation", publishInfoByDeviceId.get(deviceId));
        }
    }

    private String stripPublishPrefix(String text) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("发布内容：")) {
            return value.substring("发布内容：".length()).trim();
        }
        return value;
    }

    private String resolveFirstDeviceIdBySegment(List<Map<String, Object>> items, String... keywords) {
        for (Map<String, Object> row : items) {
            String segment = stateService.stringValue(row.get("segment"));
            boolean matched = false;
            for (String keyword : keywords) {
                if (!keyword.isBlank() && segment.contains(keyword)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                continue;
            }
            String deviceId = firstNonBlank(row.get("deviceId"), row.get("facilityId"));
            if (!deviceId.isBlank()) {
                return deviceId;
            }
        }
        return "";
    }

    private String resolveFirstDeviceIdByType(List<Map<String, Object>> items, String targetType, String... fallbackKeywords) {
        for (Map<String, Object> row : items) {
            String rowType = stateService.stringValue(row.get("targetDeviceType"));
            if (!targetType.equals(rowType)) {
                continue;
            }
            String deviceId = firstNonBlank(row.get("deviceId"), row.get("facilityId"));
            if (!deviceId.isBlank()) {
                return deviceId;
            }
        }
        return resolveFirstDeviceIdBySegment(items, fallbackKeywords);
    }

    /**
     * 大屏图三：事件报告表格（按发生时间倒序，默认20条）。
     */
    public List<Map<String, Object>> listEventReportTableRows(Integer limit) {
        autoCloseExpiredPublishedPlans();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> source : stateService.getWindEventRecords()) {
            Map<String, Object> record = toWindEventViewRow(source);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eventId", stateService.stringValue(record.get("eventId")));
            row.put("eventLocation", resolveWindEventStakeLocation(source, record));
            row.put("windSpeedScale", stateService.stringValue(record.get("windSpeedScale")));
            row.put("direction", stateService.intValue(record.get("direction"), DIRECTION_HAMI));
            row.put("directionText", directionToText(stateService.intValue(record.get("direction"), DIRECTION_HAMI)));
            row.put("controlPlan", stateService.stringValue(record.get("managementPlan")));
            row.put("startTime", stateService.stringValue(record.get("timeOfOccurrence")));
            row.put("endTime", stateService.stringValue(record.get("conclusionTime")));
            row.put("controlPerimeter", stateService.stringValue(record.get("controlPerimeter")));
            row.put("onDutyPersonnel", stateService.stringValue(record.get("onDutyPersonnel")));
            rows.add(row);
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

    private String resolveWindEventStakeLocation(Map<String, Object> source, Map<String, Object> viewRow) {
        String fromStakeFields = buildEventLocation(
                stateService.stringValue(source.get("startStake")),
                stateService.stringValue(source.get("endStake"))
        );
        if (containsStake(fromStakeFields)) {
            return fromStakeFields;
        }
        String fromExistingLocation = extractStakeRangeText(stateService.stringValue(viewRow.get("incidentLocation")));
        if (!fromExistingLocation.isBlank()) {
            return fromExistingLocation;
        }
        String fromMappedLocation = mapSegmentTextToStakeRange(firstNonBlank(
                viewRow.get("controlPerimeter"),
                viewRow.get("segment"),
                source.get("controlPerimeter"),
                source.get("segmentText"),
                source.get("segment")
        ));
        if (!fromMappedLocation.isBlank()) {
            return fromMappedLocation;
        }
        String fromControlPerimeter = extractStakeRangeText(stateService.stringValue(viewRow.get("controlPerimeter")));
        if (!fromControlPerimeter.isBlank()) {
            return fromControlPerimeter;
        }
        String fromSegment = extractStakeRangeText(stateService.stringValue(viewRow.get("segment")));
        if (!fromSegment.isBlank()) {
            return fromSegment;
        }
        return stateService.stringValue(viewRow.get("incidentLocation"));
    }

    private String mapSegmentTextToStakeRange(String text) {
        String normalized = normalizeSegmentKey(text);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.contains("红山口服务区-一碗泉服务区")
                || normalized.contains("一碗泉服务区-红山口服务区")) {
            return "K3178-K3192";
        }
        if (normalized.contains("红山口互通-红山口服务区")
                || normalized.contains("红山口服务区-红山口互通")) {
            return "K3192-K3197";
        }
        if (normalized.contains("沙尔湖服务区-红山口互通")) {
            return "K3197-K3204";
        }
        if (normalized.contains("红山口互通-沙尔湖服务区")) {
            return "K3197-K3204";
        }
        return "";
    }

    private String normalizeSegmentKey(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("—", "-")
                .replace("至", "-")
                .replace("到", "-")
                .replace(" ", "")
                .trim();
    }

    private boolean containsStake(String text) {
        return text != null && stakePattern.matcher(text).find();
    }

    private String extractStakeRangeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = stakePattern.matcher(text);
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
        return stakes.get(0) + "-" + stakes.get(stakes.size() - 1);
    }

    private String[] parseIncidentLocationRange(String incidentLocation) {
        String[] result = new String[]{"", ""};
        if (incidentLocation == null || incidentLocation.isBlank()) {
            return result;
        }
        String normalized = incidentLocation.replace("—", "-")
                .replace("至", "-")
                .replace("到", "-")
                .replace(" ", "")
                .trim();
        String[] parts = normalized.split("-");
        if (parts.length >= 2) {
            result[0] = parts[0];
            result[1] = parts[1];
        } else {
            result[0] = normalized;
        }
        return result;
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
            case 5 -> "正常通行";
            default -> "未知";
        };
    }

    private FixedVmsContent resolveFixedVmsContent(int controlLevel, String vmsKind, String content) {
        String fallbackContent = resolveFallbackFixedVmsContent(controlLevel, vmsKind);
        String templateTipContent = resolveFixedVmsTemplateText(controlLevel, vmsKind, content);
        FixedVmsContent fallback = splitFixedVmsContent(fallbackContent);
        return new FixedVmsContent(
                fallback.mainContent(),
                templateTipContent.isBlank() ? fallback.tipContent() : templateTipContent
        );
    }

    private String resolveFallbackFixedVmsContent(int controlLevel, String vmsKind) {
        return switch (controlLevel) {
            case 1 -> switch (vmsKind) {
                case VMS_KIND_UPSTREAM_TOLLGATE -> "主线高速大风红色预警，车辆禁止驶入。\n入口提示：小型车禁行，大型车禁行。";
                case VMS_KIND_UPSTREAM_EXIT -> "前方大风，所有车辆靠右驶离高速。\n出口提示：小型车禁行，大型车禁行。";
                case VMS_KIND_INSIDE_SEGMENT -> "当前路段大风红色预警，车辆紧急避险。\n路段提示：小型车避险，大型车避险。";
                case VMS_KIND_UPSTREAM_SERVICE_AREA -> "当前路段大风红色预警，车辆服务区避险。\n服务区提示：小型车避险，大型车避险。";
                default -> "";
            };
            case 2 -> switch (vmsKind) {
                case VMS_KIND_UPSTREAM_TOLLGATE -> "主线高速大风橙色预警，小车预约大型车禁行。\n入口提示：车辆预约，小型车限速60，大型车禁行。";
                case VMS_KIND_UPSTREAM_EXIT -> "前方大风橙色预警，小车预约大车驶离高速。\n出口提示：车辆预约，小型车限速60，大型车禁行。";
                case VMS_KIND_INSIDE_SEGMENT -> "当前路段大风橙色预警，大车紧急避险。\n路段提示：小型车限速60，大型车避险。";
                case VMS_KIND_UPSTREAM_SERVICE_AREA -> "当前路段大风橙色预警，大车紧急避险。\n服务区提示：小型车限速60，大型车避险。";
                default -> "";
            };
            case 3 -> switch (vmsKind) {
                case VMS_KIND_UPSTREAM_TOLLGATE -> "主线高速大风黄色预警，仅预约车辆通行。\n入口提示：车辆预约，小型车限速60，大型车限速40。";
                case VMS_KIND_UPSTREAM_EXIT -> "前方大风黄色预警，未预约车辆驶离高速。\n出口提示：车辆预约，小型车限速60，大型车限速40。";
                case VMS_KIND_INSIDE_SEGMENT -> "当前路段大风黄色预警，请按限速行驶。\n路段提示：小型车限速60，大型车限速40。";
                case VMS_KIND_UPSTREAM_SERVICE_AREA -> "当前路段大风黄色预警，请按限速行驶。\n服务区提示：小型车限速60，大型车限速40。";
                default -> "";
            };
            case 4 -> switch (vmsKind) {
                case VMS_KIND_UPSTREAM_TOLLGATE -> "主线高速大风，请遵循指引安全驾驶。\n入口提示：小型车限速80，大型车限速60。";
                case VMS_KIND_UPSTREAM_EXIT -> "前方大风，请遵循指引安全驾驶。\n出口提示：小型车限速80，大型车限速60。";
                case VMS_KIND_INSIDE_SEGMENT -> "当前路段大风，请遵循指示安全驾驶。\n路段提示：小型车限速80，大型车限速60。";
                case VMS_KIND_UPSTREAM_SERVICE_AREA -> "当前路段大风，请遵循指示安全驾驶。\n服务区提示：小型车限速80，大型车限速60。";
                default -> "";
            };
            case 5 -> GREEN_ALERT_FIXED_VMS_CONTENT;
            default -> "";
        };
    }

    private FixedVmsContent splitFixedVmsContent(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return new FixedVmsContent("", "");
        }
        String[] lines = value.split("\\R", 2);
        return new FixedVmsContent(lines[0].trim(), lines.length > 1 ? lines[1].trim() : "");
    }

    private String resolveFixedVmsTemplateText(int controlLevel, String vmsKind, String content) {
        String controlLevelText = levelToText(controlLevel);
        String publishPosition = resolveTemplatePublishPosition(vmsKind);
        if (controlLevelText.isBlank() || publishPosition.isBlank()) {
            return "";
        }
        VmsContentTemplateStatic row = vmsContentTemplateStaticService.matchTemplate(controlLevelText, publishPosition, "ALL");
        if (row == null) {
            row = vmsContentTemplateStaticService.matchTemplate(resolveControlLevelName(controlLevel), publishPosition, "ALL");
        }
        if (row == null) {
            row = matchTemplateByLevelCandidates(controlLevel, publishPosition);
        }
        if (row == null || row.getTemplateText() == null || row.getTemplateText().isBlank()) {
            return "";
        }
        return renderFixedVmsTemplateText(row.getTemplateText(), controlLevel, content);
    }

    private String renderFixedVmsTemplateText(String templateText, int controlLevel, String content) {
        String text = templateText == null ? "" : templateText;
        String passengerSpeed = resolvePlanSpeedLimit(controlLevel, "passengerSpeedLimit", defaultPassengerLimitByControlLevel(controlLevel));
        String freightSpeed = resolvePlanSpeedLimit(controlLevel, "freightSpeedLimit", defaultFreightLimitByControlLevel(controlLevel));
        String rendered = text.replace("{LIGHT_SPEED}", passengerSpeed)
                .replace("${LIGHT_SPEED}", passengerSpeed)
                .replace("{HEAVY_SPEED}", freightSpeed)
                .replace("${HEAVY_SPEED}", freightSpeed);
        rendered = alignVehicleControlByContent(rendered, content, "小型车", "小车");
        rendered = alignVehicleControlByContent(rendered, content, "大型车", "大车");
        rendered = removeReservationWhenAllVehiclesForbidden(rendered, content);
        return rendered.trim();
    }

    private String removeReservationWhenAllVehiclesForbidden(String text, String content) {
        String passengerControl = resolveVehicleControlFromContent(content, "小型车", "小车");
        String freightControl = resolveVehicleControlFromContent(content, "大型车", "大车");
        if (!"禁行".equals(passengerControl) || !"禁行".equals(freightControl)) {
            return text;
        }
        return text.replace("车辆预约，", "")
                .replace("车辆预约,", "")
                .replace("，车辆预约", "")
                .replace(",车辆预约", "")
                .replace("车辆预约", "");
    }

    private String alignVehicleControlByContent(String templateText, String content, String standardName, String alias) {
        String control = resolveVehicleControlFromContent(content, standardName, alias);
        if (control.isBlank()) {
            return templateText;
        }
        String result = replaceVehicleControlInText(templateText, standardName, control);
        if (alias != null && !alias.isBlank()) {
            result = replaceVehicleControlInText(result, alias, control);
        }
        return result;
    }

    private String resolveVehicleControlFromContent(String content, String standardName, String alias) {
        String text = content == null ? "" : content.trim();
        if (text.isBlank()) {
            return "";
        }
        if (text.contains("所有车辆禁行")) {
            return "禁行";
        }
        if (text.contains("所有车辆避险")) {
            return "避险";
        }
        String speed = extractVehicleSpeed(text, standardName);
        if (speed.isBlank() && alias != null && !alias.isBlank()) {
            speed = extractVehicleSpeed(text, alias);
        }
        if (!speed.isBlank()) {
            return "限速" + speed;
        }
        if (text.contains(standardName + "禁行")
                || (alias != null && !alias.isBlank() && text.contains(alias + "禁行"))) {
            return "禁行";
        }
        if (text.contains(standardName + "避险")
                || (alias != null && !alias.isBlank() && text.contains(alias + "避险"))) {
            return "避险";
        }
        return "";
    }

    private String extractVehicleSpeed(String text, String vehicleName) {
        if (text == null || vehicleName == null || vehicleName.isBlank()) {
            return "";
        }
        Pattern pattern = Pattern.compile(Pattern.quote(vehicleName) + "\\s*限速\\s*(\\d+)(?:\\s*km/h)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String replaceVehicleControlInText(String text, String vehicleName, String control) {
        if (text == null || vehicleName == null || vehicleName.isBlank() || control == null || control.isBlank()) {
            return text == null ? "" : text;
        }
        Pattern pattern = Pattern.compile("(" + Pattern.quote(vehicleName) + "\\s*)(?:限速\\s*\\d+|禁行|避险)");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + control));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String resolvePlanSpeedLimit(int controlLevel, String field, int defaultValue) {
        Map<String, Object> plan = stateService.getControlPlanLibrary().get(controlLevel);
        Integer value = positiveOrZeroInt(plan == null ? null : plan.get(field));
        if (value != null) {
            return String.valueOf(value);
        }
        for (Map<String, Object> threshold : stateService.getSpeedThresholdByWindLevel().values()) {
            if (threshold == null) {
                continue;
            }
            int thresholdLevel = stateService.intValue(threshold.get("controlLevel"), -1);
            if (thresholdLevel != controlLevel) {
                continue;
            }
            value = positiveOrZeroInt(threshold.get(field));
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(defaultValue);
    }

    private Integer positiveOrZeroInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue() >= 0 ? n.intValue() : null;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(text);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int defaultPassengerLimitByControlLevel(int controlLevel) {
        return switch (controlLevel) {
            case 1 -> 0;
            case 2 -> 60;
            case 3 -> 60;
            case 4 -> 80;
            default -> 120;
        };
    }

    private int defaultFreightLimitByControlLevel(int controlLevel) {
        return switch (controlLevel) {
            case 1 -> 0;
            case 2 -> 0;
            case 3 -> 40;
            case 4 -> 60;
            default -> 80;
        };
    }

    private VmsContentTemplateStatic matchTemplateByLevelCandidates(int controlLevel, String publishPosition) {
        for (String levelName : resolveTemplateLevelCandidates(controlLevel)) {
            for (VmsContentTemplateStatic row : vmsContentTemplateStaticService.listByControlLevel(levelName, 1)) {
                if (publishPosition.equalsIgnoreCase(row.getPublishPosition())
                        && "ALL".equalsIgnoreCase(row.getVehicleType())) {
                    return row;
                }
            }
        }
        return null;
    }

    private List<String> resolveTemplateLevelCandidates(int controlLevel) {
        return switch (controlLevel) {
            case 1 -> List.of("红色警戒", "一级管控", "一级");
            case 2 -> List.of("橙色警戒", "二级管控", "二级");
            case 3 -> List.of("黄色警戒", "三级管控", "三级");
            case 4 -> List.of("蓝色警戒", "四级管控", "四级");
            case 5 -> List.of("绿色警戒", "正常通行", "五级管控", "五级");
            default -> List.of();
        };
    }

    private String resolveTemplatePublishPosition(String vmsKind) {
        return switch (vmsKind) {
            case VMS_KIND_INSIDE_SEGMENT -> "IN_SECTION";
            case VMS_KIND_UPSTREAM_EXIT -> "UPSTREAM_EXIT";
            case VMS_KIND_UPSTREAM_TOLLGATE -> "UPSTREAM_ENTRY_TOLL";
            case VMS_KIND_UPSTREAM_SERVICE_AREA -> "SERVICE_AREA";
            default -> "";
        };
    }

    private String resolveControlLevelName(int controlLevel) {
        return switch (controlLevel) {
            case 1 -> "一级管控";
            case 2 -> "二级管控";
            case 3 -> "三级管控";
            case 4 -> "四级管控";
            case 5 -> "五级管控";
            default -> "";
        };
    }

    /**
     * segmentText 严格使用固定区间文案（双向共六个），来源 control_interval_static.interval_name。
     */
    private String resolveFixedSegmentText(Map<String, Object> interval, int direction) {
        String segmentText = resolveDashboardSegmentText(interval, direction);
        if (!segmentText.isBlank()) {
            return segmentText;
        }
        String directionText = direction == DIRECTION_TURPAN ? "吐鲁番方向" : "哈密方向";
        throw new IllegalArgumentException("segmentText must map to one of the fixed 6 intervals in control_interval_static: " + directionText);
    }

    /**
     * 推导管控事件文案（预约/限速/限行/封路）。
     */
    private String resolveControlEventText(Map<String, Object> template, int level) {
        String riskSectionPlan = materializePlanText(
                stateService.stringValue(template.get("riskSectionPlan")),
                stateService.stringValue(template.get("riskSectionPlan"))
        );
        String upstreamEntryPlan = materializePlanText(
                stateService.stringValue(template.get("upstreamEntryPlan")),
                riskSectionPlan
        );
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
     * 解析可空 int 值。
     */
    private Integer toNullableInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return null;
        }
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

        String querySegmentKey = normalizeSegmentLookupKey(segment);
        for (Map<String, Object> row : stateService.getDispatchPlanLibrary().values()) {
            int rowDirection = stateService.intValue(row.get("direction"), -1);
            if (rowDirection != direction) {
                continue;
            }
            String rowDisplayKey = normalizeSegmentLookupKey(resolveDashboardSegmentText(row, direction));
            if (!querySegmentKey.isBlank() && querySegmentKey.equals(rowDisplayKey)) {
                return new LinkedHashMap<>(row);
            }
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

    private String normalizeSegmentLookupKey(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(" ", "")
                .replace("（", "(")
                .replace("）", ")")
                .replace("区段", "")
                .trim();
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
     * 生成设施级 VMS 发布列表。文案仅来自 4.4.2 预案库当前等级。
     */
    private List<Map<String, Object>> buildVmsPublishItems(int direction,
                                                             String nearestInterchangeStake,
                                                             String segmentText,
                                                             String intervalName,
                                                             String startStake,
                                                             String endStake,
                                                             String vmsInsideSegment,
                                                             String vmsUpstreamExit,
                                                             String vmsUpstreamTollgate,
                                                             String vmsUpstreamServiceArea,
                                                             boolean includeUpstreamServiceArea) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sendStakeRange = resolveExecutionSendStakeRange(intervalName, startStake, endStake);
        double[] sendRange = parseStakeRange(sendStakeRange);
        if (sendRange != null) {
            addExecutionVmsDeviceRow(rows, direction, intervalName, sendStakeRange, sendRange,
                    segmentText, VMS_KIND_INSIDE_SEGMENT, "管控区间内VMS", vmsInsideSegment);
            addExecutionVmsDeviceRow(rows, direction, intervalName, sendStakeRange, sendRange,
                    segmentText, VMS_KIND_UPSTREAM_EXIT, "管控区间上游互通出口VMS", vmsUpstreamExit);
            addExecutionVmsDeviceRow(rows, direction, intervalName, sendStakeRange, sendRange,
                    segmentText, VMS_KIND_UPSTREAM_TOLLGATE, "管控区间上游互通入口收费站VMS", vmsUpstreamTollgate);
            addExecutionVmsDeviceRow(rows, direction, intervalName, sendStakeRange, sendRange,
                    segmentText, VMS_KIND_UPSTREAM_SERVICE_AREA, "服务区前VMS", vmsUpstreamServiceArea);
        }
        if (!rows.isEmpty()) {
            return rows;
        }

        List<Map<String, Object>> facilities = getPublishFacilitiesForExecution();
        String targetInterchangeName = extractFirstInterchangeName(segmentText);
        String normalizedTargetInterchange = normalizeTextForMatch(targetInterchangeName);
        String normalizedNearestStake = normalizeStakeForMatch(nearestInterchangeStake);
        for (Map<String, Object> facility : facilities) {
            int facilityDirection = stateService.intValue(facility.get("direction"), -1);
            if (facilityDirection != direction) {
                continue;
            }
            String facilitySegment = stateService.stringValue(facility.get("segment"));
            String facilityInterchangeStake = stateService.stringValue(facility.get("interchangeStake"));
            String facilityInterchangeName = stateService.stringValue(facility.get("interchangeName"));
            String normalizedFacilityInterchangeName = normalizeTextForMatch(facilityInterchangeName);
            String normalizedFacilitySegment = normalizeTextForMatch(facilitySegment);
            String normalizedFacilityInterchangeStake = normalizeStakeForMatch(facilityInterchangeStake);

            boolean inNearestInterchange = false;
            if (!normalizedNearestStake.isBlank()) {
                inNearestInterchange = normalizedNearestStake.equalsIgnoreCase(normalizedFacilityInterchangeStake);
            }
            if (!inNearestInterchange && !normalizedTargetInterchange.isBlank() && isInterchangeFacility(facility)) {
                inNearestInterchange = normalizedFacilityInterchangeName.contains(normalizedTargetInterchange)
                        || normalizedFacilitySegment.contains(normalizedTargetInterchange);
            }

            boolean inUpstreamServiceArea = includeUpstreamServiceArea
                    && isServiceAreaFacilityForSegment(facilitySegment, segmentText);
            if (!inNearestInterchange && !inUpstreamServiceArea) {
                continue;
            }

            String message;
            if (inUpstreamServiceArea && !inNearestInterchange) {
                message = vmsUpstreamServiceArea;
            } else if (facilitySegment.contains("入口") || facilitySegment.contains("收费站")) {
                message = vmsUpstreamTollgate;
            } else if (facilitySegment.contains("出口")) {
                message = vmsUpstreamExit;
            } else if (facilitySegment.contains("区段")) {
                message = vmsInsideSegment;
            } else {
                message = vmsUpstreamExit;
            }
            if (message == null || message.isBlank()) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("facilityId", stateService.stringValue(facility.get("facilityId")));
            row.put("deviceId", stateService.stringValue(facility.get("facilityId")));
            row.put("pileNo", stateService.stringValue(facility.get("pileNo")));
            row.put("segment", facilitySegment);
            row.put("interchangeName", facilityInterchangeName);
            row.put("publishMessage", message);
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            return rows;
        }

        // 兜底：若静态数据命名不规范导致未命中，按方向取“首个互通”设备，避免发布表为空。
        Map<String, Object> firstInterchangeFacility = null;
        for (Map<String, Object> facility : facilities) {
            int facilityDirection = stateService.intValue(facility.get("direction"), -1);
            if (facilityDirection != direction || !isInterchangeFacility(facility)) {
                continue;
            }
            firstInterchangeFacility = facility;
            break;
        }
        if (firstInterchangeFacility == null) {
            return rows;
        }
        for (Map<String, Object> facility : facilities) {
            int facilityDirection = stateService.intValue(facility.get("direction"), -1);
            if (facilityDirection != direction || !isInterchangeFacility(facility)) {
                continue;
            }
            if (!isSameInterchangeFacility(facility, firstInterchangeFacility)) {
                continue;
            }
            String facilitySegment = stateService.stringValue(facility.get("segment"));
            String message;
            if (facilitySegment.contains("入口") || facilitySegment.contains("收费站")) {
                message = vmsUpstreamTollgate;
            } else if (facilitySegment.contains("出口")) {
                message = vmsUpstreamExit;
            } else {
                message = vmsUpstreamExit;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("facilityId", stateService.stringValue(facility.get("facilityId")));
            row.put("deviceId", stateService.stringValue(facility.get("facilityId")));
            row.put("pileNo", stateService.stringValue(facility.get("pileNo")));
            row.put("segment", facilitySegment);
            row.put("interchangeName", stateService.stringValue(facility.get("interchangeName")));
            row.put("publishMessage", message);
            rows.add(row);
        }
        return rows;
    }

    private void addExecutionVmsDeviceRow(List<Map<String, Object>> rows,
                                          int direction,
                                          String intervalName,
                                          String sendStakeRange,
                                          double[] sendRange,
                                          String segmentText,
                                          String deviceType,
                                          String target,
                                          String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        ExecutionVmsDevice device = resolveExecutionVmsDevice(direction, intervalName, sendStakeRange, sendRange, segmentText, deviceType);
        if (device == null) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("facilityId", device.deviceId());
        row.put("deviceId", device.deviceId());
        row.put("pileNo", device.stake());
        row.put("stake", device.stake());
        row.put("segment", target);
        row.put("target", target);
        row.put("targetDeviceType", deviceType);
        row.put("publishMessage", message);
        rows.add(row);
    }

    private ExecutionVmsDevice resolveExecutionVmsDevice(int direction,
                                                         String intervalName,
                                                         String sendStakeRange,
                                                         double[] sendRange,
                                                         String segmentText,
                                                         String deviceType) {
        String line = resolveExecutionVmsLine(direction, intervalName);
        List<ExecutionVmsDevice> sourceDevices = resolveExecutionVmsDevicesFromRuntimeFacilities();
        if (sourceDevices.isEmpty()) {
            sourceDevices = EXECUTION_VMS_DEVICES;
        }
        List<ExecutionVmsDevice> candidates = sourceDevices.stream()
                .filter(device -> line.equals(device.line()))
                .filter(device -> deviceType.equals(device.type()))
                .filter(device -> parseStakeValue(device.stake()) != null)
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        ExecutionVmsDevice semanticDevice = selectExecutionDeviceByBusinessText(candidates, deviceType, segmentText);
        if (semanticDevice != null) {
            return semanticDevice;
        }
        boolean upstreamLowerSide = isExecutionUpstreamLowerSide(sendStakeRange);
        if (VMS_KIND_INSIDE_SEGMENT.equals(deviceType)) {
            ExecutionVmsDevice inside = candidates.stream()
                    .filter(device -> isExecutionStakeInside(device, sendRange))
                    .min((left, right) -> compareExecutionInsideDevice(left, right, upstreamLowerSide))
                    .orElse(null);
            if (inside != null) {
                return inside;
            }
        }
        return candidates.stream()
                .filter(device -> isExecutionUpstreamDevice(device, sendRange, upstreamLowerSide))
                .min((left, right) -> compareExecutionUpstreamDevice(left, right, upstreamLowerSide))
                .orElseGet(() -> candidates.stream()
                        .min(Comparator.comparingDouble(device -> executionStakeDistance(device, sendRange)))
                        .orElse(null));
    }

    private List<ExecutionVmsDevice> resolveExecutionVmsDevicesFromRuntimeFacilities() {
        List<ExecutionVmsDevice> rows = new ArrayList<>();
        for (Map<String, Object> facility : getPublishFacilitiesForExecution()) {
            String deviceId = stateService.stringValue(facility.get("facilityId"));
            String stake = stateService.stringValue(facility.get("pileNo"));
            int direction = stateService.intValue(facility.get("direction"), -1);
            String segment = stateService.stringValue(facility.get("segment"));
            String interchangeName = stateService.stringValue(facility.get("interchangeName"));
            String type = inferExecutionVmsType(deviceId, segment);
            if (deviceId.isBlank() || stake.isBlank() || type.isBlank()) {
                continue;
            }
            String line = direction == DIRECTION_TURPAN ? VMS_LINE_TURPAN : VMS_LINE_HAMI;
            rows.add(new ExecutionVmsDevice(line, deviceId, stake, type, segment, interchangeName));
        }
        return rows;
    }

    private ExecutionVmsDevice selectExecutionDeviceByBusinessText(List<ExecutionVmsDevice> candidates,
                                                                   String deviceType,
                                                                   String segmentText) {
        String segmentKey = normalizeSegmentLookupKey(segmentText);
        if (segmentKey.isBlank()) {
            return null;
        }
        if (VMS_KIND_INSIDE_SEGMENT.equals(deviceType)) {
            return candidates.stream()
                    .filter(device -> normalizedDeviceSegment(device).contains(segmentKey)
                            || segmentKey.contains(normalizedDeviceSegment(device)))
                    .findFirst()
                    .orElse(null);
        }
        String interchangeName = extractFirstInterchangeName(segmentText);
        if ((VMS_KIND_UPSTREAM_EXIT.equals(deviceType) || VMS_KIND_UPSTREAM_TOLLGATE.equals(deviceType))
                && !interchangeName.isBlank()) {
            String interchangeKey = normalizeSegmentLookupKey(interchangeName);
            return candidates.stream()
                    .filter(device -> normalizedDeviceInterchange(device).contains(interchangeKey)
                            || normalizedDeviceSegment(device).contains(interchangeKey))
                    .findFirst()
                    .orElse(null);
        }
        if (VMS_KIND_UPSTREAM_SERVICE_AREA.equals(deviceType)) {
            String serviceAreaName = extractLastServiceAreaName(segmentText);
            if (!serviceAreaName.isBlank()) {
                String serviceAreaKey = normalizeSegmentLookupKey(serviceAreaName);
                return candidates.stream()
                        .filter(device -> normalizedDeviceSegment(device).contains(serviceAreaKey))
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }

    private String normalizedDeviceSegment(ExecutionVmsDevice device) {
        return normalizeSegmentLookupKey(device.segment());
    }

    private String normalizedDeviceInterchange(ExecutionVmsDevice device) {
        return normalizeSegmentLookupKey(device.interchangeName());
    }

    private String extractLastServiceAreaName(String text) {
        String value = stateService.stringValue(text);
        String result = "";
        Matcher matcher = Pattern.compile("[\\u4e00-\\u9fa5]+服务区").matcher(value);
        while (matcher.find()) {
            result = matcher.group();
        }
        return result;
    }

    private String inferExecutionVmsType(String deviceId, String segment) {
        String id = stateService.stringValue(deviceId).toUpperCase(Locale.ROOT);
        if (id.endsWith("F")) {
            return VMS_KIND_UPSTREAM_SERVICE_AREA;
        }
        if (id.endsWith("C")) {
            return VMS_KIND_UPSTREAM_EXIT;
        }
        if (id.endsWith("R")) {
            return VMS_KIND_UPSTREAM_TOLLGATE;
        }
        String text = stateService.stringValue(segment);
        if (text.contains("服务区前") || text.contains("服务区入口")) {
            return VMS_KIND_UPSTREAM_SERVICE_AREA;
        }
        if (text.contains("出口")) {
            return VMS_KIND_UPSTREAM_EXIT;
        }
        if (text.contains("入口") || text.contains("收费站")) {
            return VMS_KIND_UPSTREAM_TOLLGATE;
        }
        return VMS_KIND_INSIDE_SEGMENT;
    }

    private String resolveExecutionVmsLine(int direction, String intervalName) {
        String interval = stateService.stringValue(intervalName);
        if (interval.startsWith("2-")) {
            return VMS_LINE_HAMI;
        }
        if (interval.startsWith("1-")) {
            return VMS_LINE_TURPAN;
        }
        return direction == DIRECTION_TURPAN ? VMS_LINE_TURPAN : VMS_LINE_HAMI;
    }

    private String resolveExecutionSendStakeRange(String intervalName, String startStake, String endStake) {
        String interval = stateService.stringValue(intervalName);
        if ("1-1".equals(interval)) {
            return "K3192-K3178";
        }
        if ("1-2".equals(interval)) {
            return "K3197-K3193";
        }
        if ("1-3".equals(interval)) {
            return "K3203-K3198";
        }
        if ("2-1".equals(interval)) {
            return "K3191-K3178";
        }
        if ("2-2".equals(interval)) {
            return "K3196-K3192";
        }
        if ("2-3".equals(interval)) {
            return "K3203-K3197";
        }
        String resolvedStart = firstNonBlank(startStake, extractStake(resolveSpatiotemporalStakeRange(Map.of("intervalName", interval)), true));
        String resolvedEnd = firstNonBlank(endStake, extractStake(resolveSpatiotemporalStakeRange(Map.of("intervalName", interval)), false));
        if (resolvedStart.isBlank() || resolvedEnd.isBlank()) {
            return "";
        }
        return resolvedStart + "-" + resolvedEnd;
    }

    private double[] parseStakeRange(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = stakePattern.matcher(text.toUpperCase(Locale.ROOT));
        List<Double> values = new ArrayList<>();
        while (matcher.find()) {
            Double value = parseStakeValue("K" + matcher.group(1));
            if (value != null) {
                values.add(value);
            }
        }
        if (values.size() < 2) {
            return null;
        }
        return new double[]{Math.min(values.get(0), values.get(1)), Math.max(values.get(0), values.get(1))};
    }

    private boolean isExecutionUpstreamLowerSide(String sendStakeRange) {
        List<Double> values = orderedStakeValues(sendStakeRange);
        if (values.size() < 2) {
            return true;
        }
        return values.get(0) < values.get(1);
    }

    private List<Double> orderedStakeValues(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Matcher matcher = stakePattern.matcher(text.toUpperCase(Locale.ROOT));
        List<Double> values = new ArrayList<>();
        while (matcher.find()) {
            Double value = parseStakeValue("K" + matcher.group(1));
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private boolean isExecutionStakeInside(ExecutionVmsDevice device, double[] sendRange) {
        Double value = parseStakeValue(device.stake());
        return value != null && value >= sendRange[0] && value <= sendRange[1];
    }

    private boolean isExecutionUpstreamDevice(ExecutionVmsDevice device, double[] sendRange, boolean upstreamLowerSide) {
        Double value = parseStakeValue(device.stake());
        if (value == null) {
            return false;
        }
        return upstreamLowerSide ? value < sendRange[0] : value > sendRange[1];
    }

    private int compareExecutionInsideDevice(ExecutionVmsDevice left, ExecutionVmsDevice right, boolean upstreamLowerSide) {
        Double leftValue = parseStakeValue(left.stake());
        Double rightValue = parseStakeValue(right.stake());
        if (leftValue == null || rightValue == null) {
            return 0;
        }
        return upstreamLowerSide ? Double.compare(leftValue, rightValue) : Double.compare(rightValue, leftValue);
    }

    private int compareExecutionUpstreamDevice(ExecutionVmsDevice left, ExecutionVmsDevice right, boolean upstreamLowerSide) {
        Double leftValue = parseStakeValue(left.stake());
        Double rightValue = parseStakeValue(right.stake());
        if (leftValue == null || rightValue == null) {
            return 0;
        }
        return upstreamLowerSide ? Double.compare(rightValue, leftValue) : Double.compare(leftValue, rightValue);
    }

    private double executionStakeDistance(ExecutionVmsDevice device, double[] sendRange) {
        Double value = parseStakeValue(device.stake());
        if (value == null) {
            return Double.MAX_VALUE;
        }
        if (value < sendRange[0]) {
            return sendRange[0] - value;
        }
        if (value > sendRange[1]) {
            return value - sendRange[1];
        }
        return 0D;
    }

    private boolean isInterchangeFacility(Map<String, Object> facility) {
        String segment = stateService.stringValue(facility.get("segment"));
        String interchangeName = stateService.stringValue(facility.get("interchangeName"));
        String interchangeStake = stateService.stringValue(facility.get("interchangeStake"));
        return segment.contains("互通") || interchangeName.contains("互通") || !interchangeStake.isBlank();
    }

    private boolean isSameInterchangeFacility(Map<String, Object> left, Map<String, Object> right) {
        String leftStake = normalizeStakeForMatch(stateService.stringValue(left.get("interchangeStake")));
        String rightStake = normalizeStakeForMatch(stateService.stringValue(right.get("interchangeStake")));
        if (!leftStake.isBlank() && !rightStake.isBlank()) {
            return leftStake.equalsIgnoreCase(rightStake);
        }
        String leftName = normalizeTextForMatch(stateService.stringValue(left.get("interchangeName")));
        String rightName = normalizeTextForMatch(stateService.stringValue(right.get("interchangeName")));
        if (!leftName.isBlank() && !rightName.isBlank()) {
            return leftName.equalsIgnoreCase(rightName);
        }
        String leftSegment = normalizeTextForMatch(stateService.stringValue(left.get("segment")));
        String rightSegment = normalizeTextForMatch(stateService.stringValue(right.get("segment")));
        if (leftSegment.isBlank() || rightSegment.isBlank()) {
            return false;
        }
        return leftSegment.contains(rightSegment) || rightSegment.contains(leftSegment);
    }

    private String normalizeTextForMatch(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(" ", "")
                .replace("区段", "")
                .trim();
    }

    private String normalizeStakeForMatch(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private Map<String, String> resolvePlanVmsTexts(Map<String, Object> template,
                                                     String segmentText,
                                                     boolean includeUpstreamServiceArea) {
        String vmsInsideSegment = materializePlanText(
                stateService.stringValue(template.get("riskSectionPlan")),
                stateService.stringValue(template.get("riskSectionPlan"))
        );
        String vmsUpstreamExit = materializePlanText(
                stateService.stringValue(template.get("upstreamExitPlan")),
                vmsInsideSegment
        );
        String vmsUpstreamTollgate = materializePlanText(
                stateService.stringValue(template.get("upstreamEntryPlan")),
                vmsInsideSegment
        );
        String vmsUpstreamServiceArea = includeUpstreamServiceArea
                ? materializePlanText(stateService.stringValue(template.get("upstreamServiceAreaPlan")), vmsInsideSegment)
                : buildServiceAreaWelcomeText(segmentText);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("vmsInsideSegment", vmsInsideSegment);
        result.put("vmsUpstreamExit", vmsUpstreamExit);
        result.put("vmsUpstreamTollgate", vmsUpstreamTollgate);
        result.put("vmsUpstreamServiceArea", vmsUpstreamServiceArea);
        result.put("vmsContent", buildVmsContent(vmsInsideSegment, vmsUpstreamExit, vmsUpstreamTollgate, vmsUpstreamServiceArea));
        return result;
    }

    private String materializePlanText(String rawText, String riskSectionPlan) {
        String raw = rawText == null ? "" : rawText.trim();
        if (raw.isBlank()) {
            return raw;
        }
        String riskText = riskSectionPlan == null ? "" : riskSectionPlan.trim();
        if (raw.contains(SAME_RISK_PLACEHOLDER) && !riskText.isBlank()) {
            return raw.replace(SAME_RISK_PLACEHOLDER, riskText);
        }
        return raw;
    }

    private boolean isServiceAreaFacilityForSegment(String facilitySegment, String segmentText) {
        if (facilitySegment == null || facilitySegment.isBlank() || segmentText == null || segmentText.isBlank()) {
            return false;
        }
        String serviceName = extractFirstServiceAreaName(segmentText);
        if (!serviceName.isBlank() && facilitySegment.contains(serviceName)) {
            return true;
        }
        return facilitySegment.contains("服务区前") || facilitySegment.contains("服务区入口");
    }

    private String extractFirstServiceAreaName(String segmentText) {
        String[] tokens = segmentText.split("-");
        for (String token : tokens) {
            String value = token.trim();
            if (value.contains("服务区")) {
                return value;
            }
        }
        return "";
    }

    private String extractFirstInterchangeName(String segmentText) {
        String[] tokens = segmentText.split("-");
        for (String token : tokens) {
            String value = token.trim();
            if (value.contains("互通")) {
                return value;
            }
        }
        return "";
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

    private boolean shouldIncludeUpstreamServiceArea(boolean upstreamInterchangeFirst) {
        // 需求约束：上游为互通时仅发布互通内容；上游为服务区前时需同时发布服务区与互通内容。
        return !upstreamInterchangeFirst;
    }

    private boolean isUpstreamInterchangeFirst(Map<String, Object> interval, String segmentText, int direction) {
        String text = stateService.stringValue(segmentText).trim();
        if (!text.isBlank() && text.contains("-")) {
            String[] tokens = text.split("-");
            if (tokens.length >= 2) {
                String left = tokens[0].trim();
                String right = tokens[tokens.length - 1].trim();
                if (direction == DIRECTION_HAMI) {
                    if (left.contains("服务区")) {
                        return false;
                    }
                    if (left.contains("互通")) {
                        return true;
                    }
                    if (right.contains("服务区")) {
                        return false;
                    }
                    if (right.contains("互通")) {
                        return true;
                    }
                } else {
                    if (right.contains("服务区")) {
                        return false;
                    }
                    if (right.contains("互通")) {
                        return true;
                    }
                    if (left.contains("服务区")) {
                        return false;
                    }
                    if (left.contains("互通")) {
                        return true;
                    }
                }
            }
        }
        if (text.contains("服务区") && !text.contains("互通")) {
            return false;
        }
        if (text.contains("互通") && !text.contains("服务区")) {
            return true;
        }
        return Boolean.TRUE.equals(interval.get("hasInterchange"));
    }

    private boolean isUpstreamServiceAreaFacility(String facilitySegment) {
        String segment = facilitySegment == null ? "" : facilitySegment;
        return segment.contains("服务区前") || segment.contains("服务区入口");
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

    private int resolveConfiguredControlLevel(int windLevel) {
        if (windLevel <= 0) {
            return stateService.getDefaultControlLevel();
        }
        Map<String, Object> threshold = stateService.getSpeedThresholdByWindLevel().get(windLevel);
        if (threshold == null) {
            return stateService.mapWindToControlLevel(windLevel);
        }
        int controlLevel = stateService.intValue(threshold.get("controlLevel"), -1);
        return controlLevel >= 1 && controlLevel <= 5
                ? controlLevel
                : stateService.mapWindToControlLevel(windLevel);
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
        Double rowStartValue = parseStakeValue(rowStartStake);
        Double rowEndValue = parseStakeValue(rowEndStake);
        Double sectionStartValue = parseStakeValue(sectionStartStake);
        Double sectionEndValue = parseStakeValue(sectionEndStake);
        if (rowStartValue != null && rowEndValue != null && sectionStartValue != null && sectionEndValue != null) {
            double rowMin = Math.min(rowStartValue, rowEndValue);
            double rowMax = Math.max(rowStartValue, rowEndValue);
            double sectionMin = Math.min(sectionStartValue, sectionEndValue);
            double sectionMax = Math.max(sectionStartValue, sectionEndValue);
            return !(rowMax < sectionMin || rowMin > sectionMax);
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

    private Map<String, Object> buildEventReportRecord(Map<String, Object> plan, long closeTs) {
        Map<String, Object> report = new LinkedHashMap<>();
        String eventId = stateService.stringValue(plan.get("eventId"));
        if (eventId.isBlank()) {
            eventId = "EVT-" + UUID.randomUUID().toString().substring(0, 6);
            plan.put("eventId", eventId);
        }
        long startTs = longValue(plan.get("timestamp"), closeTs);
        long conclusionTs = startTs + WINDOW_2H_MS;
        int controlLevel = stateService.intValue(plan.get("recommendedControlLevel"), stateService.getDefaultControlLevel());
        int maxWindLevel = Math.max(
                stateService.intValue(plan.get("realtimeWindLevel"), 0),
                stateService.intValue(plan.get("forecastMaxWindLevel"), 0)
        );
        String incidentLocation = buildEventLocation(
                stateService.stringValue(plan.get("startStake")),
                stateService.stringValue(plan.get("endStake"))
        );
        String managementPlan = normalizeManagementPlanText(
                firstNonBlank(plan.get("controlEventText"), plan.get("managementPlan")),
                controlLevel
        );
        String controlPerimeter = firstNonBlank(plan.get("segmentText"), plan.get("segment"));
        String onDutyPersonnel = resolveOnDutyPersonnel(plan);
        report.put("eventId", eventId);
        report.put("planId", stateService.stringValue(plan.get("planId")));
        report.put("startTime", dtf.format(Instant.ofEpochMilli(startTs)));
        report.put("endTime", dtf.format(Instant.ofEpochMilli(conclusionTs)));
        report.put("segment", stateService.stringValue(plan.get("segment")));
        report.put("startStake", stateService.stringValue(plan.get("startStake")));
        report.put("endStake", stateService.stringValue(plan.get("endStake")));
        report.put("direction", normalizeDirectionValue(stateService.intValue(plan.get("direction"), DIRECTION_HAMI), DIRECTION_HAMI));
        report.put("controlPlan", managementPlan);
        report.put("incidentLocation", incidentLocation);
        report.put("windSpeedScale", formatWindSpeedScale(maxWindLevel));
        report.put("managementPlan", managementPlan);
        report.put("timeOfOccurrence", dtf.format(Instant.ofEpochMilli(startTs)));
        report.put("conclusionTime", dtf.format(Instant.ofEpochMilli(conclusionTs)));
        report.put("controlPerimeter", controlPerimeter);
        report.put("onDutyPersonnel", onDutyPersonnel);
        report.put("maxWindLevel", maxWindLevel);
        report.put("controlLevel", controlLevel);
        report.put("durationMin", (int) (WINDOW_2H_MS / 60_000L));
        report.put("status", "FINISHED");
        return report;
    }

    private Map<String, Object> buildRunningEventRecord(Map<String, Object> plan, long publishTs) {
        Map<String, Object> report = buildEventReportRecord(plan, publishTs);
        report.put("durationMin", 0);
        report.put("status", "RUNNING");
        return report;
    }

    private String plannedEventConclusionTime(Object startTimeObj, long fallbackStartTs) {
        LocalDateTime startTime = parseDateTime(stateService.stringValue(startTimeObj));
        long startTs = startTime == null
                ? fallbackStartTs
                : startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return dtf.format(Instant.ofEpochMilli(startTs + WINDOW_2H_MS));
    }

    private Map<String, Object> ensureRunningEventRecord(Map<String, Object> plan, long publishTs) {
        if (plan == null) {
            return new LinkedHashMap<>();
        }
        String eventId = newEventId();
        plan.put("eventId", eventId);
        plan.put("eventStartTime", dtf.format(Instant.ofEpochMilli(publishTs)));
        Map<String, Object> eventRecord = buildRunningEventRecord(plan, publishTs);
        stateService.getWindEventRecords().add(eventRecord);
        stateService.getPersistenceService().upsertEvent(eventRecord);
        log.info("wind control running event created: planId={}, eventId={}, status={}",
                stateService.stringValue(plan.get("planId")),
                eventId,
                stateService.stringValue(eventRecord.get("status")));
        return new LinkedHashMap<>(eventRecord);
    }

    private String newEventId() {
        String eventId;
        do {
            eventId = "EVT-" + UUID.randomUUID().toString().substring(0, 6);
        } while (findEventRecordById(eventId) != null);
        return eventId;
    }

    private String resolveOnDutyPersonnel(Map<String, Object> plan) {
        Map<String, Object> dispatch = safeMap(plan.get("dispatch"));
        String teamId = firstNonBlank(dispatch.get("teamId"), plan.get("teamId"));
        List<String> names = new ArrayList<>();
        for (Map<String, Object> staff : stateService.getStaffList()) {
            String sidTeamId = stateService.stringValue(staff.get("teamId"));
            if (!teamId.isBlank() && !teamId.equalsIgnoreCase(sidTeamId)) {
                continue;
            }
            if (!Boolean.TRUE.equals(staff.get("onDuty"))) {
                continue;
            }
            String name = firstNonBlank(staff.get("name"), staff.get("staffId"));
            if (!name.isBlank() && !names.contains(name)) {
                names.add(name);
            }
        }
        if (!names.isEmpty()) {
            return String.join("、", names);
        }
        String contact = firstNonBlank(
                plan.get("contactStaff"),
                dispatch.get("contactStaff"),
                dispatch.get("contactName")
        );
        return contact;
    }

    /**
     * 定时收口：到达预计结束时间后自动关闭管控，并结束已有事件记录。
     */
    @Scheduled(fixedDelayString = "${wind.control.auto-close.fixed-delay-ms:30000}")
    public void autoCloseExpiredPlansJob() {
        autoCloseExpiredPublishedPlans();
    }

    /**
     * 自动关闭到期的 PUBLISHED 方案，不主动新增事件报告。
     */
    private void autoCloseExpiredPublishedPlans() {
        long now = System.currentTimeMillis();
        List<String> expiredPlanIds = new ArrayList<>();
        for (Map<String, Object> plan : stateService.getGeneratedPlans().values()) {
            if (!"PUBLISHED".equalsIgnoreCase(stateService.stringValue(plan.get("status")))) {
                continue;
            }
            long endTimestamp = longValue(plan.get("endTimestamp"), Long.MAX_VALUE);
            if (endTimestamp <= now) {
                String planId = stateService.stringValue(plan.get("planId"));
                if (!planId.isBlank()) {
                    expiredPlanIds.add(planId);
                }
            }
        }
        for (String planId : expiredPlanIds) {
            try {
                closePlan(planId);
            } catch (Exception ignored) {
                // 到期收口是幂等动作，单条失败不影响其余区段收口。
            }
        }
    }

    private String firstNonBlank(Object... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        for (Object value : values) {
            String text = stateService.stringValue(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private String resolveDashboardSegmentText(Map<String, Object> interval, int direction) {
        String raw = firstNonBlank(interval.get("segmentText"), interval.get("segment"), interval.get("intervalName"));
        String normalized = normalizeSegmentText(raw);
        if (!normalized.isBlank() && !isIntervalCode(normalized)) {
            return normalized;
        }
        String intervalName = stateService.stringValue(interval.get("intervalName")).trim();
        if ("1-1".equals(intervalName) || "2-1".equals(intervalName)) {
            return direction == DIRECTION_TURPAN ? "一碗泉服务区-红山口服务区" : "红山口服务区-一碗泉服务区";
        }
        if ("1-2".equals(intervalName) || "2-2".equals(intervalName)) {
            return direction == DIRECTION_TURPAN ? "红山口服务区-红山口互通" : "红山口互通-红山口服务区";
        }
        if ("1-3".equals(intervalName) || "2-3".equals(intervalName)) {
            return direction == DIRECTION_TURPAN ? "红山口互通-沙尔湖服务区" : "沙尔湖服务区-红山口互通";
        }
        String startStake = stateService.stringValue(interval.get("startStake"));
        String endStake = stateService.stringValue(interval.get("endStake"));
        if (!startStake.isBlank() && !endStake.isBlank()) {
            return startStake + "-" + endStake;
        }
        return direction == DIRECTION_TURPAN ? "吐鲁番方向区间" : "哈密方向区间";
    }

    private String normalizeSegmentText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("区段", "").trim();
    }

    private boolean isIntervalCode(String text) {
        return text.matches("^\\d+-\\d+$");
    }

    private String buildServiceAreaWelcomeText(Object segmentObj) {
        String segmentText = stateService.stringValue(segmentObj).trim();
        if (segmentText.isBlank()) {
            return "服务区" + SERVICE_AREA_WELCOME_SUFFIX;
        }
        String[] tokens = segmentText.split("-");
        for (String token : tokens) {
            String value = token.trim();
            if (value.contains("服务区")) {
                return value + SERVICE_AREA_WELCOME_SUFFIX;
            }
        }
        if (segmentText.contains("服务区")) {
            return segmentText + SERVICE_AREA_WELCOME_SUFFIX;
        }
        return "服务区" + SERVICE_AREA_WELCOME_SUFFIX;
    }

    private List<Map<String, Object>> normalizeVmsPublishItems(Object rows) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (!(rows instanceof List<?> list)) {
            return items;
        }
        for (Object item : list) {
            Map<String, Object> row = safeMap(item);
            if (row.isEmpty()) {
                continue;
            }
            String facilityId = firstNonBlank(row.get("facilityId"), row.get("deviceId"));
            row.put("facilityId", facilityId);
            row.put("deviceId", facilityId);
            items.add(row);
        }
        return items;
    }

    private List<Map<String, Object>> buildCurrentPublishInfoRows(List<Map<String, Object>> autoItems) {
        LinkedHashSet<String> itemDeviceIds = new LinkedHashSet<>();
        for (Map<String, Object> row : autoItems) {
            String deviceId = firstNonBlank(row.get("deviceId"), row.get("facilityId"));
            if (!deviceId.isBlank()) {
                itemDeviceIds.add(deviceId);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String deviceId : itemDeviceIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deviceId", deviceId);
            // 业务规则：本次自动生成命中的设备，当前发布信息返回空字符串
            row.put("currentPublishInfo", "");
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> resolveExecutionDispatch(Map<String, Object> interval, String segmentText, int direction) {
        Map<String, Object> dispatch = new LinkedHashMap<>(interval == null ? Collections.emptyMap() : interval);
        List<Map<String, Object>> teamRows = getDutyTeamsForExecution();
        Map<String, Object> team = findTeamBySegment(interval == null ? Collections.emptyMap() : interval, segmentText, direction, teamRows);
        if (team == null) {
            String preferredTeamId = stateService.stringValue(dispatch.get("teamId"));
            team = preferredTeamId.isBlank() ? null : findTeamById(preferredTeamId, teamRows);
        }

        String teamId = team == null ? "" : stateService.stringValue(team.get("teamId"));
        String contact = resolveContactStaffByTeam(team);
        String warehouse = stateService.stringValue(dispatch.get("warehouse"));

        dispatch.put("teamId", teamId);
        dispatch.put("teamName", team == null ? "" : firstNonBlank(team.get("name"), team.get("teamName"), team.get("team")));
        dispatch.put("contactStaff", contact);
        dispatch.put("warehouse", warehouse);
        return dispatch;
    }

    private Map<String, Object> findTeamById(String teamId, List<Map<String, Object>> teamRows) {
        if (teamId == null || teamId.isBlank()) {
            return null;
        }
        for (Map<String, Object> team : teamRows) {
            if (teamId.equalsIgnoreCase(stateService.stringValue(team.get("teamId")))) {
                return team;
            }
        }
        return null;
    }

    private Map<String, Object> findTeamBySegment(Map<String, Object> interval,
                                                  String segmentText,
                                                  int direction,
                                                  List<Map<String, Object>> teamRows) {
        if (teamRows.isEmpty()) {
            return null;
        }
        Double start = stateService.parseStakeValue(stateService.stringValue(interval.get("startStake")));
        Double end = stateService.parseStakeValue(stateService.stringValue(interval.get("endStake")));
        double center = (start != null && end != null) ? (start + end) / 2.0 : -1D;

        Map<String, Object> nearestRangeTeam = null;
        double nearestRangeDistance = Double.MAX_VALUE;
        if (center >= 0) {
            for (Map<String, Object> team : teamRows) {
                Double[] range = parseNodeStakeRange(stateService.stringValue(team.get("node")));
                if (range[0] == null || range[1] == null) {
                    continue;
                }
                double min = Math.min(range[0], range[1]);
                double max = Math.max(range[0], range[1]);
                if (center >= min && center <= max) {
                    return team;
                }
                double distance = center < min ? (min - center) : (center - max);
                if (distance < nearestRangeDistance) {
                    nearestRangeDistance = distance;
                    nearestRangeTeam = team;
                }
            }
            if (nearestRangeTeam != null) {
                return nearestRangeTeam;
            }
        }

        List<String> keywords = new ArrayList<>();
        for (String token : stateService.stringValue(segmentText).split("-")) {
            String value = token.trim();
            if (!value.isBlank()) {
                keywords.add(value);
            }
        }

        Map<String, Object> best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map<String, Object> team : teamRows) {
            String node = stateService.stringValue(team.get("node"));
            int score = 0;
            for (String keyword : keywords) {
                if (!keyword.isBlank() && node.contains(keyword)) {
                    score += 4;
                }
            }
            if (segmentText.contains("互通") && node.contains("互通")) {
                score += 3;
            }
            if (segmentText.contains("服务区") && node.contains("服务区")) {
                score += 3;
            }
            if (direction == DIRECTION_TURPAN && node.contains("南")) {
                score += 2;
            }
            if (direction == DIRECTION_HAMI && node.contains("北")) {
                score += 2;
            }
            Double[] range = parseNodeStakeRange(node);
            if (range[0] != null && range[1] != null && center >= 0) {
                double min = Math.min(range[0], range[1]);
                double max = Math.max(range[0], range[1]);
                if (center >= min && center <= max) {
                    score += 8;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = team;
            }
        }
        return best;
    }

    private Double[] parseNodeStakeRange(String node) {
        Double[] values = new Double[]{null, null};
        if (node == null || node.isBlank()) {
            return values;
        }
        Matcher matcher = stakePattern.matcher(node.toUpperCase(Locale.ROOT));
        List<Double> stakes = new ArrayList<>();
        while (matcher.find()) {
            String token = "K" + matcher.group(1);
            Double value = stateService.parseStakeValue(token);
            if (value != null) {
                stakes.add(value);
            }
        }
        if (stakes.size() >= 2) {
            values[0] = stakes.get(0);
            values[1] = stakes.get(1);
        }
        return values;
    }

    private String resolveContactStaffByTeam(Map<String, Object> team) {
        if (team == null) {
            return "";
        }
        // 需求：contactStaff 以 duty_team_static.contact_name 为准。
        return firstNonBlank(team.get("contactName"), team.get("contact_name"));
    }

    private String findStaffNameById(String staffId) {
        if (staffId == null || staffId.isBlank()) {
            return "";
        }
        for (Map<String, Object> staff : stateService.getStaffList()) {
            String rowStaffId = firstNonBlank(staff.get("staffId"), staff.get("staff_id"));
            if (staffId.equalsIgnoreCase(rowStaffId)) {
                return firstNonBlank(staff.get("name"), rowStaffId);
            }
        }
        return "";
    }

    private List<Map<String, Object>> getDutyTeamsForExecution() {
        List<Map<String, Object>> dbRows = getDutyTeamsFromDbForExecution();
        if (!dbRows.isEmpty()) {
            return dbRows;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> team : stateService.getDutyTeams()) {
            String teamId = firstNonBlank(team.get("teamId"), team.get("team_id"));
            if (teamId.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("teamId", teamId);
            row.put("name", firstNonBlank(team.get("name"), team.get("teamName"), team.get("team")));
            row.put("contactName", firstNonBlank(team.get("contactName"), team.get("contact_name")));
            row.put("node", firstNonBlank(team.get("node"), team.get("responsibleNode")));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> getDutyTeamsFromDbForExecution() {
        try {
            List<Map<String, Object>> queryRows = jdbcTemplate.queryForList(
                    "SELECT team_id AS teamId, name, contact_name AS contactName, node " +
                            "FROM duty_team_static WHERE is_enabled = 1 ORDER BY sort_no, id"
            );
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> item : queryRows) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("teamId", firstNonBlank(item.get("teamId"), item.get("team_id")));
                row.put("name", firstNonBlank(item.get("name")));
                row.put("contactName", firstNonBlank(item.get("contactName"), item.get("contact_name")));
                row.put("node", firstNonBlank(item.get("node")));
                rows.add(row);
            }
            return rows;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private String findStaffNameByTeam(String teamId, boolean onDutyOnly) {
        if (teamId == null || teamId.isBlank()) {
            return "";
        }
        for (Map<String, Object> staff : stateService.getStaffList()) {
            if (!teamId.equalsIgnoreCase(stateService.stringValue(staff.get("teamId")))) {
                continue;
            }
            if (onDutyOnly && !Boolean.TRUE.equals(staff.get("onDuty"))) {
                continue;
            }
            return firstNonBlank(staff.get("name"), staff.get("staffId"));
        }
        return "";
    }

    private List<Map<String, Object>> getPublishFacilitiesForExecution() {
        List<Map<String, Object>> fallback = stateService.getPublishFacilities();
        List<PublishFacilityStatic> staticRows = publishFacilityStaticService.getEnabledFacilities();
        if (staticRows == null || staticRows.isEmpty()) {
            return fallback;
        }
        Map<String, String> postInformationById = new LinkedHashMap<>();
        for (Map<String, Object> row : fallback) {
            String facilityId = stateService.stringValue(row.get("facilityId"));
            if (!facilityId.isBlank()) {
                postInformationById.put(facilityId, stateService.stringValue(row.get("postInformation")));
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PublishFacilityStatic facility : staticRows) {
            String facilityId = stateService.stringValue(facility.getFacilityId());
            if (facilityId.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("facilityId", facilityId);
            row.put("pileNo", stateService.stringValue(facility.getPileNo()));
            row.put("direction", stateService.intValue(facility.getDirection(), DIRECTION_HAMI));
            row.put("type", stateService.stringValue(facility.getFacilityType()));
            row.put("segment", stateService.stringValue(facility.getSegment()));
            row.put("interchangeName", stateService.stringValue(facility.getInterchangeName()));
            row.put("interchangeStake", stateService.stringValue(facility.getInterchangeStake()));
            row.put("redAlertMessage", stateService.stringValue(facility.getRedAlertMessage()));
            row.put("colorAlertMessage", stateService.stringValue(facility.getColorAlertMessage()));
            row.put("postInformation", postInformationById.getOrDefault(facilityId, ""));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Collections.emptyMap();
    }

    private String buildEventLocation(String startStake, String endStake) {
        if (startStake == null || startStake.isBlank()) {
            return endStake == null ? "" : endStake;
        }
        if (endStake == null || endStake.isBlank()) {
            return startStake;
        }
        return startStake + "-" + endStake;
    }

    private record FixedVmsContent(String mainContent, String tipContent) {
    }

    private static class ExecutionVmsDevice {
        private final String line;
        private final String deviceId;
        private final String stake;
        private final String type;
        private final String segment;
        private final String interchangeName;

        private ExecutionVmsDevice(String line, String deviceId, String stake, String type, String segment, String interchangeName) {
            this.line = line;
            this.deviceId = deviceId;
            this.stake = stake;
            this.type = type;
            this.segment = segment;
            this.interchangeName = interchangeName;
        }

        private String line() {
            return line;
        }

        private String deviceId() {
            return deviceId;
        }

        private String stake() {
            return stake;
        }

        private String type() {
            return type;
        }

        private String segment() {
            return segment;
        }

        private String interchangeName() {
            return interchangeName;
        }
    }
}
