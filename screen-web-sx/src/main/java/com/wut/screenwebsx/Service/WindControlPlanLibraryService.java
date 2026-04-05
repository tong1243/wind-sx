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
            rows.add(new LinkedHashMap<>(stateService.getControlPlanLibrary().get(level)));
        }
        return rows;
    }

    /**
     * 更新指定等级预案并执行“只能更严格”校验；当高等级收紧时级联同步低等级。
     */
    public Map<String, Object> updateControlPlanLevel(int level, Map<String, Object> body) {
        Map<String, Object> existing = stateService.getControlPlanLibrary().get(level);
        if (existing == null) {
            throw new IllegalArgumentException("level not found: " + level);
        }
        int oldMinWind = stateService.intValue(existing.get("minWindLevel"), 99);
        int oldPassenger = stateService.intValue(existing.get("passengerSpeedLimit"), 999);
        int oldFreight = stateService.intValue(existing.get("freightSpeedLimit"), 999);

        int newMinWind = stateService.intValue(body.getOrDefault("minWindLevel", oldMinWind), oldMinWind);
        int newPassenger = stateService.intValue(body.getOrDefault("passengerSpeedLimit", oldPassenger), oldPassenger);
        int newFreight = stateService.intValue(body.getOrDefault("freightSpeedLimit", oldFreight), oldFreight);

        if (newMinWind > oldMinWind || newPassenger > oldPassenger || newFreight > oldFreight) {
            throw new IllegalArgumentException("plan update must be stricter, not looser");
        }
        boolean changedToStricter = newMinWind < oldMinWind || newPassenger < oldPassenger || newFreight < oldFreight;

        stateService.mergeIfPresent(existing, body, "minWindLevel");
        stateService.mergeIfPresent(existing, body, "maxWindLevel");
        stateService.mergeIfPresent(existing, body, "passengerSpeedLimit");
        stateService.mergeIfPresent(existing, body, "freightSpeedLimit");
        stateService.mergeIfPresent(existing, body, "description");

        // 文档要求：高等级方案收紧后，低等级方案需联动收紧为同一策略。
        if (changedToStricter) {
            for (Map.Entry<Integer, Map<String, Object>> entry : stateService.getControlPlanLibrary().entrySet()) {
                Integer targetLevel = entry.getKey();
                if (targetLevel <= level) {
                    continue;
                }
                Map<String, Object> targetPlan = entry.getValue();
                targetPlan.put("minWindLevel", existing.get("minWindLevel"));
                targetPlan.put("maxWindLevel", existing.get("maxWindLevel"));
                targetPlan.put("passengerSpeedLimit", existing.get("passengerSpeedLimit"));
                targetPlan.put("freightSpeedLimit", existing.get("freightSpeedLimit"));
                targetPlan.put("description", existing.get("description"));
            }
        }
        stateService.persistSnapshot();
        return new LinkedHashMap<>(existing);
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
        stateService.getVmsContentLibrary().put(level, content == null ? "" : content);
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

        // 联系人员必须来自 4.3，且限定为班组组长。
        if (body != null && body.containsKey("contactStaff")) {
            String contact = stateService.stringValue(body.get("contactStaff"));
            if (!contact.isBlank() && !existsTeamLeader(contact)) {
                throw new IllegalArgumentException("contactStaff must be a team leader from resource library: " + contact);
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
     * 校验给定人员是否为班组组长（支持组长 ID 或组长姓名）。
     */
    private boolean existsTeamLeader(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        for (Map<String, Object> team : stateService.getDutyTeams()) {
            String leaderId = stateService.stringValue(team.get("leaderId"));
            if (leaderId.isBlank()) {
                continue;
            }
            if (t.equals(leaderId.toLowerCase(Locale.ROOT))) {
                return true;
            }
            Map<String, Object> staff = stateService.findById(stateService.getStaffList(), "staffId", leaderId);
            if (staff != null) {
                String leaderName = stateService.stringValue(staff.get("name"));
                if (!leaderName.isBlank() && t.equals(leaderName.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }
}

