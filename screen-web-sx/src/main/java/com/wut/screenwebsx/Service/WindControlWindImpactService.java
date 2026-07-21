package com.wut.screenwebsx.Service;

import com.wut.screendbmysqlsx.Model.WindData;
import com.wut.screendbmysqlsx.Model.VmsContentTemplateStatic;
import com.wut.screendbmysqlsx.Service.SpeedThresholdStaticService;
import com.wut.screendbmysqlsx.Service.VmsContentTemplateStaticService;
import com.wut.screendbmysqlsx.Service.WindDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 4.2 大风时空影响业务服务。
 *
 * 关键说明：
 * 1. 大风数据只读取真实来源（wind_data + 轨迹表）；
 * 2. 不再提供任何模拟兜底数据；
 * 3. 方向规范始终使用 1（去往哈密）与 2（去往吐鲁番）。
 */
@Service
public class WindControlWindImpactService {
    /** 去往哈密方向（下行）。 */
    private static final int DIRECTION_HAMI = 1;
    /** 去往吐鲁番方向（上行）。 */
    private static final int DIRECTION_TURPAN = 2;
    /** 4 小时时窗。 */
    private static final long WINDOW_4H_MS = 4L * 60 * 60 * 1000;
    /** 2 小时时窗。 */
    private static final long WINDOW_2H_MS = 2L * 60 * 60 * 1000;
    /** 24 小时时窗。 */
    private static final long WINDOW_24H_MS = 24L * 60 * 60 * 1000;
    private static final long WINDOW_72H_MS = 72L * 60 * 60 * 1000;
    /** 默认时间格式。 */
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 4.2.3 固定管控区间。 */
    private static final List<String> FIXED_HAMI_IMPACT_STAKE_RANGES = List.of(
            "K3178-K3192",
            "K3192-K3197",
            "K3197-K3204"
    );
    private static final List<String> FIXED_TURPAN_IMPACT_STAKE_RANGES = List.of(
            "K3178-K3192",
            "K3192-K3197",
            "K3197-K3204"
    );
    private static final String SAME_RISK_PLACEHOLDER = "\u540c\u98ce\u9669\u533a\u6bb5\u5185\u65b9\u6848";
    private static final String VMS_INSIDE_SEGMENT = "vmsInsideSegment";
    private static final String VMS_UPSTREAM_EXIT = "vmsUpstreamExit";
    private static final String VMS_UPSTREAM_TOLLGATE = "vmsUpstreamTollgate";
    private static final String VMS_UPSTREAM_SERVICE_AREA = "vmsUpstreamServiceArea";
    private static final String VMS_LINE_T = "T";
    private static final String VMS_LINE_H = "H";
    private static final List<VmsDevice> VMS_DEVICES = List.of(
            new VmsDevice(VMS_LINE_T, "T10013R", "K3282+300", VMS_UPSTREAM_TOLLGATE),
            new VmsDevice(VMS_LINE_T, "T10012C", "K3281+370", VMS_UPSTREAM_EXIT),
            new VmsDevice(VMS_LINE_T, "T10011", "K3263+200", VMS_INSIDE_SEGMENT),
            new VmsDevice(VMS_LINE_T, "T10010F", "K3243+100", VMS_UPSTREAM_SERVICE_AREA),
            new VmsDevice(VMS_LINE_T, "T10009", "K3234+000", VMS_INSIDE_SEGMENT),
            new VmsDevice(VMS_LINE_T, "T10008R", "K3198+000", VMS_UPSTREAM_TOLLGATE),
            new VmsDevice(VMS_LINE_T, "T10007C", "K3196+450", VMS_UPSTREAM_EXIT),
            new VmsDevice(VMS_LINE_T, "T10006F", "K3191+800", VMS_UPSTREAM_SERVICE_AREA),
            new VmsDevice(VMS_LINE_T, "T10005", "K3180+000", VMS_INSIDE_SEGMENT),
            new VmsDevice(VMS_LINE_T, "T10004F", "K3150+000", VMS_UPSTREAM_SERVICE_AREA),
            new VmsDevice(VMS_LINE_T, "T10003", "K3134+000", VMS_INSIDE_SEGMENT),
            new VmsDevice(VMS_LINE_T, "T10002R", "K3117+850", VMS_UPSTREAM_TOLLGATE),
            new VmsDevice(VMS_LINE_T, "T10001C", "K3110+500", VMS_UPSTREAM_EXIT),
            new VmsDevice(VMS_LINE_H, "H10013C", "K3283+900", VMS_UPSTREAM_EXIT),
            new VmsDevice(VMS_LINE_H, "H10012R", "K3283+000", VMS_UPSTREAM_TOLLGATE),
            new VmsDevice(VMS_LINE_H, "H10011", "K3261+200", VMS_INSIDE_SEGMENT),
            new VmsDevice(VMS_LINE_H, "H10010F", "K3245+200", VMS_UPSTREAM_SERVICE_AREA),
            new VmsDevice(VMS_LINE_H, "H10009", "K3232+000", VMS_INSIDE_SEGMENT),
            new VmsDevice(VMS_LINE_H, "H10008C", "K3199+500", VMS_UPSTREAM_EXIT),
            new VmsDevice(VMS_LINE_H, "H10007R", "K3197+000", VMS_UPSTREAM_TOLLGATE),
            new VmsDevice(VMS_LINE_H, "H10006F", "K3194+515", VMS_UPSTREAM_SERVICE_AREA),
            new VmsDevice(VMS_LINE_H, "H10005", "K3180+000", VMS_INSIDE_SEGMENT),
            new VmsDevice(VMS_LINE_H, "H10004F", "K3152+900", VMS_UPSTREAM_SERVICE_AREA),
            new VmsDevice(VMS_LINE_H, "H10003", "K3132+000", VMS_INSIDE_SEGMENT),
            new VmsDevice(VMS_LINE_H, "H10002C", "K3119+300", VMS_UPSTREAM_EXIT),
            new VmsDevice(VMS_LINE_H, "H10001R", "K3117+800", VMS_UPSTREAM_TOLLGATE)
    );

    /** 桩号提取规则，支持 K3191 与 K3191+800。 */
    private static final Pattern STAKE_PATTERN = Pattern.compile("K(\\d+(?:\\+\\d+)?)", Pattern.CASE_INSENSITIVE);

    /** 公共状态服务。 */
    private final WindControlStateService stateService;
    /** 轨迹聚合服务（用于交通量估算）。 */
    private final WindControlTrajectoryService trajectoryService;
    /** wind_data 数据服务。 */
    private final WindDataService windDataService;
    /** 限速阈值静态表服务。 */
    private final SpeedThresholdStaticService speedThresholdStaticService;
    /** VMS 固定提示模板静态表服务。 */
    private final VmsContentTemplateStaticService vmsContentTemplateStaticService;
    /** APP 侧小时级限速重算服务。 */
    private final WindRiskSpeedService windRiskSpeedService;

    /**
     * 构造函数。
     *
     * @param stateService 公共状态服务
     * @param trajectoryService 轨迹聚合服务
     * @param windDataService wind_data 服务
     */
    public WindControlWindImpactService(WindControlStateService stateService,
                                        WindControlTrajectoryService trajectoryService,
                                        WindDataService windDataService,
                                        SpeedThresholdStaticService speedThresholdStaticService,
                                        VmsContentTemplateStaticService vmsContentTemplateStaticService,
                                        WindRiskSpeedService windRiskSpeedService) {
        this.stateService = stateService;
        this.trajectoryService = trajectoryService;
        this.windDataService = windDataService;
        this.speedThresholdStaticService = speedThresholdStaticService;
        this.vmsContentTemplateStaticService = vmsContentTemplateStaticService;
        this.windRiskSpeedService = windRiskSpeedService;
    }

    /**
     * 查询全线风力可视化数据（4.2.1）。
     *
     * 规则：
     * 1. mode=real：读取 wind_data 的“最新快照”；
     * 2. mode=forecast：读取未来 4h 窗口并取每段最大风级；
     * 3. mode=max2h/max72h：分别读取未来 2h/72h 窗口最大风级；
     * 4. 无真实风数据时，不返回该路段记录。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param mode 模式：real/forecast/max2h/max72h（兼容 max4h/4h 历史入参）
     * @return 可视化结果
     */
    public Map<String, Object> getWindVisualization(long timestamp, String mode) {
        String finalMode = mode == null ? "real" : mode.toLowerCase(Locale.ROOT);
        if ("max4h".equals(finalMode) || "4h".equals(finalMode)) {
            finalMode = "max2h";
        }
        LocalDateTime now = toLocalDateTime(timestamp);

        List<WindData> latestRows = windDataService.listLatestSnapshot(now);
        List<WindData> future2hRows = "max2h".equals(finalMode)
                ? windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_2H_MS))
                : List.of();
        List<WindData> future72hRows = ("forecast".equals(finalMode) || "max72h".equals(finalMode))
                ? windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_72H_MS))
                : List.of();

        List<WindData> rowsFromDb;
        if ("forecast".equals(finalMode) || "max72h".equals(finalMode)) {
            rowsFromDb = future72hRows;
        } else if ("max2h".equals(finalMode)) {
            rowsFromDb = future2hRows;
        } else {
            rowsFromDb = latestRows;
        }
        List<Map<String, Object>> rows = buildWindQueryRecordsFromDb(rowsFromDb, rowsFromDb, null, finalMode);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("mode", finalMode);
        data.put("sections", rows);
        return data;
    }

    /**
     * 按 road_segment_static.control_interval 聚合管控区间，并生成实际下发的结束桩号范围。
     *
     * 示例：展示区间 K3178-K3192 由 K3178-K3179 ... K3191-K3192 组成，
     * 实际下发结束桩号为 K3179-K3192。
     *
     * @param direction 可选方向（1=去往哈密方向，2=去往吐鲁番方向）
     * @return 管控区间下发桩号数据
     */
    public Map<String, Object> listControlIntervalSendRanges(Integer direction) {
        return listControlIntervalSendRanges(direction, null);
    }

    public Map<String, Object> listControlIntervalSendRanges(Integer direction, Long timestamp) {
        return listControlIntervalSendRanges(direction, timestamp, null);
    }

    public Map<String, Object> listControlIntervalSendRanges(Integer direction, Long timestamp, String planId) {
        Map<String, Object> targetPlan = resolveTargetPlan(planId);
        Integer planDirection = resolvePlanDirection(targetPlan);
        Integer normalizedDirection = direction == null ? planDirection : normalizeDirection(direction);
        long effectiveTimestamp = timestamp == null ? System.currentTimeMillis() : timestamp;
        if (targetPlan == null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("timestamp", effectiveTimestamp);
            data.put("records", List.of());
            return data;
        }
        LocalDateTime now = toLocalDateTime(effectiveTimestamp);
        List<WindData> latestRows = windDataService.listLatestSnapshot(now);
        List<WindData> future2hRows = windDataService.listByTimeRange(now, toLocalDateTime(effectiveTimestamp + WINDOW_2H_MS));
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> section : stateService.getFullLineWindSections()) {
            int sectionDirection = stateService.intValue(section.get("direction"), DIRECTION_HAMI);
            if (normalizedDirection != null && sectionDirection != normalizedDirection) {
                continue;
            }
            String startStake = normalizeStakeText(stateService.stringValue(section.get("startStake")));
            String endStake = normalizeStakeText(stateService.stringValue(section.get("endStake")));
            String controlInterval = resolveControlIntervalForDisplay(
                    sectionDirection,
                    stateService.stringValue(section.get("controlInterval")),
                    startStake,
                    endStake
            );
            if (controlInterval.isBlank() || startStake.isBlank() || endStake.isBlank()) {
                continue;
            }
            String key = sectionDirection + "|" + controlInterval;
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(section);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            List<Map<String, Object>> sections = entry.getValue();
            if (sections.isEmpty()) {
                continue;
            }
            int rowDirection = stateService.intValue(sections.get(0).get("direction"), DIRECTION_HAMI);
            String controlInterval = stateService.stringValue(sections.get(0).get("controlInterval"));
            String displayStartStake = "";
            String displayEndStake = "";
            String sendStartStake = "";
            String sendEndStake = "";

            for (Map<String, Object> section : sections) {
                String startStake = normalizeStakeText(stateService.stringValue(section.get("startStake")));
                String endStake = normalizeStakeText(stateService.stringValue(section.get("endStake")));
                if (startStake.isBlank() || endStake.isBlank()) {
                    continue;
                }
                if (displayStartStake.isBlank()) {
                    displayStartStake = startStake;
                }
                displayEndStake = endStake;
                if (sendStartStake.isBlank()) {
                    sendStartStake = endStake;
                }
                sendEndStake = endStake;
            }

            if (displayStartStake.isBlank() || displayEndStake.isBlank() || sendStartStake.isBlank() || sendEndStake.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("controlInterval", controlInterval);
            String sendStakeRange = resolveControlIntervalSendStakeRange(controlInterval, sendStartStake, sendEndStake);
            double[] sendRange = parseRange(sendStakeRange);
            double[] displayRange = parseRange(displayStartStake + "-" + displayEndStake);
            Integer targetDirection = resolvePlanDirection(targetPlan);
            if (targetDirection != null && rowDirection != targetDirection) {
                continue;
            }
            if (!isPlanForControlInterval(targetPlan, controlInterval, displayRange, sendRange)) {
                continue;
            }
            row.put("sendStakeRange", sendStakeRange);
            String displayStakeRange = displayStartStake + "-" + displayEndStake;
            String statusStakeRange = resolveControlIntervalStatusStakeRange(controlInterval, displayStakeRange);
            int controlLevel = resolveFinalControlLevel(latestRows, future2hRows, rowDirection, statusStakeRange);
            row.put("controlLevel", controlLevel);
            row.put("controlLevelText", levelName(controlLevel));
            row.put("color", resolveControlIntervalColor(
                    latestRows,
                    future2hRows,
                    rowDirection,
                    controlInterval,
                    displayStakeRange
            ));
            Map<String, Object> vmsData = buildControlIntervalVmsData(
                    rowDirection,
                    controlInterval,
                    displayStartStake,
                    displayEndStake,
                    sendStakeRange,
                    controlLevel
            );
            if (vmsData != null && !vmsData.isEmpty()) {
                row.put("data", vmsData);
            }
            rows.add(row);
        }

        rows.sort((a, b) -> {
            return stateService.stringValue(a.get("controlInterval")).compareTo(stateService.stringValue(b.get("controlInterval")));
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", effectiveTimestamp);
        data.put("planId", stateService.stringValue(targetPlan.get("planId")));
        data.put("intervalName", stateService.stringValue(targetPlan.get("intervalName")));
        data.put("controlInterval", rows.isEmpty()
                ? stateService.stringValue(targetPlan.get("intervalName"))
                : stateService.stringValue(rows.get(0).get("controlInterval")));
        data.put("records", rows);
        return data;
    }

    private String resolveControlIntervalForDisplay(int direction,
                                                    String controlInterval,
                                                    String startStake,
                                                    String endStake) {
        if (isStakeRange(startStake, endStake, 3192D, 3193D)) {
            if ("1-1".equals(controlInterval)) {
                return "1-2";
            }
            if ("2-1".equals(controlInterval)) {
                return "2-2";
            }
        }
        return controlInterval;
    }

    private boolean isStakeRange(String startStake,
                                 String endStake,
                                 double expectedStart,
                                 double expectedEnd) {
        Double start = parseStakeValue(startStake);
        Double end = parseStakeValue(endStake);
        return start != null
                && end != null
                && Math.abs(start - expectedStart) < 0.001D
                && Math.abs(end - expectedEnd) < 0.001D;
    }

    private Map<String, Object> resolveTargetPlan(String planId) {
        String normalizedPlanId = stateService.stringValue(planId);
        Map<String, Object> plan = normalizedPlanId.isBlank()
                ? resolveLatestPlan()
                : stateService.getGeneratedPlans().get(normalizedPlanId);
        if (plan == null || plan.isEmpty()) {
            if (normalizedPlanId.isBlank()) {
                return null;
            }
            throw new IllegalArgumentException("plan not found: " + normalizedPlanId);
        }
        return plan;
    }

    private Map<String, Object> resolveLatestPlan() {
        Map<String, Object> matched = null;
        long matchedTime = Long.MIN_VALUE;
        for (Map<String, Object> plan : stateService.getGeneratedPlans().values()) {
            if (stateService.stringValue(plan.get("intervalName")).isBlank()) {
                continue;
            }
            long planTime = resolvePlanSortTime(plan);
            if (matched == null || planTime >= matchedTime) {
                matched = plan;
                matchedTime = planTime;
            }
        }
        return matched;
    }

    private Integer resolvePlanDirection(Map<String, Object> plan) {
        if (plan == null) {
            return null;
        }
        int direction = stateService.intValue(plan.get("direction"), 0);
        if (direction != DIRECTION_HAMI && direction != DIRECTION_TURPAN) {
            return null;
        }
        return direction;
    }

    private String resolveControlIntervalSendStakeRange(String controlInterval, String sendStartStake, String sendEndStake) {
        return switch (controlInterval) {
            case "1-1" -> "K3192-K3178";
            case "1-2" -> "K3197-K3193";
            case "1-3" -> "K3203-K3198";
            case "2-1" -> "K3191-K3178";
            case "2-2" -> "K3196-K3192";
            case "2-3" -> "K3203-K3197";
            default -> sendStartStake + "-" + sendEndStake;
        };
    }

    private Map<String, Object> buildControlIntervalVmsData(int direction,
                                                            String controlInterval,
                                                            String startStake,
                                                            String endStake,
                                                            String sendStakeRange,
                                                            int controlLevel) {
        double[] sendRange = parseRange(sendStakeRange);
        if (sendRange == null) {
            return Map.of();
        }
        Map<String, Object> plan = findLatestPlanForControlInterval(controlInterval, direction, startStake, endStake, sendRange);
        if (plan == null || plan.isEmpty()) {
            return Map.of();
        }

        Map<String, String> planTexts = resolveControlLevelPlanTexts(controlLevel);
        Map<String, Object> result = new LinkedHashMap<>();
        putVmsItemIfNotBlank(result, controlInterval, sendStakeRange, sendRange, VMS_INSIDE_SEGMENT,
                resolveFixedVmsContent(controlLevel, VMS_INSIDE_SEGMENT, planTexts.get(VMS_INSIDE_SEGMENT)));
        putVmsItemIfNotBlank(result, controlInterval, sendStakeRange, sendRange, VMS_UPSTREAM_EXIT,
                resolveFixedVmsContent(controlLevel, VMS_UPSTREAM_EXIT, planTexts.get(VMS_UPSTREAM_EXIT)));
        putVmsItemIfNotBlank(result, controlInterval, sendStakeRange, sendRange, VMS_UPSTREAM_TOLLGATE,
                resolveFixedVmsContent(controlLevel, VMS_UPSTREAM_TOLLGATE, planTexts.get(VMS_UPSTREAM_TOLLGATE)));
        putVmsItemIfNotBlank(result, controlInterval, sendStakeRange, sendRange, VMS_UPSTREAM_SERVICE_AREA,
                resolveFixedVmsContent(controlLevel, VMS_UPSTREAM_SERVICE_AREA, planTexts.get(VMS_UPSTREAM_SERVICE_AREA)));
        return result;
    }

    private Map<String, String> resolveControlLevelPlanTexts(int controlLevel) {
        Map<String, Object> template = stateService.getControlPlanLibrary().get(controlLevel);
        if (template == null || template.isEmpty()) {
            return Map.of();
        }
        String inside = materializePlanText(
                stateService.stringValue(template.get("riskSectionPlan")),
                stateService.stringValue(template.get("riskSectionPlan"))
        );
        String exit = materializePlanText(
                stateService.stringValue(template.get("upstreamExitPlan")),
                inside
        );
        String tollgate = materializePlanText(
                stateService.stringValue(template.get("upstreamEntryPlan")),
                inside
        );
        String serviceArea = materializePlanText(
                stateService.stringValue(template.get("upstreamServiceAreaPlan")),
                inside
        );
        Map<String, String> result = new LinkedHashMap<>();
        result.put(VMS_INSIDE_SEGMENT, inside);
        result.put(VMS_UPSTREAM_EXIT, exit);
        result.put(VMS_UPSTREAM_TOLLGATE, tollgate);
        result.put(VMS_UPSTREAM_SERVICE_AREA, serviceArea);
        return result;
    }

    private Map<String, String> resolvePlanVmsTexts(Map<String, Object> plan) {
        Map<String, String> mergedTexts = parseMergedVmsContent(firstNonBlank(
                plan.get("vmsContent"),
                plan.get(VMS_INSIDE_SEGMENT),
                plan.get(VMS_UPSTREAM_EXIT),
                plan.get(VMS_UPSTREAM_TOLLGATE),
                plan.get(VMS_UPSTREAM_SERVICE_AREA)
        ));
        String inside = resolveSingleVmsText(plan, mergedTexts, VMS_INSIDE_SEGMENT, "");
        String exit = resolveSingleVmsText(plan, mergedTexts, VMS_UPSTREAM_EXIT, inside);
        String tollgate = resolveSingleVmsText(plan, mergedTexts, VMS_UPSTREAM_TOLLGATE, inside);
        String serviceArea = resolveSingleVmsText(plan, mergedTexts, VMS_UPSTREAM_SERVICE_AREA, inside);

        Map<String, String> result = new LinkedHashMap<>();
        result.put(VMS_INSIDE_SEGMENT, inside);
        result.put(VMS_UPSTREAM_EXIT, exit);
        result.put(VMS_UPSTREAM_TOLLGATE, tollgate);
        result.put(VMS_UPSTREAM_SERVICE_AREA, serviceArea);
        return result;
    }

    private String resolveSingleVmsText(Map<String, Object> plan,
                                        Map<String, String> mergedTexts,
                                        String key,
                                        String riskSectionPlan) {
        String raw = stateService.stringValue(plan.get(key));
        if (isMergedVmsContent(raw)) {
            raw = mergedTexts.getOrDefault(key, "");
        }
        if (raw.isBlank()) {
            raw = mergedTexts.getOrDefault(key, "");
        }
        return materializePlanText(raw, riskSectionPlan);
    }

    private Map<String, String> parseMergedVmsContent(String rawText) {
        if (!isMergedVmsContent(rawText)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        putMergedVmsPart(result, rawText, VMS_INSIDE_SEGMENT,
                "\u533a\u6bb5\u5185\uff1a", "\u4e0a\u6e38\u51fa\u53e3\uff1a");
        putMergedVmsPart(result, rawText, VMS_UPSTREAM_EXIT,
                "\u4e0a\u6e38\u51fa\u53e3\uff1a", "\u4e0a\u6e38\u5165\u53e3\uff1a");
        putMergedVmsPart(result, rawText, VMS_UPSTREAM_TOLLGATE,
                "\u4e0a\u6e38\u5165\u53e3\uff1a", "\u4e0a\u6e38\u670d\u52a1\u533a\uff1a");
        putMergedVmsPart(result, rawText, VMS_UPSTREAM_SERVICE_AREA,
                "\u4e0a\u6e38\u670d\u52a1\u533a\uff1a", "");
        return result;
    }

    private void putMergedVmsPart(Map<String, String> target,
                                  String rawText,
                                  String key,
                                  String startLabel,
                                  String endLabel) {
        int start = rawText.indexOf(startLabel);
        if (start < 0) {
            return;
        }
        start += startLabel.length();
        int end = endLabel.isBlank() ? rawText.length() : rawText.indexOf(endLabel, start);
        if (end < 0) {
            end = rawText.length();
        }
        String value = rawText.substring(start, end).trim();
        if (value.endsWith("\uff1b")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        if (!value.isBlank()) {
            target.put(key, value);
        }
    }

    private boolean isMergedVmsContent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("\u533a\u6bb5\u5185\uff1a")
                && text.contains("\u4e0a\u6e38\u51fa\u53e3\uff1a")
                && text.contains("\u4e0a\u6e38\u5165\u53e3\uff1a");
    }

    private String firstNonBlank(Object... values) {
        if (values == null) {
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

    private void putVmsItemIfNotBlank(Map<String, Object> target,
                                      String controlInterval,
                                      String sendStakeRange,
                                      double[] sendRange,
                                      String key,
                                      FixedVmsContent fixedContent) {
        if (fixedContent == null || (fixedContent.mainContent().isBlank() && fixedContent.tipContent().isBlank())) {
            return;
        }
        VmsDevice device = resolveVmsDevice(controlInterval, sendStakeRange, sendRange, key);
        if (device == null) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("stake", device.stake());
        item.put("deviceId", device.deviceId());
        item.put("fixedMainContent", fixedContent.mainContent());
        item.put("fixedTipContent", fixedContent.tipContent());
        target.put(key, item);
    }

    private FixedVmsContent resolveFixedVmsContent(int controlLevel, String vmsKind) {
        return resolveDefaultFixedVmsContent(controlLevel, vmsKind);
    }

    private FixedVmsContent resolveFixedVmsContent(int controlLevel, String vmsKind, String planTipContent) {
        FixedVmsContent fallback = resolveDefaultFixedVmsContent(controlLevel, vmsKind);
        String templateTipContent = resolveFixedVmsTemplateText(controlLevel, vmsKind, planTipContent);
        if (templateTipContent.isBlank()) {
            return new FixedVmsContent(
                    fallback.mainContent(),
                    alignFixedTipContentByPlanText(fallback.tipContent(), planTipContent)
            );
        }
        return new FixedVmsContent(fallback.mainContent(), templateTipContent);
    }

    private String resolveFixedVmsTemplateText(int controlLevel, String vmsKind, String planTipContent) {
        String controlLevelText = levelName(controlLevel);
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
        return renderFixedVmsTemplateText(row.getTemplateText(), controlLevel, planTipContent);
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
            case VMS_INSIDE_SEGMENT -> "IN_SECTION";
            case VMS_UPSTREAM_EXIT -> "UPSTREAM_EXIT";
            case VMS_UPSTREAM_TOLLGATE -> "UPSTREAM_ENTRY_TOLL";
            case VMS_UPSTREAM_SERVICE_AREA -> "SERVICE_AREA";
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

    private String renderFixedVmsTemplateText(String templateText, int controlLevel, String planTipContent) {
        String passengerSpeed = resolvePlanSpeedLimit(controlLevel, "passengerSpeedLimit", defaultPassengerLimitByControlLevel(controlLevel));
        String freightSpeed = resolvePlanSpeedLimit(controlLevel, "freightSpeedLimit", defaultFreightLimitByControlLevel(controlLevel));
        String rendered = (templateText == null ? "" : templateText)
                .replace("{LIGHT_SPEED}", passengerSpeed)
                .replace("${LIGHT_SPEED}", passengerSpeed)
                .replace("{HEAVY_SPEED}", freightSpeed)
                .replace("${HEAVY_SPEED}", freightSpeed);
        return alignFixedTipContentByPlanText(rendered, planTipContent);
    }

    private String alignFixedTipContentByPlanText(String tipContent, String planTipContent) {
        String rendered = tipContent == null ? "" : tipContent;
        rendered = alignVehicleControlByContent(rendered, planTipContent, "小型车", "小车");
        rendered = alignVehicleControlByContent(rendered, planTipContent, "大型车", "大车");
        rendered = removeReservationWhenAllVehiclesForbidden(rendered, planTipContent);
        return rendered.trim();
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

    private FixedVmsContent resolveDefaultFixedVmsContent(int controlLevel, String vmsKind) {
        return switch (controlLevel) {
            case 1 -> switch (vmsKind) {
                case VMS_UPSTREAM_TOLLGATE -> new FixedVmsContent("主线高速大风红色预警，车辆禁止驶入。", "入口提示：小型车禁行，大型车禁行。");
                case VMS_UPSTREAM_EXIT -> new FixedVmsContent("前方大风，所有车辆靠右驶离高速。", "出口提示：小型车禁行，大型车禁行。");
                case VMS_INSIDE_SEGMENT -> new FixedVmsContent("当前路段大风红色预警，车辆紧急避险。", "路段提示：小型车避险，大型车避险。");
                case VMS_UPSTREAM_SERVICE_AREA -> new FixedVmsContent("当前路段大风红色预警，车辆服务区避险。", "服务区提示：小型车避险，大型车避险。");
                default -> FixedVmsContent.empty();
            };
            case 2 -> switch (vmsKind) {
                case VMS_UPSTREAM_TOLLGATE -> new FixedVmsContent("主线高速大风橙色预警，小车预约大型车禁行。", "入口提示：车辆预约，小型车限速60，大型车禁行。");
                case VMS_UPSTREAM_EXIT -> new FixedVmsContent("前方大风橙色预警，小车预约大车驶离高速。", "出口提示：车辆预约，小型车限速60，大型车禁行。");
                case VMS_INSIDE_SEGMENT -> new FixedVmsContent("当前路段大风橙色预警，大车紧急避险。", "路段提示：小型车限速60，大型车避险。");
                case VMS_UPSTREAM_SERVICE_AREA -> new FixedVmsContent("当前路段大风橙色预警，大车紧急避险。", "服务区提示：小型车限速60，大型车避险。");
                default -> FixedVmsContent.empty();
            };
            case 3 -> switch (vmsKind) {
                case VMS_UPSTREAM_TOLLGATE -> new FixedVmsContent("主线高速大风黄色预警，仅预约车辆通行。", "入口提示：车辆预约，小型车限速60，大型车限速40。");
                case VMS_UPSTREAM_EXIT -> new FixedVmsContent("前方大风黄色预警，未预约车辆驶离高速。", "出口提示：车辆预约，小型车限速60，大型车限速40。");
                case VMS_INSIDE_SEGMENT -> new FixedVmsContent("当前路段大风黄色预警，请按限速行驶。", "路段提示：小型车限速60，大型车限速40。");
                case VMS_UPSTREAM_SERVICE_AREA -> new FixedVmsContent("当前路段大风黄色预警，请按限速行驶。", "服务区提示：小型车限速60，大型车限速40。");
                default -> FixedVmsContent.empty();
            };
            case 4 -> switch (vmsKind) {
                case VMS_UPSTREAM_TOLLGATE -> new FixedVmsContent("主线高速大风，请遵循指引安全驾驶。", "入口提示：小型车限速80，大型车限速60。");
                case VMS_UPSTREAM_EXIT -> new FixedVmsContent("前方大风，请遵循指引安全驾驶。", "出口提示：小型车限速80，大型车限速60。");
                case VMS_INSIDE_SEGMENT -> new FixedVmsContent("当前路段大风，请遵循指示安全驾驶。", "路段提示：小型车限速80，大型车限速60。");
                case VMS_UPSTREAM_SERVICE_AREA -> new FixedVmsContent("当前路段大风，请遵循指示安全驾驶。", "服务区提示：小型车限速80，大型车限速60。");
                default -> FixedVmsContent.empty();
            };
            case 5 -> new FixedVmsContent("连霍高速欢迎您，请遵循指引安全驾驶。", "温馨提示：小型车限速120，大型车限速80。");
            default -> FixedVmsContent.empty();
        };
    }

    private VmsDevice resolveVmsDevice(String controlInterval, String sendStakeRange, double[] sendRange, String key) {
        String line = vmsLineByControlInterval(controlInterval);
        List<VmsDevice> candidates = VMS_DEVICES.stream()
                .filter(device -> line.equals(device.line()))
                .filter(device -> parseStakeValue(device.stake()) != null)
                .toList();
        if (VMS_INSIDE_SEGMENT.equals(key)) {
            return selectInsideVmsDevice(sendStakeRange, sendRange, candidates);
        }
        boolean upstreamLowerSide = isUpstreamLowerSide(sendStakeRange);
        return candidates.stream()
                .filter(device -> key.equals(device.type()))
                .filter(device -> isUpstreamDevice(device, sendRange, upstreamLowerSide))
                .min((left, right) -> compareUpstreamDevice(left, right, upstreamLowerSide))
                .orElse(null);
    }

    private VmsDevice selectInsideVmsDevice(String sendStakeRange, double[] sendRange, List<VmsDevice> candidates) {
        boolean upstreamLowerSide = isUpstreamLowerSide(sendStakeRange);
        VmsDevice insideDevice = candidates.stream()
                .filter(device -> VMS_INSIDE_SEGMENT.equals(device.type()))
                .filter(device -> isStakeInside(device, sendRange))
                .min((left, right) -> compareInsideDevice(left, right, upstreamLowerSide))
                .orElse(null);
        if (insideDevice != null) {
            return insideDevice;
        }
        return candidates.stream()
                .filter(device -> isStakeInside(device, sendRange))
                .min((left, right) -> compareInsideDevice(left, right, upstreamLowerSide))
                .orElse(null);
    }

    private String vmsLineByControlInterval(String controlInterval) {
        return controlInterval != null && controlInterval.startsWith("2-") ? VMS_LINE_H : VMS_LINE_T;
    }

    private boolean isUpstreamLowerSide(String sendStakeRange) {
        List<Double> ordered = parseOrderedStakeValues(sendStakeRange);
        if (ordered.size() < 2) {
            return true;
        }
        return ordered.get(0) < ordered.get(1);
    }

    private boolean isStakeInside(VmsDevice device, double[] sendRange) {
        Double stakeValue = parseStakeValue(device.stake());
        return stakeValue != null && stakeValue >= sendRange[0] && stakeValue <= sendRange[1];
    }

    private boolean isUpstreamDevice(VmsDevice device, double[] sendRange, boolean upstreamLowerSide) {
        Double stakeValue = parseStakeValue(device.stake());
        if (stakeValue == null) {
            return false;
        }
        return upstreamLowerSide ? stakeValue < sendRange[0] : stakeValue > sendRange[1];
    }

    private int compareInsideDevice(VmsDevice left, VmsDevice right, boolean upstreamLowerSide) {
        Double leftValue = parseStakeValue(left.stake());
        Double rightValue = parseStakeValue(right.stake());
        if (leftValue == null || rightValue == null) {
            return 0;
        }
        return upstreamLowerSide ? Double.compare(leftValue, rightValue) : Double.compare(rightValue, leftValue);
    }

    private int compareUpstreamDevice(VmsDevice left, VmsDevice right, boolean upstreamLowerSide) {
        Double leftValue = parseStakeValue(left.stake());
        Double rightValue = parseStakeValue(right.stake());
        if (leftValue == null || rightValue == null) {
            return 0;
        }
        return upstreamLowerSide ? Double.compare(rightValue, leftValue) : Double.compare(leftValue, rightValue);
    }

    private Map<String, Object> findLatestPlanForControlInterval(String controlInterval,
                                                                 int direction,
                                                                 String startStake,
                                                                 String endStake,
                                                                 double[] sendRange) {
        Map<String, Object> matched = null;
        long matchedTime = Long.MIN_VALUE;
        double[] targetRange = parseRange(startStake + "-" + endStake);
        for (Map<String, Object> plan : stateService.getGeneratedPlans().values()) {
            if (direction != stateService.intValue(plan.get("direction"), direction)) {
                continue;
            }
            if (!isPlanForControlInterval(plan, controlInterval, targetRange, sendRange)) {
                continue;
            }
            long planTime = resolvePlanSortTime(plan);
            if (matched == null || planTime >= matchedTime) {
                matched = plan;
                matchedTime = planTime;
            }
        }
        return matched;
    }

    private boolean isPlanForControlInterval(Map<String, Object> plan,
                                             String controlInterval,
                                             double[] targetRange,
                                             double[] sendRange) {
        String intervalName = stateService.stringValue(plan.get("intervalName"));
        String segment = stateService.stringValue(plan.get("segment"));
        String segmentText = stateService.stringValue(plan.get("segmentText"));
        if (!controlInterval.isBlank()
                && (controlInterval.equals(intervalName)
                || controlInterval.equals(segment)
                || controlInterval.equals(segmentText))) {
            return true;
        }
        String planStart = stateService.stringValue(plan.get("startStake"));
        String planEnd = stateService.stringValue(plan.get("endStake"));
        double[] planRange = parseRange(planStart + "-" + planEnd);
        if (rangesEquivalent(targetRange, planRange) || rangesEquivalent(sendRange, planRange)) {
            return true;
        }
        int controlGroup = controlIntervalGroup(controlInterval);
        int planGroup = resolvePlanIntervalGroup(plan, planRange);
        return controlGroup > 0 && controlGroup == planGroup;
    }

    private int resolvePlanIntervalGroup(Map<String, Object> plan, double[] planRange) {
        int group = textIntervalGroup(stateService.stringValue(plan.get("intervalName")));
        if (group > 0) {
            return group;
        }
        group = textIntervalGroup(stateService.stringValue(plan.get("segmentText")));
        if (group > 0) {
            return group;
        }
        group = textIntervalGroup(stateService.stringValue(plan.get("segment")));
        if (group > 0) {
            return group;
        }
        return stakeRangeGroup(planRange);
    }

    private boolean rangesEquivalent(double[] left, double[] right) {
        if (left == null || right == null) {
            return false;
        }
        return left[0] == right[0] && left[1] == right[1];
    }

    private int controlIntervalGroup(String controlInterval) {
        if (controlInterval == null || controlInterval.isBlank()) {
            return 0;
        }
        if (controlInterval.endsWith("-1")) {
            return 1;
        }
        if (controlInterval.endsWith("-2")) {
            return 2;
        }
        if (controlInterval.endsWith("-3")) {
            return 3;
        }
        return 0;
    }

    private int textIntervalGroup(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String normalized = text.replace("—", "-")
                .replace("－", "-")
                .replace("至", "-")
                .replace(" ", "")
                .trim();
        if (normalized.contains("红山口服务区-一碗泉服务区")
                || normalized.contains("一碗泉服务区-红山口服务区")) {
            return 1;
        }
        if (normalized.contains("红山口互通-红山口服务区")
                || normalized.contains("红山口服务区-红山口互通")) {
            return 2;
        }
        if (normalized.contains("沙尔湖服务区-红山口互通")
                || normalized.contains("红山口互通-沙尔湖服务区")) {
            return 3;
        }
        return stakeRangeGroup(parseRange(normalized));
    }

    private int stakeRangeGroup(double[] range) {
        if (range == null) {
            return 0;
        }
        double min = range[0];
        double max = range[1];
        if (min >= 3178D && max <= 3193.5D) {
            return 1;
        }
        if (min >= 3192D && max <= 3197.5D) {
            return 2;
        }
        if (min >= 3197D && max <= 3204D) {
            return 3;
        }
        return 0;
    }

    private long resolvePlanSortTime(Map<String, Object> plan) {
        Object timestamp = plan.get("timestamp");
        if (timestamp instanceof Number n) {
            return n.longValue();
        }
        Object publishTime = plan.get("publishTime");
        if (publishTime instanceof Number n) {
            return n.longValue();
        }
        return 0L;
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

    /**
     * 查询风力限速阈值表（4.2.2）。
     *
     * 说明：此接口仍使用静态阈值配置（表1-6 + 运行时调整），与 wind_data 解耦。
     *
     * @return 阈值列表
     */
    public List<Map<String, Object>> getSpeedThresholds() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seenDesc = new HashSet<>();
        for (Integer level : new TreeSet<>(stateService.getSpeedThresholdByWindLevel().keySet())) {
            Map<String, Object> source = stateService.getSpeedThresholdByWindLevel().get(level);
            if (source == null) {
                continue;
            }
            String desc = stateService.stringValue(source.get("windLevelDesc")).trim();
            String key = desc.isEmpty() ? "WL#" + level : desc.toLowerCase(Locale.ROOT);
            if (!seenDesc.add(key)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(source);
            row.remove("dangerousGoodsSpeedLimit");
            rows.add(row);
        }
        return rows;
    }

    /**
     * 更新指定风级限速阈值。
     *
     * @param body 更新请求体
     * @return 更新后的阈值记录
     */
    public Map<String, Object> updateSpeedThreshold(Map<String, Object> body) {
        int windLevel = stateService.intValue(body.get("windLevel"), -1);
        if (windLevel < 1 || windLevel > 12) {
            throw new IllegalArgumentException("windLevel must be between 1 and 12");
        }

        Map<Integer, Map<String, Object>> thresholds = stateService.getSpeedThresholdByWindLevel();
        Map<String, Object> existing = thresholds.computeIfAbsent(windLevel, this::newThresholdByWindLevel);
        int oldControlLevel = stateService.intValue(existing.get("controlLevel"), stateService.mapWindToControlLevel(windLevel));
        String oldWindLevelDesc = stateService.stringValue(existing.get("windLevelDesc")).trim();
        int oldPassenger = stateService.intValue(existing.get("passengerSpeedLimit"), 999);
        int oldFreight = stateService.intValue(existing.get("freightSpeedLimit"), 999);

        int newControlLevel = stateService.intValue(body.get("controlLevel"), oldControlLevel);
        int newPassenger = stateService.intValue(body.get("passengerSpeedLimit"), oldPassenger);
        int newFreight = stateService.intValue(body.get("freightSpeedLimit"), oldFreight);

        if (newControlLevel < 1 || newControlLevel > 5) {
            throw new IllegalArgumentException("controlLevel must be between 1 and 5");
        }

        Map<Integer, Map<String, Object>> controlPlanLibrary = stateService.getControlPlanLibrary();
        Map<String, Object> sourcePlan = new LinkedHashMap<>(controlPlanLibrary.getOrDefault(oldControlLevel, Map.of()));

        String newWindLevelDesc = normalizeWindLevelDesc(newControlLevel);
        List<Integer> affectedWindLevels = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Object>> entry : thresholds.entrySet()) {
            Map<String, Object> row = entry.getValue();
            if (row == null) {
                continue;
            }
            String rowDesc = stateService.stringValue(row.get("windLevelDesc")).trim();
            if (!oldWindLevelDesc.isBlank() && !oldWindLevelDesc.equals(rowDesc)) {
                continue;
            }
            if (oldWindLevelDesc.isBlank() && entry.getKey() != windLevel) {
                continue;
            }
            row.put("controlLevel", newControlLevel);
            row.put("passengerSpeedLimit", newPassenger);
            row.put("freightSpeedLimit", newFreight);
            row.put("controlLevelName", levelName(newControlLevel));
            row.put("windLevelDesc", newWindLevelDesc);
            row.put("dangerousGoodsSpeedLimit", newFreight);
            affectedWindLevels.add(entry.getKey());
        }

        if (affectedWindLevels.isEmpty()) {
            existing.put("controlLevel", newControlLevel);
            existing.put("passengerSpeedLimit", newPassenger);
            existing.put("freightSpeedLimit", newFreight);
            existing.put("controlLevelName", levelName(newControlLevel));
            existing.put("windLevelDesc", newWindLevelDesc);
            existing.put("dangerousGoodsSpeedLimit", newFreight);
            affectedWindLevels.add(windLevel);
        }

        if (newControlLevel < oldControlLevel && !sourcePlan.isEmpty()) {
            for (int level = newControlLevel; level <= oldControlLevel; level++) {
                Map<String, Object> target = controlPlanLibrary.get(level);
                if (target == null) {
                    continue;
                }
                target.put("riskSectionPlan", sourcePlan.get("riskSectionPlan"));
                target.put("upstreamExitPlan", sourcePlan.get("upstreamExitPlan"));
                target.put("upstreamEntryPlan", sourcePlan.get("upstreamEntryPlan"));
                target.put("upstreamServiceAreaPlan", sourcePlan.get("upstreamServiceAreaPlan"));
                target.put("description", sourcePlan.get("riskSectionPlan"));
            }
        }

        stateService.persistSnapshot();
        boolean speedThresholdStaticUpdated = speedThresholdStaticService.updateEnabledByWindLevel(
                windLevel,
                newPassenger,
                newFreight
        );

        Map<String, Object> result = new LinkedHashMap<>(existing);
        result.remove("dangerousGoodsSpeedLimit");
        result.put("affectedWindLevels", affectedWindLevels.stream().sorted().toList());
        result.put("speedThresholdStaticUpdated", speedThresholdStaticUpdated);
        try {
            result.put("appSync", windRiskSpeedService.syncSpeedLimitAfterThresholdUpdate(null));
        } catch (Exception e) {
            Map<String, Object> syncFail = new LinkedHashMap<>();
            syncFail.put("synced", false);
            syncFail.put("message", "sync app speed limits failed: " + e.getMessage());
            result.put("appSync", syncFail);
        }
        return result;
    }

    /**
     * 按管控等级编辑风力阈值映射。
     */
    public Map<String, Object> updateSpeedThresholdByControlLevel(int controlLevel, Map<String, Object> body) {
        if (controlLevel < 1 || controlLevel > 5) {
            throw new IllegalArgumentException("controlLevel must be between 1 and 5");
        }
        String windLevelDesc = stateService.stringValue(body == null ? null : body.get("windLevelDesc")).trim();
        if (windLevelDesc.isBlank()) {
            throw new IllegalArgumentException("windLevelDesc is required");
        }

        int sourceLevel = resolveControlLevelByWindLevelDesc(windLevelDesc);
        if (sourceLevel <= 0) {
            throw new IllegalArgumentException("windLevelDesc not found: " + windLevelDesc);
        }

        Integer reqPassenger = toNullableInt(body == null ? null : body.get("passengerSpeedLimit"));
        Integer reqFreight = toNullableInt(body == null ? null : body.get("freightSpeedLimit"));

        List<Integer> affectedWindLevels = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Object>> entry : stateService.getSpeedThresholdByWindLevel().entrySet()) {
            int windLevel = entry.getKey();
            Map<String, Object> row = entry.getValue();
            if (row == null) {
                continue;
            }
            int rowLevel = stateService.intValue(row.get("controlLevel"), -1);
            if (rowLevel != sourceLevel) {
                continue;
            }

            int oldPassenger = stateService.intValue(row.get("passengerSpeedLimit"), 999);
            int oldFreight = stateService.intValue(row.get("freightSpeedLimit"), 999);
            int newPassenger = reqPassenger == null ? oldPassenger : reqPassenger;
            int newFreight = reqFreight == null ? oldFreight : reqFreight;

            row.put("controlLevel", controlLevel);
            row.put("controlLevelName", levelName(controlLevel));
            row.put("windLevelDesc", windLevelDesc);
            row.put("passengerSpeedLimit", newPassenger);
            row.put("freightSpeedLimit", newFreight);
            row.put("dangerousGoodsSpeedLimit", newFreight);
            affectedWindLevels.add(windLevel);
        }

        if (affectedWindLevels.isEmpty()) {
            throw new IllegalArgumentException("no wind level rows matched windLevelDesc: " + windLevelDesc);
        }

        Map<Integer, Map<String, Object>> controlPlanLibrary = stateService.getControlPlanLibrary();
        Map<String, Object> targetPlan = controlPlanLibrary.get(controlLevel);
        if (targetPlan != null && sourceLevel > controlLevel) {
            for (int level = controlLevel; level <= sourceLevel; level++) {
                Map<String, Object> plan = controlPlanLibrary.get(level);
                if (plan == null) {
                    continue;
                }
                plan.put("riskSectionPlan", targetPlan.get("riskSectionPlan"));
                plan.put("upstreamExitPlan", targetPlan.get("upstreamExitPlan"));
                plan.put("upstreamEntryPlan", targetPlan.get("upstreamEntryPlan"));
                plan.put("upstreamServiceAreaPlan", targetPlan.get("upstreamServiceAreaPlan"));
                plan.put("description", targetPlan.get("riskSectionPlan"));
            }
        }

        stateService.persistSnapshot();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("controlLevel", controlLevel);
        result.put("windLevelDesc", windLevelDesc);
        result.put("affectedWindLevels", affectedWindLevels.stream().sorted().toList());
        result.put("affectedControlLevels", sourceLevel > controlLevel ? buildLevelRange(controlLevel, sourceLevel) : List.of(controlLevel));
        if (reqPassenger != null) {
            result.put("passengerSpeedLimit", reqPassenger);
        }
        if (reqFreight != null) {
            result.put("freightSpeedLimit", reqFreight);
        }
        try {
            result.put("appSync", windRiskSpeedService.syncSpeedLimitAfterThresholdUpdate(null));
        } catch (Exception e) {
            Map<String, Object> syncFail = new LinkedHashMap<>();
            syncFail.put("synced", false);
            syncFail.put("message", "sync app speed limits failed: " + e.getMessage());
            result.put("appSync", syncFail);
        }
        return result;
    }

    /**
     * 风力时空影响研判（4.2.3）。
     *
     * 数据融合逻辑：
     * 1. maxWindLevel：仅基于 wind_data（实时/未来2h）；
     * 2. currentControlLevel：优先基于 wind_data.control_level；缺失时按当前 wind_speed 计算并回写 wind_data；
     * 3. trafficVolumeVehPerHour：仅基于轨迹聚合服务；
     * 4. 风速、交通量等缺失字段返回 null，不做模拟兜底。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param periodType real/future2h/all（兼容 future4h 入参）
     * @param direction 可选方向：1/2
     * @return 研判结果
     */
    public Map<String, Object> evaluateSpatiotemporalImpact(long timestamp, String periodType, Integer direction) {
        String normalizedPeriodType = periodType == null ? "all" : periodType.toLowerCase(Locale.ROOT);
        if ("future4h".equals(normalizedPeriodType)) {
            normalizedPeriodType = "future2h";
        }
        if (!"real".equals(normalizedPeriodType)
                && !"future2h".equals(normalizedPeriodType)
                && !"all".equals(normalizedPeriodType)) {
            normalizedPeriodType = "all";
        }
        Integer normalizedDirection = normalizeDirection(direction);
        LocalDateTime now = toLocalDateTime(timestamp);

        List<WindData> latestRows = windDataService.listLatestSnapshot(now);
        List<WindData> future2hRows = windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_2H_MS));

        List<Map<String, Object>> records = new ArrayList<>();
        for (Integer dir : List.of(DIRECTION_HAMI, DIRECTION_TURPAN)) {
            if (normalizedDirection != null && !normalizedDirection.equals(dir)) {
                continue;
            }
            for (String stakeRange : fixedImpactStakeRangesByDirection(dir)) {
                Map<String, Object> realRecord = buildImpactRecord(stakeRange, stakeRange, timestamp, "real", dir, latestRows, future2hRows);
                realRecord.put("stakeRange", stakeRange);
                Map<String, Object> future2hRecord = buildImpactRecord(stakeRange, stakeRange, timestamp, "future2h", dir, latestRows, future2hRows);
                future2hRecord.put("stakeRange", stakeRange);
                if ("real".equals(normalizedPeriodType)) {
                    records.add(realRecord);
                } else if ("future2h".equals(normalizedPeriodType)) {
                    records.add(future2hRecord);
                } else {
                    records.add(realRecord);
                    records.add(future2hRecord);
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("periodType", normalizedPeriodType);
        data.put("records", records);
        data.put("appSpeedPublishRecords",
                buildAppSpeedPublishRecords(timestamp, normalizedPeriodType, latestRows, future2hRows));
        return data;
    }

    /**
     * 4.2.3 实时研判（固定三段、双向，共 6 组）。
     */
    public Map<String, Object> evaluateSpatiotemporalImpactReal(long timestamp) {
        return evaluateSpatiotemporalImpact(timestamp, "real", null);
    }

    /**
     * 4.2.3 未来 2 小时研判（固定三段、双向，共 6 组）。
     */
    public Map<String, Object> evaluateSpatiotemporalImpactFuture2h(long timestamp) {
        return evaluateSpatiotemporalImpact(timestamp, "future2h", null);
    }

    /**
     * 查询大风观测/历史/预测序列（4.2.4）。
     *
     * 仅从 wind_data 拉取并聚合为时间序列；无数据时返回空 records。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param period real/history/forecast
     * @param direction 可选方向：1/2
     * @return 风数据序列
     */
    public Map<String, Object> queryWindData(long timestamp, String period, Integer direction) {
        String p = period == null ? "real" : period.toLowerCase(Locale.ROOT);
        if (!"real".equals(p) && !"history".equals(p) && !"forecast".equals(p)) {
            p = "real";
        }

        Integer normalizedDirection = normalizeDirection(direction);
        LocalDateTime now = toLocalDateTime(timestamp);
        List<WindData> rows;
        List<WindData> durationRows;
        if ("real".equals(p)) {
            rows = windDataService.listLatestSnapshot(now);
            List<WindData> futureRows = windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_72H_MS));
            durationRows = mergeWindRows(rows, futureRows);
        } else if ("history".equals(p)) {
            rows = windDataService.listByTimeRange(toLocalDateTime(timestamp - WINDOW_24H_MS), now);
            durationRows = rows;
        } else {
            // forecast 仅返回“目标时间戳”对应的一帧；前端通过 timestamp 传入当前+N小时。
            rows = windDataService.listLatestSnapshot(now);
            // 持续时间仍需看后续变化，故单独取目标时刻之后的序列。
            durationRows = windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_72H_MS));
        }

        List<Map<String, Object>> recordList = buildWindQueryRecordsFromDb(rows, durationRows, normalizedDirection, p);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("period", p);
        data.put("records", recordList);
        return data;
    }

    /**
     * 阻断时长预测（4.2.5）。
     *
     * 仅从 wind_data 最新快照统计“严重风区段数”（风级>=11）。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 预测结果
     */
    public Map<String, Object> predictBlockDuration(long timestamp) {
        LocalDateTime now = toLocalDateTime(timestamp);
        List<WindData> latestRows = windDataService.listLatestSnapshot(now);
        List<WindData> future72hRows = windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_72H_MS));

        int severeCount = 0;
        for (WindData row : latestRows) {
            Integer level = toWindLevel(row.getWindSpeed());
            if (level != null && level >= 11) {
                severeCount++;
            }
        }
        int sustainedHours = estimateMaxContinuousSevereHours(now, future72hRows);
        int predictedMinutes = Math.max(severeCount * 25, sustainedHours * 60);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("severeSegmentCount", severeCount);
        data.put("maxContinuousSevereHours", sustainedHours);
        data.put("predictedBlockDurationMin", predictedMinutes);
        return data;
    }

    /**
     * 查询 APP 限速发布数据（4.2.3 扩展）。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @param periodType real/future2h/all
     * @param direction 可选方向：1/2
     * @return APP 限速发布数据
     */
    public Map<String, Object> queryAppSpeedPublish(long timestamp, String periodType, Integer direction) {
        String normalizedPeriodType = periodType == null ? "all" : periodType.toLowerCase(Locale.ROOT);
        if ("future4h".equals(normalizedPeriodType)) {
            normalizedPeriodType = "future2h";
        }
        if (!"real".equals(normalizedPeriodType)
                && !"future2h".equals(normalizedPeriodType)
                && !"all".equals(normalizedPeriodType)) {
            normalizedPeriodType = "all";
        }
        Integer normalizedDirection = normalizeDirection(direction);

        LocalDateTime now = toLocalDateTime(timestamp);
        List<WindData> latestRows = windDataService.listLatestSnapshot(now);
        List<WindData> future2hRows = windDataService.listByTimeRange(now, toLocalDateTime(timestamp + WINDOW_2H_MS));

        List<Map<String, Object>> records = buildAppSpeedPublishRecords(
                timestamp, normalizedPeriodType, latestRows, future2hRows
        );
        if (normalizedDirection != null) {
            records = records.stream()
                    .filter(row -> stateService.intValue(row.get("direction"), 0) == normalizedDirection)
                    .collect(Collectors.toList());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("periodType", normalizedPeriodType);
        data.put("records", records);
        return data;
    }

    /**
     * 构建单条区间研判记录。
     */
    private Map<String, Object> buildImpactRecord(String intervalName,
                                                  String stakeRange,
                                                  long timestamp,
                                                   String periodType,
                                                   int direction,
                                                   List<WindData> latestRows,
                                                   List<WindData> future2hRows) {
        Integer maxWind = "future2h".equals(periodType)
                ? resolveMaxWindLevelFromRows(future2hRows, stakeRange, direction)
                : resolveMaxWindLevelFromRows(latestRows, stakeRange, direction);
        Integer recommendedLevel = maxWind == null ? null : resolveConfiguredControlLevel(maxWind);
        Integer currentLevel = resolveCurrentControlLevelFromRows(latestRows, stakeRange, direction);
        Integer trafficVolume = trajectoryService.estimateTrafficVolumeVehPerHour(timestamp, stakeRange, direction);
        Boolean needAdjust = recommendedLevel != null && currentLevel != null && !recommendedLevel.equals(currentLevel);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("controlInterval", intervalName);
        record.put("stakeRange", stakeRange);
        record.put("direction", direction);
        record.put("baseTime", timestamp);
        record.put("periodType", periodType);
        record.put("trafficVolumeVehPerHour", trafficVolume);
        record.put("maxWindLevel", maxWind);
        record.put("recommendedControlLevel", recommendedLevel);
        record.put("currentControlLevel", currentLevel);
        record.put("needAdjust", needAdjust);
        return record;
    }

    /**
     * 获取研判区间列表。
     *
     * 优先读取 stateService 的 dispatchPlanLibrary（其初始化已优先来自 control_interval_static），
     * 若无可用区间，视为静态表缺失并抛出异常。
     */
    private List<Map<String, Object>> listControlIntervals() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> plan : stateService.getDispatchPlanLibrary().values()) {
            String startStake = stateService.stringValue(plan.get("startStake"));
            String endStake = stateService.stringValue(plan.get("endStake"));
            if (startStake.isBlank() || endStake.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", stateService.stringValue(plan.get("segment")));
            row.put("stakeRange", startStake + "-" + endStake);
            if (plan.containsKey("direction")) {
                row.put("direction", stateService.intValue(plan.get("direction"), 0));
            }
            rows.add(row);
        }

        if (!rows.isEmpty()) {
            return rows;
        }
        throw new IllegalStateException("dispatchPlanLibrary 为空，请先初始化 control_interval_static 并完成状态快照加载。");
    }

    /**
     * 生成缺省阈值行。
     */
    private Map<String, Object> newThresholdByWindLevel(int windLevel) {
        int controlLevel = stateService.mapWindToControlLevel(windLevel);
        Map<String, Object> plan = stateService.getControlPlanLibrary().get(controlLevel);
        int passenger = plan == null ? Math.max(30, 80 - (windLevel - 7) * 10) : stateService.intValue(plan.get("passengerSpeedLimit"), 80);
        int freight = plan == null ? Math.max(20, 70 - (windLevel - 7) * 10) : stateService.intValue(plan.get("freightSpeedLimit"), 60);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("windLevel", windLevel);
        row.put("controlLevel", controlLevel);
        row.put("controlLevelName", levelName(controlLevel));
        row.put("windLevelDesc", normalizeWindLevelDesc(controlLevel));
        row.put("passengerSpeedLimit", Math.max(0, passenger));
        row.put("freightSpeedLimit", Math.max(0, freight));
        row.put("dangerousGoodsSpeedLimit", Math.max(0, freight));
        return row;
    }

    private int resolveControlLevelByWindLevelDesc(String windLevelDesc) {
        String token = windLevelDesc.trim().toLowerCase(Locale.ROOT);
        Map<Integer, Integer> counterByLevel = new HashMap<>();
        for (Map<String, Object> row : stateService.getSpeedThresholdByWindLevel().values()) {
            if (row == null) {
                continue;
            }
            String desc = stateService.stringValue(row.get("windLevelDesc")).trim().toLowerCase(Locale.ROOT);
            if (!token.equals(desc)) {
                continue;
            }
            int level = stateService.intValue(row.get("controlLevel"), -1);
            if (level > 0) {
                counterByLevel.put(level, counterByLevel.getOrDefault(level, 0) + 1);
            }
        }
        int matchedLevel = -1;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : counterByLevel.entrySet()) {
            int level = entry.getKey();
            int count = entry.getValue();
            if (count > bestCount) {
                bestCount = count;
                matchedLevel = level;
            }
        }
        return matchedLevel;
    }

    private Integer toNullableInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        return Integer.parseInt(s);
    }

    private List<Integer> buildLevelRange(int start, int end) {
        List<Integer> levels = new ArrayList<>();
        for (int level = start; level <= end; level++) {
            levels.add(level);
        }
        return levels;
    }

    private String normalizeWindLevelDesc(int controlLevel) {
        return switch (controlLevel) {
            case 1 -> "12级";
            case 2 -> "11级";
            case 3 -> "9-10级";
            case 4 -> "7-8级";
            default -> "7级以下";
        };
    }

    private String levelName(int level) {
        return switch (level) {
            case 1 -> "红色警戒";
            case 2 -> "橙色警戒";
            case 3 -> "黄色警戒";
            case 4 -> "蓝色警戒";
            case 5 -> "正常通行";
            default -> "未知";
        };
    }

    /**
     * 方向参数标准化。
     */
    private Integer normalizeDirection(Integer direction) {
        if (direction == null) {
            return null;
        }
        if (direction != DIRECTION_HAMI && direction != DIRECTION_TURPAN) {
            throw new IllegalArgumentException("direction must be 1(hami) or 2(turpan)");
        }
        return direction;
    }

    private String directionText(int direction) {
        return direction == DIRECTION_TURPAN ? "吐鲁番方向" : "哈密方向";
    }

    /**
     * 将 wind_data.direction 标准化为 1/2。
     */
    private int normalizeDirection(String directionText) {
        if (directionText == null || directionText.isBlank()) {
            return 0;
        }
        String s = directionText.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(s) || "下行".equals(s) || "哈密".equals(s) || "hami".equals(s) || "towh".equals(s) || "to_wh".equals(s)) {
            return DIRECTION_HAMI;
        }
        if ("2".equals(s) || "上行".equals(s) || "吐鲁番".equals(s) || "turpan".equals(s) || "toez".equals(s) || "to_ez".equals(s)) {
            return DIRECTION_TURPAN;
        }
        return 0;
    }

    /**
     * 判断路段与目标桩号区间是否重叠。
     */
    private boolean inStakeRange(String segmentName, String stakeRange) {
        double[] segment = parseRange(segmentName);
        double[] target = parseRange(stakeRange);
        if (segment == null || target == null) {
            return false;
        }
        boolean segmentPoint = segment[0] == segment[1];
        boolean targetPoint = target[0] == target[1];
        if (segmentPoint || targetPoint) {
            double point = segmentPoint ? segment[0] : target[0];
            double[] range = segmentPoint ? target : segment;
            return point >= range[0] && point <= range[1];
        }
        return segment[1] > target[0] && segment[0] < target[1];
    }

    /**
     * 从文本中提取桩号范围。
     */
    private double[] parseRange(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = STAKE_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
        List<Double> values = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token.contains("+")) {
                String[] parts = token.split("\\+");
                values.add(Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]) / 1000.0);
            } else {
                values.add(Double.parseDouble(token));
            }
        }
        if (values.size() < 2) {
            return null;
        }
        double start = Math.min(values.get(0), values.get(1));
        double end = Math.max(values.get(0), values.get(1));
        return new double[]{start, end};
    }

    private List<Double> parseOrderedStakeValues(String text) {
        if (text == null) {
            return List.of();
        }
        Matcher matcher = STAKE_PATTERN.matcher(text.toUpperCase(Locale.ROOT));
        List<Double> values = new ArrayList<>();
        while (matcher.find()) {
            Double value = parseStakeValue("K" + matcher.group(1));
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * 时间戳转本地时间。
     */
    private LocalDateTime toLocalDateTime(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 将风速（m/s）映射为风级（1-12）。
     *
     * 采用近似蒲福风级阈值。
     */
    private Integer toWindLevel(BigDecimal windSpeed) {
        if (windSpeed == null) {
            return null;
        }
        double v = windSpeed.doubleValue();
        if (v >= 32.7) return 12;
        if (v >= 28.5) return 11;
        if (v >= 24.5) return 10;
        if (v >= 20.8) return 9;
        if (v >= 17.2) return 8;
        if (v >= 13.9) return 7;
        if (v >= 10.8) return 6;
        if (v >= 8.0) return 5;
        if (v >= 5.5) return 4;
        if (v >= 3.4) return 3;
        if (v >= 1.6) return 2;
        if (v >= 0.3) return 1;
        return 1;
    }

    /**
     * 根据风级映射前端色块颜色。
     */
    private String colorByWindLevel(int windLevel) {
        if (windLevel >= 12) {
            return "red";
        }
        if (windLevel >= 11) {
            return "orange";
        }
        if (windLevel >= 9) {
            return "yellow";
        }
        if (windLevel >= 7) {
            return "blue";
        }
        return "green";
    }

    /**
     * 从 wind_data 行集中解析某区间某方向的最大风级。
     */
    private Integer resolveMaxWindLevelFromRows(List<WindData> rows, String targetStakeRange, int direction) {
        Integer max = null;
        for (WindData row : rows) {
            if (normalizeDirection(row.getDirection()) != direction) {
                continue;
            }
            if (!inStakeRange(rowStakeRange(row), targetStakeRange)) {
                continue;
            }
            Integer level = toWindLevel(row.getWindSpeed());
            if (level == null) {
                continue;
            }
            max = max == null ? level : Math.max(max, level);
        }
        return max;
    }

    /**
     * 从未来窗口中解析最接近 targetTime 的预测风级（用于 forecast 模式）。
     */
    private int resolveFinalControlLevel(List<WindData> latestRows,
                                         List<WindData> future2hRows,
                                         int direction,
                                         String targetStakeRange) {
        Integer finalWindLevel = resolveFinalWindLevel(latestRows, future2hRows, direction, targetStakeRange);
        return finalWindLevel == null
                ? stateService.getDefaultControlLevel()
                : resolveConfiguredControlLevel(finalWindLevel);
    }

    private Integer resolveFinalWindLevel(List<WindData> latestRows,
                                          List<WindData> future2hRows,
                                          int direction,
                                          String targetStakeRange) {
        Integer realWindLevel = resolveMaxWindLevelFromRows(latestRows, targetStakeRange, direction);
        Integer futureWindLevel = resolveMaxWindLevelFromRows(future2hRows, targetStakeRange, direction);
        Integer finalWindLevel = null;
        if (realWindLevel != null) {
            finalWindLevel = realWindLevel;
        }
        if (futureWindLevel != null && (finalWindLevel == null || futureWindLevel > finalWindLevel)) {
            finalWindLevel = futureWindLevel;
        }
        return finalWindLevel;
    }

    private String resolveControlIntervalColor(List<WindData> latestRows,
                                               List<WindData> future2hRows,
                                               int direction,
                                               String controlInterval,
                                               String displayStakeRange) {
        String colorStakeRange = resolveControlIntervalStatusStakeRange(controlInterval, displayStakeRange);
        Integer windLevel = resolveFinalWindLevel(latestRows, future2hRows, direction, colorStakeRange);
        return colorByWindLevel(windLevel == null ? 1 : windLevel);
    }

    private String resolveControlIntervalStatusStakeRange(String controlInterval, String displayStakeRange) {
        return switch (stateService.stringValue(controlInterval)) {
            case "2-1" -> "K3178-K3192";
            case "2-3" -> "K3197-K3204";
            default -> displayStakeRange;
        };
    }

    private int resolveConfiguredControlLevel(int windLevel) {
        Map<String, Object> threshold = stateService.getSpeedThresholdByWindLevel().get(windLevel);
        if (threshold == null) {
            return stateService.mapWindToControlLevel(windLevel);
        }
        int controlLevel = stateService.intValue(threshold.get("controlLevel"), -1);
        return controlLevel >= 1 && controlLevel <= 5
                ? controlLevel
                : stateService.mapWindToControlLevel(windLevel);
    }

    private Integer resolveForecastWindLevelFromRows(List<WindData> rows,
                                                     String targetStakeRange,
                                                     int direction,
                                                     LocalDateTime targetTime) {
        WindData best = null;
        long bestDistance = Long.MAX_VALUE;
        for (WindData row : rows) {
            if (normalizeDirection(row.getDirection()) != direction) {
                continue;
            }
            if (!inStakeRange(rowStakeRange(row), targetStakeRange)) {
                continue;
            }
            if (row.getTimeStamp() == null) {
                continue;
            }
            long distance = Math.abs(Duration.between(targetTime, row.getTimeStamp()).toMillis());
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && row.getTimeStamp().isBefore(best.getTimeStamp()))) {
                best = row;
                bestDistance = distance;
            }
        }
        return best == null ? null : toWindLevel(best.getWindSpeed());
    }

    /**
     * 从 wind_data 行集中解析某区间某方向的当前控制等级（取最严格等级）。
     *
     * 若历史/实时入库没有写 control_level，则按该行 wind_speed 计算等级并回写。
     */
    private Integer resolveCurrentControlLevelFromRows(List<WindData> rows, String targetStakeRange, int direction) {
        Integer level = null;
        for (WindData row : rows) {
            if (normalizeDirection(row.getDirection()) != direction) {
                continue;
            }
            if (!inStakeRange(rowStakeRange(row), targetStakeRange)) {
                continue;
            }
            Integer rowLevel = resolvePersistedOrCalculatedControlLevel(row);
            if (rowLevel == null || rowLevel <= 0) {
                continue;
            }
            level = level == null ? rowLevel : Math.min(level, rowLevel);
        }
        return level;
    }

    private Integer resolvePersistedOrCalculatedControlLevel(WindData row) {
        Integer persisted = row.getControlLevel();
        if (persisted != null && persisted > 0) {
            return persisted;
        }

        Integer windLevel = toWindLevel(row.getWindSpeed());
        if (windLevel == null) {
            return null;
        }

        int calculated = resolveConfiguredControlLevel(windLevel);
        row.setControlLevel(calculated);
        row.setUpdateTime(LocalDateTime.now());
        windDataService.upsert(row);
        return calculated;
    }

    /**
     * 将 wind_data 的起止桩号拼成 stakeRange。
     */
    private String rowStakeRange(WindData row) {
        return stateService.stringValue(row.getStartStake()) + "-" + stateService.stringValue(row.getEndStake());
    }

    /**
     * 将 wind_data 结果聚合为 queryWindData 接口格式。
     */
    private List<Map<String, Object>> buildWindQueryRecordsFromDb(List<WindData> rows,
                                                                   List<WindData> durationRows,
                                                                   Integer direction,
                                                                   String period) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<WindAgg> aggList = buildWindAggList(rows, direction);
        aggList.sort(Comparator
                .comparing((WindAgg a) -> a.time)
                .thenComparingInt(a -> a.direction)
                .thenComparing((a, b) -> Double.compare(b.stakeSortValue, a.stakeSortValue)));

        List<Map<String, Object>> records = new ArrayList<>();
        int defaultDurationMin = "real".equals(period) ? 5 : 60;
        List<WindAgg> durationAggList = buildWindAggList(durationRows, direction);
        Map<String, Integer> durationByAggKey = computeDynamicDurationMinutes(
                aggList,
                durationAggList,
                defaultDurationMin
        );
        for (WindAgg agg : aggList) {
            String aggKey = buildAggKey(agg);
            int durationMin = durationByAggKey.getOrDefault(aggKey, defaultDurationMin);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", agg.time.format(DATETIME_FMT));
            row.put("direction", agg.direction);
            row.put("segmentId", agg.segmentId);
            row.put("segmentName", agg.segmentName);
            row.put("startStake", agg.startStake);
            row.put("endStake", agg.endStake);
            row.put("stakeRange", agg.stakeRange);
            row.put("windLevel", agg.maxWindLevel);
            row.put("windLevelDesc", toWindLevelDisplayText(agg.maxWindLevel));
            row.put("color", colorByWindLevel(agg.maxWindLevel));
            row.put("windDirection", toChineseWindDirection(agg.windDirection));
            row.put("durationMin", durationMin);
            records.add(row);
        }
        return records;
    }

    /**
     * 持续时长计算：
     * 对每个“方向+路段范围”序列，取当前风级到下一次风级变化的时间差（分钟）。
     */
    private Map<String, Integer> computeDynamicDurationMinutes(List<WindAgg> currentAggList,
                                                               List<WindAgg> durationAggList,
                                                               int fallbackDurationMin) {
        if (currentAggList == null || currentAggList.isEmpty()) {
            return Map.of();
        }
        Map<String, List<WindAgg>> group = new LinkedHashMap<>();
        List<WindAgg> effectiveDurationList = (durationAggList == null || durationAggList.isEmpty())
                ? currentAggList
                : durationAggList;
        for (WindAgg agg : effectiveDurationList) {
            String key = agg.direction + "|" + agg.stakeRange;
            group.computeIfAbsent(key, k -> new ArrayList<>()).add(agg);
        }

        Map<String, Integer> durationByAggKey = new HashMap<>();
        for (List<WindAgg> series : group.values()) {
            series.sort(Comparator.comparing(a -> a.time));
            int stepMin = detectStepMinutes(series, fallbackDurationMin);
            for (WindAgg current : currentAggList) {
                String seriesKey = current.direction + "|" + current.stakeRange;
                if (!seriesKey.equals(series.get(0).direction + "|" + series.get(0).stakeRange)) {
                    continue;
                }
                int startIdx = findStartIndex(series, current.time);
                if (startIdx < 0) {
                    durationByAggKey.put(buildAggKey(current), fallbackDurationMin);
                    continue;
                }
                int nextChangeIndex = -1;
                for (int j = startIdx + 1; j < series.size(); j++) {
                    WindAgg next = series.get(j);
                    if (!equalsInt(current.maxWindLevel, next.maxWindLevel)) {
                        nextChangeIndex = j;
                        break;
                    }
                }

                int durationMin;
                if (nextChangeIndex > 0) {
                    durationMin = (int) Duration.between(current.time, series.get(nextChangeIndex).time).toMinutes();
                } else {
                    WindAgg last = series.get(series.size() - 1);
                    durationMin = (int) Duration.between(current.time, last.time).toMinutes() + stepMin;
                }
                if (durationMin <= 0) {
                    durationMin = fallbackDurationMin;
                }
                durationByAggKey.put(buildAggKey(current), durationMin);
            }
        }
        return durationByAggKey;
    }

    private int findStartIndex(List<WindAgg> series, LocalDateTime time) {
        if (series == null || series.isEmpty() || time == null) {
            return -1;
        }
        for (int i = 0; i < series.size(); i++) {
            if (series.get(i).time.equals(time)) {
                return i;
            }
            if (series.get(i).time.isAfter(time)) {
                return i;
            }
        }
        return series.size() - 1;
    }

    private List<WindAgg> buildWindAggList(List<WindData> rows, Integer direction) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, WindAgg> aggMap = new LinkedHashMap<>();
        for (WindData row : rows) {
            int rowDirection = normalizeDirection(row.getDirection());
            if (rowDirection == 0) {
                continue;
            }
            if (direction != null && rowDirection != direction) {
                continue;
            }
            if (row.getTimeStamp() == null) {
                continue;
            }
            Integer level = toWindLevel(row.getWindSpeed());
            if (level == null) {
                continue;
            }
            String sourceStakeRange = rowStakeRange(row);
            for (Map<String, Object> section : stateService.getFullLineWindSections()) {
                int sectionDirection = stateService.intValue(section.get("direction"), 0);
                if (sectionDirection != rowDirection) {
                    continue;
                }
                String sectionStakeRange = sectionStakeRange(section);
                if (sectionStakeRange.isBlank() || !inStakeRange(sourceStakeRange, sectionStakeRange)) {
                    continue;
                }
                DisplaySection display = displaySection(section);
                String key = row.getTimeStamp().format(DATETIME_FMT) + "#" + rowDirection + "#" + sectionStakeRange;
                WindAgg agg = aggMap.computeIfAbsent(key, k -> new WindAgg(
                        row.getTimeStamp(),
                        rowDirection,
                        display.segmentId,
                        display.segmentName,
                        display.stakeRange,
                        display.startStake,
                        display.endStake,
                        display.sortValue
                ));
                if (agg.maxWindLevel == null || level > agg.maxWindLevel) {
                    agg.maxWindLevel = level;
                    agg.windDirection = stateService.stringValue(row.getWindDirection());
                }
            }
        }
        return new ArrayList<>(aggMap.values());
    }

    private List<WindData> mergeWindRows(List<WindData> a, List<WindData> b) {
        List<WindData> merged = new ArrayList<>();
        if (a != null && !a.isEmpty()) {
            merged.addAll(a);
        }
        if (b != null && !b.isEmpty()) {
            merged.addAll(b);
        }
        return merged;
    }

    private int detectStepMinutes(List<WindAgg> series, int fallbackDurationMin) {
        if (series == null || series.size() < 2) {
            return fallbackDurationMin;
        }
        int minPositive = Integer.MAX_VALUE;
        for (int i = 1; i < series.size(); i++) {
            int diff = (int) Duration.between(series.get(i - 1).time, series.get(i).time).toMinutes();
            if (diff > 0 && diff < minPositive) {
                minPositive = diff;
            }
        }
        return minPositive == Integer.MAX_VALUE ? fallbackDurationMin : minPositive;
    }

    private String buildAggKey(WindAgg agg) {
        return agg.time.format(DATETIME_FMT) + "#" + agg.direction + "#" + agg.stakeRange;
    }

    private boolean equalsInt(Integer a, Integer b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.intValue() == b.intValue();
    }

    /**
     * 生成 APP 可直接消费的限速发布数据（按路段、方向输出）。
     */
    private List<Map<String, Object>> buildAppSpeedPublishRecords(long timestamp,
                                                                  String periodType,
                                                                  List<WindData> latestRows,
                                                                  List<WindData> future2hRows) {
        List<WindData> sourceRows = switch (periodType) {
            case "future2h" -> future2hRows;
            case "all" -> {
                List<WindData> merged = new ArrayList<>();
                if (latestRows != null) {
                    merged.addAll(latestRows);
                }
                if (future2hRows != null) {
                    merged.addAll(future2hRows);
                }
                yield merged;
            }
            default -> latestRows;
        };
        if (sourceRows == null || sourceRows.isEmpty()) {
            return List.of();
        }

        Map<String, Map<String, Object>> segmentMetaByKey = new HashMap<>();
        for (Map<String, Object> section : stateService.getFullLineWindSections()) {
            int direction = stateService.intValue(section.get("direction"), 0);
            String startStake = stateService.stringValue(section.get("startStake"));
            String endStake = stateService.stringValue(section.get("endStake"));
            if (direction <= 0 || startStake.isBlank() || endStake.isBlank()) {
                continue;
            }
            segmentMetaByKey.put(segmentStakeKey(direction, startStake, endStake), section);
        }

        Map<String, Integer> maxWindBySegment = new HashMap<>();
        for (WindData row : sourceRows) {
            int direction = normalizeDirection(row.getDirection());
            if (direction == 0) {
                continue;
            }
            String startStake = stateService.stringValue(row.getStartStake());
            String endStake = stateService.stringValue(row.getEndStake());
            if (startStake.isBlank() || endStake.isBlank()) {
                continue;
            }
            Integer level = toWindLevel(row.getWindSpeed());
            if (level == null) {
                continue;
            }
            String key = direction + "|" + startStake + "|" + endStake;
            Integer current = maxWindBySegment.get(key);
            if (current == null || level > current) {
                maxWindBySegment.put(key, level);
            }
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (Map.Entry<String, Integer> e : maxWindBySegment.entrySet()) {
            String[] parts = e.getKey().split("\\|", -1);
            int direction = Integer.parseInt(parts[0]);
            String startStake = parts[1];
            String endStake = parts[2];
            int windLevel = e.getValue();

            Map<String, Object> threshold = stateService.getSpeedThresholdByWindLevel().get(windLevel);
            Integer passenger = null;
            Integer freight = null;
            if (threshold != null) {
                int p = stateService.intValue(threshold.get("passengerSpeedLimit"), -1);
                int f = stateService.intValue(threshold.get("freightSpeedLimit"), -1);
                passenger = p >= 0 ? p : null;
                freight = f >= 0 ? f : null;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("baseTime", timestamp);
            row.put("periodType", periodType);
            row.put("direction", direction);
            row.put("startStake", startStake);
            row.put("endStake", endStake);
            Map<String, Object> segmentMeta = segmentMetaByKey.get(segmentStakeKey(direction, startStake, endStake));
            if (segmentMeta == null) {
                segmentMeta = segmentMetaByKey.get(segmentStakeKey(direction, endStake, startStake));
            }
            row.put("appSpeedInterval", segmentMeta == null ? null : stateService.stringValue(segmentMeta.get("appSpeedInterval")));
            row.put("controlInterval", segmentMeta == null ? null : stateService.stringValue(segmentMeta.get("controlInterval")));
            row.put("windLevel", windLevel);
            row.put("recommendedControlLevel", resolveConfiguredControlLevel(windLevel));
            row.put("passengerSpeedLimit", passenger);
            row.put("freightSpeedLimit", freight);
            records.add(row);
        }

        records.sort((a, b) -> {
            int d = Integer.compare(stateService.intValue(a.get("direction"), 0), stateService.intValue(b.get("direction"), 0));
            if (d != 0) {
                return d;
            }
            String aStart = stateService.stringValue(a.get("startStake"));
            String bStart = stateService.stringValue(b.get("startStake"));
            return aStart.compareTo(bStart);
        });
        return records;
    }

    private String segmentStakeKey(int direction, String startStake, String endStake) {
        return direction + "|" + startStake.trim().toUpperCase(Locale.ROOT) + "|" + endStake.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStakeText(String stake) {
        if (stake == null || stake.isBlank()) {
            return "";
        }
        String value = stake.trim().toUpperCase(Locale.ROOT);
        if (!value.startsWith("K")) {
            value = "K" + value;
        }
        return value;
    }

    private String toStakeRangeText(String startStake, String endStake) {
        String start = normalizeStakeText(startStake);
        String end = normalizeStakeText(endStake);
        if (start.isBlank() || end.isBlank()) {
            return "";
        }
        return start + "-" + end;
    }

    private List<String> fixedImpactStakeRangesByDirection(int direction) {
        return direction == DIRECTION_TURPAN
                ? FIXED_TURPAN_IMPACT_STAKE_RANGES
                : FIXED_HAMI_IMPACT_STAKE_RANGES;
    }

    private String firstStakeInRange(String stakeRange) {
        String[] parts = splitStakeRange(stakeRange);
        return parts.length > 0 ? parts[0] : "";
    }

    private String lastStakeInRange(String stakeRange) {
        String[] parts = splitStakeRange(stakeRange);
        return parts.length > 1 ? parts[1] : "";
    }

    private String[] splitStakeRange(String stakeRange) {
        if (stakeRange == null || stakeRange.isBlank()) {
            return new String[0];
        }
        String[] parts = stakeRange.split("-", 2);
        if (parts.length < 2) {
            return new String[]{normalizeStakeText(stakeRange)};
        }
        return new String[]{normalizeStakeText(parts[0]), normalizeStakeText(parts[1])};
    }

    private DisplaySection displaySection(Map<String, Object> section) {
        String rawStart = stateService.stringValue(section.get("startStake"));
        String rawEnd = stateService.stringValue(section.get("endStake"));
        String start = normalizeStakeText(rawStart);
        String end = normalizeStakeText(rawEnd);
        Double startValue = parseStakeValue(start);
        Double endValue = parseStakeValue(end);
        if (startValue == null || endValue == null) {
            String segmentName = stateService.stringValue(section.get("segmentName"));
            String stakeRange = toStakeRangeText(start, end);
            return new DisplaySection(toDisplayStakeId(end, start), segmentName, start, end, stakeRange, Double.NEGATIVE_INFINITY);
        }

        String displayStart = startValue <= endValue ? start : end;
        String displayEnd = startValue <= endValue ? end : start;
        double sortValue = Math.min(startValue, endValue);
        String segmentName = displaySegmentName(section, displayStart, displayEnd);
        return new DisplaySection(
                toDisplayStakeId(displayStart, displayEnd),
                segmentName,
                displayStart,
                displayEnd,
                displayStart + "-" + displayEnd,
                sortValue
        );
    }

    private String displaySegmentName(Map<String, Object> section, String startStake, String endStake) {
        String sourceName = stateService.stringValue(section.get("segmentName"));
        String type = "";
        int left = sourceName.lastIndexOf('（');
        int right = sourceName.endsWith("）") ? sourceName.length() - 1 : -1;
        if (left >= 0 && right > left) {
            type = sourceName.substring(left + 1, right);
        }
        String directionName = stateService.intValue(section.get("direction"), DIRECTION_HAMI) == DIRECTION_TURPAN
                ? "吐鲁番"
                : "哈密";
        return type.isBlank()
                ? directionName + " " + startStake + "-" + endStake
                : directionName + " " + startStake + "-" + endStake + "（" + type + "）";
    }

    private int compareSectionRowsByDirectionAndStakeDesc(Map<String, Object> a, Map<String, Object> b) {
        int directionCompare = Integer.compare(
                stateService.intValue(a.get("direction"), 0),
                stateService.intValue(b.get("direction"), 0)
        );
        if (directionCompare != 0) {
            return directionCompare;
        }
        double aValue = stakeSortValue(a.get("startStake"));
        double bValue = stakeSortValue(b.get("startStake"));
        return Double.compare(bValue, aValue);
    }

    private double stakeSortValue(Object stake) {
        Double value = parseStakeValue(stateService.stringValue(stake));
        return value == null ? Double.NEGATIVE_INFINITY : value;
    }

    private String sectionStakeRange(Map<String, Object> section) {
        if (section == null || section.isEmpty()) {
            return "";
        }
        return toStakeRangeText(
                stateService.stringValue(section.get("startStake")),
                stateService.stringValue(section.get("endStake"))
        );
    }

    private String toDisplayStakeId(String primaryStake, String fallbackStake) {
        String stake = normalizeStakeText(primaryStake);
        if (stake.isBlank()) {
            stake = normalizeStakeText(fallbackStake);
        }
        if (stake.isBlank()) {
            return "";
        }
        if (stake.startsWith("K")) {
            return "k" + stake.substring(1);
        }
        return "k" + stake;
    }

    private Double parseStakeValue(String stake) {
        if (stake == null || stake.isBlank()) {
            return null;
        }
        String value = stake.trim().toUpperCase(Locale.ROOT);
        if (value.startsWith("K")) {
            value = value.substring(1);
        }
        try {
            if (value.contains("+")) {
                String[] parts = value.split("\\+", -1);
                if (parts.length != 2) {
                    return null;
                }
                double base = Double.parseDouble(parts[0]);
                double offset = Double.parseDouble(parts[1]);
                return base + offset / 1000D;
            }
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 将风向文本标准化为中文风向。
     */
    private String toChineseWindDirection(String raw) {
        if (raw == null || raw.isBlank()) {
            return "未知";
        }
        String value = raw.trim();
        // 兼容脏数据：多值拼接/换行/分隔符，优先取第一段有效方向码。
        String[] tokens = value.split("[,;/|，；\\s]+");
        String first = tokens.length == 0 ? value : tokens[0];
        String upper = first.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        if ("NORTH".equals(upper)) upper = "N";
        if ("SOUTH".equals(upper)) upper = "S";
        if ("EAST".equals(upper)) upper = "E";
        if ("WEST".equals(upper)) upper = "W";
        return switch (upper) {
            case "N" -> "北风";
            case "NNE" -> "北东北风";
            case "NE" -> "东北风";
            case "ENE" -> "东东北风";
            case "E" -> "东风";
            case "ESE" -> "东东南风";
            case "SE" -> "东南风";
            case "SSE" -> "南东南风";
            case "S" -> "南风";
            case "SSW" -> "南西南风";
            case "SW" -> "西南风";
            case "WSW" -> "西西南风";
            case "W" -> "西风";
            case "WNW" -> "西西北风";
            case "NW" -> "西北风";
            case "NNW" -> "北西北风";
            default -> {
                if (value.contains("风")) {
                    yield value;
                }
                if (value.contains("北") || value.contains("南")
                        || value.contains("东") || value.contains("西")) {
                    yield value + "风";
                }
                yield value;
            }
        };
    }

    /**
     * 风级展示文案（按业务分档，不展示 1~12 细粒度）。
     */
    private String toWindLevelDisplayText(Integer windLevel) {
        if (windLevel == null) {
            return "未知";
        }
        if (windLevel >= 12) {
            return "12级风";
        }
        if (windLevel >= 11) {
            return "11级";
        }
        if (windLevel >= 9) {
            return "9-10级";
        }
        if (windLevel >= 8) {
            return "7-8级";
        }
        return "7级及以下";
    }

    /**
     * 估算未来 72h 内最大连续严重风（11级及以上）时长。
     */
    private int estimateMaxContinuousSevereHours(LocalDateTime now, List<WindData> future72hRows) {
        if (future72hRows == null || future72hRows.isEmpty()) {
            return 0;
        }

        Map<String, List<WindData>> rowsBySegment = new HashMap<>();
        for (WindData row : future72hRows) {
            int direction = normalizeDirection(row.getDirection());
            if (direction == 0 || row.getTimeStamp() == null) {
                continue;
            }
            String startStake = stateService.stringValue(row.getStartStake());
            String endStake = stateService.stringValue(row.getEndStake());
            if (startStake.isBlank() || endStake.isBlank()) {
                continue;
            }
            String key = direction + "|" + startStake + "|" + endStake;
            rowsBySegment.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        int maxHours = 0;
        for (List<WindData> rows : rowsBySegment.values()) {
            rows.sort(Comparator.comparing(WindData::getTimeStamp));
            Set<LocalDateTime> severeHours = new HashSet<>();
            for (WindData row : rows) {
                Integer level = toWindLevel(row.getWindSpeed());
                if (level != null && level >= 11 && !row.getTimeStamp().isBefore(now)) {
                    LocalDateTime hour = row.getTimeStamp().withMinute(0).withSecond(0).withNano(0);
                    severeHours.add(hour);
                }
            }
            if (severeHours.isEmpty()) {
                continue;
            }
            List<LocalDateTime> sortedHours = new ArrayList<>(severeHours);
            sortedHours.sort(LocalDateTime::compareTo);
            int current = 1;
            int best = 1;
            for (int i = 1; i < sortedHours.size(); i++) {
                LocalDateTime prev = sortedHours.get(i - 1);
                LocalDateTime cur = sortedHours.get(i);
                if (prev.plusHours(1).equals(cur)) {
                    current++;
                    if (current > best) {
                        best = current;
                    }
                } else {
                    current = 1;
                }
            }
            if (best > maxHours) {
                maxHours = best;
            }
        }
        return maxHours;
    }

    /**
     * 风序列聚合中间对象。
     */
    private static class WindAgg {
        private final LocalDateTime time;
        private final int direction;
        private final String segmentId;
        private final String segmentName;
        private final String stakeRange;
        private final String startStake;
        private final String endStake;
        private final double stakeSortValue;
        private Integer maxWindLevel;
        private String windDirection;

        private WindAgg(LocalDateTime time,
                        int direction,
                        String segmentId,
                        String segmentName,
                        String stakeRange,
                        String startStake,
                        String endStake,
                        double stakeSortValue) {
            this.time = time;
            this.direction = direction;
            this.segmentId = segmentId;
            this.segmentName = segmentName;
            this.stakeRange = stakeRange;
            this.startStake = startStake;
            this.endStake = endStake;
            this.stakeSortValue = stakeSortValue;
        }
    }

    private static class DisplaySection {
        private final String segmentId;
        private final String segmentName;
        private final String startStake;
        private final String endStake;
        private final String stakeRange;
        private final double sortValue;

        private DisplaySection(String segmentId,
                               String segmentName,
                               String startStake,
                               String endStake,
                               String stakeRange,
                               double sortValue) {
            this.segmentId = segmentId;
            this.segmentName = segmentName;
            this.startStake = startStake;
            this.endStake = endStake;
            this.stakeRange = stakeRange;
            this.sortValue = sortValue;
        }
    }

    private static class VmsDevice {
        private final String line;
        private final String deviceId;
        private final String stake;
        private final String type;

        private VmsDevice(String line, String deviceId, String stake, String type) {
            this.line = line;
            this.deviceId = deviceId;
            this.stake = stake;
            this.type = type;
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
    }

    private static class FixedVmsContent {
        private final String mainContent;
        private final String tipContent;

        private FixedVmsContent(String mainContent, String tipContent) {
            this.mainContent = mainContent == null ? "" : mainContent;
            this.tipContent = tipContent == null ? "" : tipContent;
        }

        private static FixedVmsContent empty() {
            return new FixedVmsContent("", "");
        }

        private String mainContent() {
            return mainContent;
        }

        private String tipContent() {
            return tipContent;
        }
    }
}
