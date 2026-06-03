package com.wut.screenwebsx.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wut.screencommonsx.Request.Wind.CreateVmsContentTemplateReq;
import com.wut.screencommonsx.Request.Wind.RenderVmsContentTemplatePreviewReq;
import com.wut.screencommonsx.Request.Wind.UpdateVmsContentTemplateReq;
import com.wut.screendbmysqlsx.Model.VmsContentTemplateStatic;
import com.wut.screendbmysqlsx.Service.VmsContentTemplateStaticService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可变信息发布内容模板服务（4.4.3）。
 */
@Service
public class VmsContentTemplateService {
    private static final Set<String> PUBLISH_POSITIONS = Set.of(
            "IN_SECTION",
            "UPSTREAM_EXIT",
            "UPSTREAM_ENTRY_TOLL",
            "SERVICE_AREA"
    );
    private static final Set<String> VEHICLE_TYPES = Set.of("ALL", "PASSENGER", "FREIGHT");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([A-Z0-9_]+)}");

    private final VmsContentTemplateStaticService templateService;
    private final WindControlStateService stateService;
    private final ObjectMapper objectMapper;

    public VmsContentTemplateService(VmsContentTemplateStaticService templateService,
                                     WindControlStateService stateService,
                                     ObjectMapper objectMapper) {
        this.templateService = templateService;
        this.stateService = stateService;
        this.objectMapper = objectMapper;
    }

    /**
     * 按管控等级查询模板，返回该等级下全部记录，并附带两条管理措施。
     */
    public Map<String, Object> listByControlLevel(String controlLevel, Integer isEnabled) {
        String level = requireText(controlLevel, "controlLevel is required");
        Integer enabled = isEnabled == null ? 1 : isEnabled;
        if (enabled != 0 && enabled != 1) {
            throw new IllegalArgumentException("isEnabled must be 0 or 1");
        }

        List<VmsContentTemplateStatic> rows = templateService.listByControlLevel(level, enabled);
        List<Map<String, Object>> records = new ArrayList<>();
        for (VmsContentTemplateStatic row : rows) {
            records.addAll(toFacilityViews(row));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("controlLevel", level);
        data.put("total", records.size());
        data.put("records", records);
        data.put("managementMeasures", buildManagementMeasures(level));
        return data;
    }

    /**
     * 按模板编码获取单条详情。
     */
    public Map<String, Object> detailByTemplateCode(String templateCode) {
        VmsContentTemplateStatic row = requireTemplate(templateCode);
        return toView(row);
    }

    /**
     * 新增模板。
     */
    public Map<String, Object> create(CreateVmsContentTemplateReq req) {
        String templateCode = normalizeTemplateCode(req.getTemplateCode());
        if (templateService.getByTemplateCode(templateCode) != null) {
            throw new IllegalArgumentException("templateCode already exists: " + templateCode);
        }

        VmsContentTemplateStatic row = new VmsContentTemplateStatic();
        row.setTemplateCode(templateCode);
        row.setControlLevel(requireText(req.getControlLevel(), "controlLevel is required"));
        row.setPublishPosition(normalizePublishPosition(req.getPublishPosition()));
        row.setVehicleType(normalizeVehicleType(req.getVehicleType()));
        row.setTemplateText(requireText(req.getTemplateText(), "templateText is required"));
        row.setTemplateGraphicJson(normalizeGraphicJson(req.getTemplateGraphicJson()));
        row.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        row.setIsEnabled(req.getIsEnabled() == null ? 1 : req.getIsEnabled());
        if (row.getIsEnabled() != 0 && row.getIsEnabled() != 1) {
            throw new IllegalArgumentException("isEnabled must be 0 or 1");
        }

        if (!templateService.save(row)) {
            throw new IllegalStateException("create template failed");
        }
        return Map.of("templateCode", templateCode);
    }

    /**
     * 更新模板。
     */
    public Map<String, Object> update(String templateCode, UpdateVmsContentTemplateReq req) {
        VmsContentTemplateStatic existing = requireTemplate(templateCode);
        existing.setControlLevel(requireText(req.getControlLevel(), "controlLevel is required"));
        existing.setPublishPosition(normalizePublishPosition(req.getPublishPosition()));
        existing.setVehicleType(normalizeVehicleType(req.getVehicleType()));
        existing.setTemplateText(requireText(req.getTemplateText(), "templateText is required"));
        existing.setTemplateGraphicJson(normalizeGraphicJson(req.getTemplateGraphicJson()));
        existing.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        existing.setIsEnabled(req.getIsEnabled() == null ? 1 : req.getIsEnabled());
        if (existing.getIsEnabled() != 0 && existing.getIsEnabled() != 1) {
            throw new IllegalArgumentException("isEnabled must be 0 or 1");
        }

        if (!templateService.updateById(existing)) {
            throw new IllegalStateException("update template failed");
        }
        return Map.of("templateCode", existing.getTemplateCode());
    }

    /**
     * 更新启停状态。
     */
    public Map<String, Object> updateStatus(String templateCode, int isEnabled) {
        if (isEnabled != 0 && isEnabled != 1) {
            throw new IllegalArgumentException("isEnabled must be 0 or 1");
        }
        VmsContentTemplateStatic existing = requireTemplate(templateCode);
        existing.setIsEnabled(isEnabled);
        if (!templateService.updateById(existing)) {
            throw new IllegalStateException("update template status failed");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("templateCode", existing.getTemplateCode());
        data.put("isEnabled", isEnabled);
        return data;
    }

    /**
     * 删除模板。
     */
    public boolean delete(String templateCode) {
        String code = requireText(templateCode, "templateCode is required").toUpperCase(Locale.ROOT);
        return templateService.removeByTemplateCode(code);
    }

    /**
     * 依据等级、位置、车型匹配模板；车型未命中时回退 ALL。
     */
    public Map<String, Object> matchTemplate(String controlLevel, String publishPosition, String vehicleType) {
        String level = requireText(controlLevel, "controlLevel is required");
        String position = normalizePublishPosition(publishPosition);
        String type = normalizeVehicleType(vehicleType);

        VmsContentTemplateStatic matched = templateService.matchTemplate(level, position, type);
        if (matched == null && !"ALL".equals(type)) {
            matched = templateService.matchTemplate(level, position, "ALL");
        }
        if (matched == null) {
            throw new IllegalArgumentException("template not found");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("templateCode", matched.getTemplateCode());
        data.put("templateText", renderTemplateText(
                matched.getTemplateText(),
                matched.getControlLevel(),
                defaultTemplateVariables(matched.getControlLevel(), matched.getPublishPosition(), null)
        ));
        data.put("templateGraphicJson", parseGraphicJson(matched.getTemplateGraphicJson()));
        return data;
    }

    /**
     * 模板渲染预览。
     */
    public Map<String, Object> renderPreview(RenderVmsContentTemplatePreviewReq req) {
        VmsContentTemplateStatic row = requireTemplate(req.getTemplateCode());
        Map<String, String> variables = req.getVariables() == null ? Map.of() : req.getVariables();
        Map<String, String> mergedVariables = new LinkedHashMap<>(defaultTemplateVariables(row.getControlLevel(), row.getPublishPosition(), null));
        mergedVariables.putAll(variables);

        String renderedText = renderTemplateText(row.getTemplateText(), row.getControlLevel(), mergedVariables);
        List<String> unresolved = extractUnresolvedPlaceholders(renderedText);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("templateCode", row.getTemplateCode());
        data.put("renderedText", renderedText);
        data.put("unresolvedPlaceholders", unresolved);
        return data;
    }

    private Map<String, Object> buildManagementMeasures(String controlLevel) {
        Map<String, Object> plan = resolvePlan(controlLevel);
        if (plan != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("upstreamExitMeasure", value(stateService.stringValue(plan.get("upstreamExitPlan"))));
            m.put("upstreamEntryTollMeasure", value(stateService.stringValue(plan.get("upstreamEntryPlan"))));
            return m;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("upstreamExitMeasure", "");
        m.put("upstreamEntryTollMeasure", "");
        return m;
    }

    private VmsContentTemplateStatic requireTemplate(String templateCode) {
        String code = requireText(templateCode, "templateCode is required").toUpperCase(Locale.ROOT);
        VmsContentTemplateStatic row = templateService.getByTemplateCode(code);
        if (row == null) {
            throw new IllegalArgumentException("templateCode not found: " + code);
        }
        return row;
    }

    private String normalizeTemplateCode(String templateCode) {
        return requireText(templateCode, "templateCode is required").toUpperCase(Locale.ROOT);
    }

    private String normalizePublishPosition(String publishPosition) {
        String value = requireText(publishPosition, "publishPosition is required").toUpperCase(Locale.ROOT);
        if (!PUBLISH_POSITIONS.contains(value)) {
            throw new IllegalArgumentException("publishPosition invalid");
        }
        return value;
    }

    private String normalizeVehicleType(String vehicleType) {
        String value = requireText(vehicleType, "vehicleType is required").toUpperCase(Locale.ROOT);
        if (!VEHICLE_TYPES.contains(value)) {
            throw new IllegalArgumentException("vehicleType invalid");
        }
        return value;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String value(String input) {
        return input == null ? "" : input;
    }

    private String normalizeGraphicJson(Object templateGraphicJson) {
        if (templateGraphicJson == null) {
            return null;
        }
        if (templateGraphicJson instanceof String s && s.isBlank()) {
            return null;
        }
        try {
            JsonNode node = templateGraphicJson instanceof String s
                    ? objectMapper.readTree(s)
                    : objectMapper.valueToTree(templateGraphicJson);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("templateGraphicJson must be valid json");
        }
    }

    private Object parseGraphicJson(String templateGraphicJson) {
        if (templateGraphicJson == null || templateGraphicJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(templateGraphicJson);
        } catch (JsonProcessingException ex) {
            return templateGraphicJson;
        }
    }

    private Map<String, Object> toView(VmsContentTemplateStatic row) {
        return toView(row, null);
    }

    private List<Map<String, Object>> toFacilityViews(VmsContentTemplateStatic row) {
        List<Map<String, Object>> views = new ArrayList<>();
        List<Map<String, Object>> facilities = matchedFacilities(row.getPublishPosition());
        if (facilities.isEmpty()) {
            views.add(toView(row, null));
            return views;
        }
        for (Map<String, Object> facility : facilities) {
            views.add(toView(row, facility));
        }
        return views;
    }

    private Map<String, Object> toView(VmsContentTemplateStatic row, Map<String, Object> facility) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.getId());
        view.put("templateCode", row.getTemplateCode());
        view.put("controlLevel", row.getControlLevel());
        view.put("publishPosition", row.getPublishPosition());
        view.put("vehicleType", row.getVehicleType());
        if (facility != null) {
            view.put("facilityId", stateService.stringValue(facility.get("facilityId")));
            view.put("pileNo", stateService.stringValue(facility.get("pileNo")));
            view.put("direction", stateService.intValue(facility.get("direction"), 0));
            view.put("facilitySegment", stateService.stringValue(facility.get("segment")));
            view.put("interchangeName", stateService.stringValue(facility.get("interchangeName")));
        }
        view.put("roadName", resolveRoadName(row.getPublishPosition(), facility));
        view.put("templateText", renderTemplateText(
                row.getTemplateText(),
                row.getControlLevel(),
                defaultTemplateVariables(row.getControlLevel(), row.getPublishPosition(), facility)
        ));
        view.put("templateGraphicJson", parseGraphicJson(row.getTemplateGraphicJson()));
        view.put("sortNo", row.getSortNo());
        view.put("isEnabled", row.getIsEnabled());
        view.put("createTime", row.getCreateTime());
        view.put("updateTime", row.getUpdateTime());
        return view;
    }

    private String renderTemplateText(String templateText, String controlLevel, Map<String, String> externalVariables) {
        String raw = templateText == null ? "" : templateText;
        Map<String, String> variables = new LinkedHashMap<>(defaultTemplateVariables(controlLevel, "", null));
        if (externalVariables != null && !externalVariables.isEmpty()) {
            variables.putAll(externalVariables);
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(raw);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) {
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private List<String> extractUnresolvedPlaceholders(String renderedText) {
        LinkedHashSet<String> unresolved = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(renderedText == null ? "" : renderedText);
        while (matcher.find()) {
            unresolved.add(matcher.group(1));
        }
        return new ArrayList<>(unresolved);
    }

    private Map<String, String> defaultTemplateVariables(String controlLevel, String publishPosition, Map<String, Object> facility) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("ROAD_NAME", resolveRoadName(publishPosition, facility));

        Map<String, Object> plan = resolvePlan(controlLevel);
        if (plan == null) {
            return vars;
        }
        vars.put("LIGHT_SPEED", stateService.stringValue(plan.get("passengerSpeedLimit")));
        vars.put("HEAVY_SPEED", stateService.stringValue(plan.get("freightSpeedLimit")));
        return vars;
    }

    private List<Map<String, Object>> matchedFacilities(String publishPosition) {
        List<Map<String, Object>> facilities = new ArrayList<>();
        for (Map<String, Object> facility : stateService.getPublishFacilities()) {
            if (matchesPublishPosition(facility, publishPosition)) {
                facilities.add(facility);
            }
        }
        return facilities;
    }

    private boolean matchesPublishPosition(Map<String, Object> facility, String publishPosition) {
        String segment = stateService.stringValue(facility.get("segment"));
        String facilityId = stateService.stringValue(facility.get("facilityId")).toUpperCase(Locale.ROOT);
        if ("UPSTREAM_EXIT".equals(publishPosition)) {
            return segment.contains("出口") || facilityId.endsWith("R");
        }
        if ("UPSTREAM_ENTRY_TOLL".equals(publishPosition)) {
            return segment.contains("入口") || facilityId.endsWith("C");
        }
        if ("SERVICE_AREA".equals(publishPosition)) {
            return segment.contains("服务区") && (segment.contains("前") || segment.contains("入口"));
        }
        if ("IN_SECTION".equals(publishPosition)) {
            return isSectionFacilitySegment(segment);
        }
        return false;
    }

    private boolean isSectionFacilitySegment(String segment) {
        if (segment.isBlank()) {
            return false;
        }
        if (segment.contains("入口") || segment.contains("出口") || segment.contains("服务区前") || segment.contains("服务区入口") || segment.contains("互通前")) {
            return false;
        }
        return segment.contains("区段") || segment.contains("-");
    }

    private String resolveRoadName(String publishPosition, Map<String, Object> facility) {
        if (facility == null) {
            return "";
        }
        String segment = stateService.stringValue(facility.get("segment"));
        String interchangeName = stateService.stringValue(facility.get("interchangeName"));
        if ("UPSTREAM_EXIT".equals(publishPosition) || "UPSTREAM_ENTRY_TOLL".equals(publishPosition)) {
            return interchangeName.isBlank() ? segment : interchangeName;
        }
        if ("SERVICE_AREA".equals(publishPosition)) {
            return extractServiceAreaName(segment);
        }
        return segment;
    }

    private String extractServiceAreaName(String segment) {
        if (segment == null || segment.isBlank()) {
            return "";
        }
        String value = segment.trim();
        int index = value.indexOf("服务区");
        if (index < 0) {
            return value;
        }
        return value.substring(0, index + "服务区".length());
    }

    private Map<String, Object> findPlanByLevelName(String controlLevel) {
        for (Map<String, Object> plan : stateService.getControlPlanLibrary().values()) {
            String levelName = stateService.stringValue(plan.get("levelName"));
            if (controlLevel.equals(levelName)) {
                return plan;
            }
        }
        return null;
    }

    /**
     * 将模板等级映射到管控预案等级并返回对应预案。
     * 规则：
     * 1) 优先按 levelName 精确命中；
     * 2) 兼容旧数据中的“绿色警戒”；
     * 3) 若仍未命中，按约定等级编号回退（红1、橙2、黄3、蓝4、绿/正常5）。
     */
    private Map<String, Object> resolvePlan(String controlLevel) {
        Map<String, Object> exact = findPlanByLevelName(controlLevel);
        if (exact != null) {
            return exact;
        }

        if ("绿色警戒".equals(controlLevel)) {
            Map<String, Object> normal = findPlanByLevelName("正常通行");
            if (normal != null) {
                return normal;
            }
        }

        Integer levelKey = mapLevelNameToKey(controlLevel);
        if (levelKey != null) {
            return stateService.getControlPlanLibrary().get(levelKey);
        }
        return null;
    }

    private Integer mapLevelNameToKey(String controlLevel) {
        return switch (controlLevel) {
            case "红色警戒" -> 1;
            case "橙色警戒" -> 2;
            case "黄色警戒" -> 3;
            case "蓝色警戒" -> 4;
            case "绿色警戒", "正常通行" -> 5;
            default -> null;
        };
    }
}
