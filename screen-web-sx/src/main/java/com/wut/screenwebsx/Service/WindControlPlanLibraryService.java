package com.wut.screenwebsx.Service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * 4.4 预案库业务服务。
 */
@Service
public class WindControlPlanLibraryService {
    private final WindControlStateService stateService;

    /**
     * 构造预案库服务并注入共享状态；该服务实现 4.4 模块预案与文案的维护逻辑。
     */
    public WindControlPlanLibraryService(WindControlStateService stateService) {
        this.stateService = stateService;
    }

    /**
     * 返回管控总体原则文本，用于前端展示和规则说明。
     */
    public List<String> getControlPrinciples() {
        return List.of(
                "风险区段按管控等级执行分车型限速与禁行策略。",
                "VMS 发布内容需与管控等级、车辆类型及路段位置保持一致。",
                "预案等级编辑仅允许更严格，不允许放宽。",
                "人员与设备调用必须来自人员设备信息库，并遵循区段固定调用点规则。"
        );
    }

    /**
     * 按等级排序返回管控方案预案列表。
     */
    public List<Map<String, Object>> listControlPlans() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Integer level : new TreeSet<>(stateService.getControlPlanLibrary().keySet())) {
            Map<String, Object> row = new LinkedHashMap<>(stateService.getControlPlanLibrary().get(level));
            Map<String, Object> threshold = resolveThresholdSummaryByLevel(level);
            if (!threshold.isEmpty()) {
                row.put("levelName", threshold.get("controlLevelName"));
                row.put("windLevelDesc", threshold.get("windLevelDesc"));
                row.put("minWindLevel", threshold.get("minWindLevel"));
                row.put("maxWindLevel", threshold.get("maxWindLevel"));
                row.put("passengerSpeedLimit", threshold.get("passengerSpeedLimit"));
                row.put("freightSpeedLimit", threshold.get("freightSpeedLimit"));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 更新指定等级预案并执行“只能更严格”校验；当高等级收紧时级联同步低等级。
     */
    public Map<String, Object> updateControlPlanLevel(int level, Map<String, Object> body) {
        Map<String, Object> requestBody = body == null ? Map.of() : body;
        Map<String, Object> existing = stateService.getControlPlanLibrary().get(level);
        if (existing == null) {
            throw new IllegalArgumentException("level not found: " + level);
        }
        if (requestBody.containsKey("minWindLevel")
                || requestBody.containsKey("maxWindLevel")
                || requestBody.containsKey("passengerSpeedLimit")
                || requestBody.containsKey("freightSpeedLimit")) {
            throw new IllegalArgumentException("wind-level thresholds are immutable in control-plans; use /api/v1/wind-speed-thresholds");
        }

        stateService.mergeIfPresent(existing, requestBody, "riskSectionPlan");
        stateService.mergeIfPresent(existing, requestBody, "upstreamExitPlan");
        stateService.mergeIfPresent(existing, requestBody, "upstreamEntryPlan");
        stateService.mergeIfPresent(existing, requestBody, "upstreamServiceAreaPlan");
        if (requestBody.containsKey("description") && !requestBody.containsKey("riskSectionPlan")) {
            existing.put("riskSectionPlan", requestBody.get("description"));
        }
        existing.put("description", stateService.stringValue(existing.get("riskSectionPlan")));

        // 4.4.3 要求：当预案等级规则变化时，VMS 发布内容自动联动更新。
        String autoContent = buildAutoVmsContent(level, existing);
        stateService.getVmsContentLibrary().put(level, autoContent);
        syncPublishFacilityPostInformation(autoContent);
        stateService.persistSnapshot();
        return buildControlPlanResponse(level, existing);
    }

    /**
     * 按等级返回前端所需预案行（前置阈值字段绑定 wind-speed-thresholds）。
     */
    public Map<String, Object> buildControlPlanResponse(int level, Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>(source);
        Map<String, Object> threshold = resolveThresholdSummaryByLevel(level);
        if (!threshold.isEmpty()) {
            row.put("levelName", threshold.get("controlLevelName"));
            row.put("windLevelDesc", threshold.get("windLevelDesc"));
            row.put("minWindLevel", threshold.get("minWindLevel"));
            row.put("maxWindLevel", threshold.get("maxWindLevel"));
            row.put("passengerSpeedLimit", threshold.get("passengerSpeedLimit"));
            row.put("freightSpeedLimit", threshold.get("freightSpeedLimit"));
        }
        row.put("description", stateService.stringValue(row.get("riskSectionPlan")));
        return row;
    }

    /**
     * 按等级排序返回 VMS 发布文案列表。
     */
    public List<Map<String, Object>> listVmsContent() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Integer level : new TreeSet<>(stateService.getVmsContentLibrary().keySet())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("level", level);
            row.put("content", stateService.getVmsContentLibrary().get(level));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 更新指定等级 VMS 文案，等级不存在时拒绝写入。
     */
    public Map<String, Object> updateVmsContent(int level, String content) {
        if (!stateService.getControlPlanLibrary().containsKey(level)) {
            throw new IllegalArgumentException("level not found: " + level);
        }
        String finalContent = content == null ? "" : content;
        stateService.getVmsContentLibrary().put(level, finalContent);
        syncPublishFacilityPostInformation(finalContent);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("level", level);
        row.put("content", stateService.getVmsContentLibrary().get(level));
        stateService.persistSnapshot();
        return row;
    }

    /**
     * 按路段排序返回人员设备调度预案。
     */
    public List<Map<String, Object>> listDispatchPlans() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String segment : new TreeSet<>(stateService.getDispatchPlanLibrary().keySet())) {
            rows.add(new LinkedHashMap<>(stateService.getDispatchPlanLibrary().get(segment)));
        }
        return rows;
    }

    /**
     * 更新指定路段调度预案；严格校验联系人、班组与仓库必须来自资源库。
     */
    public Map<String, Object> updateDispatchPlan(String segment, Map<String, Object> body) {
        // 文档要求：管控区段/管控地点不可编辑，不允许新增未定义区段。
        Map<String, Object> existing = stateService.getDispatchPlanLibrary().get(segment);
        if (existing == null) {
            throw new IllegalArgumentException("segment is immutable and must exist in dispatch plan library: " + segment);
        }

        // 联系人员必须来自 4.3 人员库（支持 staffId 或姓名）。
        if (body != null && body.containsKey("contactStaff")) {
            String contact = stateService.stringValue(body.get("contactStaff"));
            if (!contact.isBlank() && !existsStaff(contact)) {
                throw new IllegalArgumentException("contactStaff must exist in staff library: " + contact);
            }
        }
        // 班组必须来自 4.3 班组信息库。
        if (body != null && body.containsKey("teamId")) {
            String teamId = stateService.stringValue(body.get("teamId"));
            if (!teamId.isBlank() && !existsTeam(teamId)) {
                throw new IllegalArgumentException("teamId must exist in team library: " + teamId);
            }
        }
        // 存放设备地点必须来自 4.3 封路设备仓库字段。
        if (body != null && body.containsKey("warehouse")) {
            String warehouse = stateService.stringValue(body.get("warehouse"));
            if (!warehouse.isBlank() && !existsWarehouse(warehouse)) {
                throw new IllegalArgumentException("warehouse must exist in closure device library: " + warehouse);
            }
        }

        stateService.mergeIfPresent(existing, body, "contactStaff");
        stateService.mergeIfPresent(existing, body, "teamId");
        stateService.mergeIfPresent(existing, body, "warehouse");
        stateService.persistSnapshot();
        return new LinkedHashMap<>(existing);
    }

    /**
     * 校验给定班组 ID 是否存在于班组库。
     */
    private boolean existsTeam(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        return stateService.getDutyTeams().stream().anyMatch(team ->
                t.equals(stateService.stringValue(team.get("teamId")).toLowerCase(Locale.ROOT))
        );
    }

    /**
     * 校验给定仓库是否存在于封路设备库。
     */
    private boolean existsWarehouse(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        return stateService.getClosureDevices().stream().anyMatch(device ->
                t.equals(stateService.stringValue(device.get("warehouse")).toLowerCase(Locale.ROOT))
        );
    }

    /**
     * 校验给定人员是否存在于人员库（支持 staffId 或姓名）。
     */
    private boolean existsStaff(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        return stateService.getStaffList().stream().anyMatch(staff -> {
            String staffId = stateService.stringValue(staff.get("staffId")).toLowerCase(Locale.ROOT);
            String name = stateService.stringValue(staff.get("name")).toLowerCase(Locale.ROOT);
            return t.equals(staffId) || (!name.isBlank() && t.equals(name));
        });
    }

    /**
     * 同步 4.3 设备发布信息字段表（按设备 ID）的当前发布信息。
     */
    private void syncPublishFacilityPostInformation(String content) {
        String finalContent = content == null ? "" : content;
        for (Map<String, Object> facility : stateService.getPublishFacilities()) {
            facility.put("postInformation", finalContent);
        }
    }

    private String buildAutoVmsContent(int level, Map<String, Object> plan) {
        Map<String, Object> threshold = resolveThresholdSummaryByLevel(level);
        int minWindLevel = stateService.intValue(threshold.get("minWindLevel"), 0);
        int maxWindLevel = stateService.intValue(threshold.get("maxWindLevel"), 0);
        int passengerLimit = stateService.intValue(threshold.get("passengerSpeedLimit"), 0);
        int freightLimit = stateService.intValue(threshold.get("freightSpeedLimit"), 0);
        String description = stateService.stringValue(plan.get("riskSectionPlan"));
        if (description.isBlank()) {
            description = "执行分级管控";
        }
        return String.format(
                Locale.ROOT,
                "L%d管控（风力%d-%d级）：小客车限速%dkm/h，客货车限速%dkm/h。%s",
                level, minWindLevel, maxWindLevel, passengerLimit, freightLimit, description
        );
    }

    private Map<String, Object> resolveThresholdSummaryByLevel(int level) {
        int minWind = Integer.MAX_VALUE;
        int maxWind = Integer.MIN_VALUE;
        int passengerLimit = -1;
        int freightLimit = -1;
        String controlLevelName = "";
        String windLevelDesc = "";
        int descWindLevel = Integer.MAX_VALUE;

        for (Map.Entry<Integer, Map<String, Object>> entry : stateService.getSpeedThresholdByWindLevel().entrySet()) {
            Integer windLevel = entry.getKey();
            Map<String, Object> row = entry.getValue();
            if (row == null) {
                continue;
            }
            int rowLevel = stateService.intValue(row.get("controlLevel"), -1);
            if (rowLevel != level) {
                continue;
            }
            minWind = Math.min(minWind, windLevel);
            maxWind = Math.max(maxWind, windLevel);
            if (passengerLimit < 0) {
                passengerLimit = stateService.intValue(row.get("passengerSpeedLimit"), 0);
            }
            if (freightLimit < 0) {
                freightLimit = stateService.intValue(row.get("freightSpeedLimit"), 0);
            }
            if (controlLevelName.isBlank()) {
                controlLevelName = stateService.stringValue(row.get("controlLevelName"));
            }
            if (windLevel < descWindLevel) {
                descWindLevel = windLevel;
                windLevelDesc = stateService.stringValue(row.get("windLevelDesc"));
            }
        }

        if (minWind == Integer.MAX_VALUE || maxWind == Integer.MIN_VALUE) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("controlLevel", level);
        summary.put("controlLevelName", controlLevelName);
        summary.put("windLevelDesc", windLevelDesc);
        summary.put("minWindLevel", minWind);
        summary.put("maxWindLevel", maxWind);
        summary.put("passengerSpeedLimit", Math.max(0, passengerLimit));
        summary.put("freightSpeedLimit", Math.max(0, freightLimit));
        return summary;
    }
}

