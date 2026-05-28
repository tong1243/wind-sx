package com.wut.screenwebsx.Service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 4.3 人员设备库业务服务。
 */
@Service
public class WindControlResourceService {
    /** 中队出警记录快照分类。*/
    private static final String CAT_DISPATCH_RECORD = "DISPATCH_RECORD";

    private final WindControlStateService stateService;
    private final WindControlPersistenceService persistenceService;

    /**
     * 构造资源库服务并注入共享状态；该服务负责 4.3 模块数据的增删改查与约束校验。
     */
    public WindControlResourceService(WindControlStateService stateService,
                                      WindControlPersistenceService persistenceService) {
        this.stateService = stateService;
        this.persistenceService = persistenceService;
    }

    /**
     * 查询信息发布设施列表，返回副本以避免外部直接修改内存态数据。
     */
    public List<Map<String, Object>> listPublishFacilities() {
        return stateService.copyList(stateService.getPublishFacilities());
    }

    /**
     * 按设施 ID 执行新增或更新，并在成功后持久化快照。
     */
    public Map<String, Object> upsertPublishFacility(String id, Map<String, Object> body) {
        Map<String, Object> row = stateService.upsertById(stateService.getPublishFacilities(), "facilityId", id, body);
        row.putIfAbsent("postInformation", "");
        stateService.persistSnapshot();
        return row;
    }

    /**
     * 删除指定信息发布设施并同步持久化，返回删除结果。
     */
    public boolean removePublishFacility(String id) {
        boolean ok = stateService.removeById(stateService.getPublishFacilities(), "facilityId", id);
        stateService.persistSnapshot();
        return ok;
    }

    /**
     * 查询封路设备列表，包含仓库、设备类型、数量与可用状态。
     */
    public List<Map<String, Object>> listClosureDevices() {
        return stateService.copyList(stateService.getClosureDevices());
    }

    /**
     * 按设备 ID 新增或更新封路设备信息，并同步持久化。
     */
    public Map<String, Object> upsertClosureDevice(String id, Map<String, Object> body) {
        Map<String, Object> row = stateService.upsertById(stateService.getClosureDevices(), "deviceId", id, body);
        stateService.persistSnapshot();
        return row;
    }

    /**
     * 删除指定封路设备并同步持久化，返回删除结果。
     */
    public boolean removeClosureDevice(String id) {
        boolean ok = stateService.removeById(stateService.getClosureDevices(), "deviceId", id);
        stateService.persistSnapshot();
        return ok;
    }

    /**
     * 查询执勤人员列表。
     */
    public List<Map<String, Object>> listStaff() {
        return stateService.copyList(stateService.getStaffList());
    }

    /**
     * 按人员 ID 新增或更新人员信息，并同步持久化。
     */
    public Map<String, Object> upsertStaff(String id, Map<String, Object> body) {
        Map<String, Object> row = stateService.upsertById(stateService.getStaffList(), "staffId", id, body);
        stateService.persistSnapshot();
        return row;
    }

    /**
     * 删除指定人员并同步持久化。
     */
    public boolean removeStaff(String id) {
        boolean ok = stateService.removeById(stateService.getStaffList(), "staffId", id);
        stateService.persistSnapshot();
        return ok;
    }

    /**
     * 查询班组列表。
     */
    public List<Map<String, Object>> listTeams() {
        return stateService.copyList(stateService.getDutyTeams());
    }

    /**
     * 按班组 ID 新增或更新班组；对出警班组限制编辑，并校验组长必须属于成员列表。
     */
    public Map<String, Object> upsertTeam(String id, Map<String, Object> body) {
        Map<String, Object> existing = stateService.findById(stateService.getDutyTeams(), "teamId", id);
        if (existing != null && isDispatchedState(stateService.stringValue(existing.get("dispatchState")))
                && containsEditableTeamFields(body)) {
            throw new IllegalArgumentException("team is dispatched and cannot edit members/leader/base info: " + id);
        }

        Map<String, Object> team = stateService.upsertById(stateService.getDutyTeams(), "teamId", id, body);
        if (!team.containsKey("memberIds")) {
            team.put("memberIds", new ArrayList<String>());
        }
        List<String> memberIds = normalizeMemberIds(team.get("memberIds"));
        team.put("memberIds", memberIds);
        String leaderId = stateService.stringValue(team.get("leaderId"));
        if (!leaderId.isBlank() && !memberIds.contains(leaderId)) {
            throw new IllegalArgumentException("leaderId must be one of memberIds");
        }
        syncStaffTeamRelations(stateService.stringValue(team.get("teamId")), memberIds);
        stateService.persistSnapshot();
        return team;
    }

    /**
     * 更新班组成员关系；禁止修改出警班组，且必须保留既有组长。
     */
    public Map<String, Object> assignTeamMembers(String teamId, List<String> memberIds) {
        Map<String, Object> team = stateService.findById(stateService.getDutyTeams(), "teamId", teamId);
        if (team == null) {
            throw new IllegalArgumentException("team not found: " + teamId);
        }
        if (isDispatchedState(stateService.stringValue(team.get("dispatchState")))) {
            throw new IllegalArgumentException("team is dispatched and cannot edit members: " + teamId);
        }
        List<String> ids = normalizeMemberIds(memberIds);
        String leaderId = stateService.stringValue(team.get("leaderId"));
        if (!leaderId.isBlank() && !ids.contains(leaderId)) {
            throw new IllegalArgumentException("leaderId must remain in memberIds when editing team members");
        }
        team.put("memberIds", ids);
        syncStaffTeamRelations(teamId, ids);
        stateService.persistSnapshot();
        return new LinkedHashMap<>(team);
    }

    /**
     * 查询中队出警记录，按出警时间倒序返回。
     */
    public List<Map<String, Object>> listDispatchRecords() {
        List<Map<String, Object>> rows = persistenceService.listByCategory(CAT_DISPATCH_RECORD);
        rows.sort((a, b) -> Long.compare(
                toLong(b.get("dispatchTime"), 0L),
                toLong(a.get("dispatchTime"), 0L)
        ));
        return rows;
    }

    /**
     * 新增一条中队出警记录。
     */
    public Map<String, Object> createDispatchRecord(Map<String, Object> body) {
        Map<String, Object> record = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        String recordId = stateService.stringValue(record.get("recordId"));
        if (recordId.isBlank()) {
            recordId = "DISP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        long now = System.currentTimeMillis();
        long dispatchTime = toLong(record.get("dispatchTime"), now);
        String dispatchStatus = stateService.stringValue(record.get("dispatchStatus"));
        if (dispatchStatus.isBlank()) {
            dispatchStatus = "DISPATCHED";
        }

        record.put("recordId", recordId);
        record.put("dispatchTime", dispatchTime);
        record.put("dispatchStatus", dispatchStatus);
        if (!record.containsKey("returnTime")) {
            record.put("returnTime", null);
        }
        persistenceService.upsertCategory(CAT_DISPATCH_RECORD, recordId, record);
        return record;
    }

    /**
     * 更新中队归队时间并将状态改为 RETURNED。
     */
    public Map<String, Object> markDispatchRecordReturned(String recordId, Map<String, Object> body) {
        Map<String, Object> record = persistenceService.getByCategoryAndKey(CAT_DISPATCH_RECORD, recordId);
        if (record == null) {
            throw new IllegalArgumentException("未找到出警记录: " + recordId);
        }
        long returnTime = body == null
                ? System.currentTimeMillis()
                : toLong(body.get("returnTime"), System.currentTimeMillis());
        record.put("returnTime", returnTime);
        record.put("dispatchStatus", "RETURNED");
        persistenceService.upsertCategory(CAT_DISPATCH_RECORD, recordId, record);
        return record;
    }

    /**
     * 判断请求体是否包含受限字段（成员/组长/基础信息），用于出警态编辑拦截。
     */
    private boolean containsEditableTeamFields(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return body.containsKey("memberIds")
                || body.containsKey("leaderId")
                || body.containsKey("name")
                || body.containsKey("node");
    }

    /**
     * 判断班组是否处于不可编辑状态（DISPATCHED 或 ON_DUTY）。
     */
    private boolean isDispatchedState(String dispatchState) {
        return "DISPATCHED".equalsIgnoreCase(dispatchState) || "ON_DUTY".equalsIgnoreCase(dispatchState);
    }

    /**
     * 统一长整型取值，支持 Number 和字符串并提供默认值。
     */
    private long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    /**
     * 标准化成员 ID 列表：过滤空值并去重，保证成员关系数据干净。
     */
    private List<String> normalizeMemberIds(Object memberIdsRaw) {
        List<String> source = new ArrayList<>();
        if (memberIdsRaw instanceof List<?> list) {
            for (Object id : list) {
                String sid = stateService.stringValue(id);
                if (!sid.isBlank()) {
                    source.add(sid);
                }
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(source));
    }

    /**
     * 同步“班组成员-人员所属班组”双向关系，并确保单人不同时归属多个班组。
     */
    private void syncStaffTeamRelations(String teamId, List<String> memberIds) {
        // 一个成员只能属于一个班组：若调配到当前班组，需从其他班组成员列表移除。
        for (Map<String, Object> team : stateService.getDutyTeams()) {
            String otherTeamId = stateService.stringValue(team.get("teamId"));
            if (otherTeamId.isBlank() || otherTeamId.equals(teamId)) {
                continue;
            }
            List<String> otherMemberIds = normalizeMemberIds(team.get("memberIds"));
            boolean changed = false;
            for (String sid : memberIds) {
                if (!otherMemberIds.contains(sid)) {
                    continue;
                }
                if (isDispatchedState(stateService.stringValue(team.get("dispatchState")))) {
                    throw new IllegalArgumentException("member " + sid + " is in dispatched team: " + otherTeamId);
                }
                otherMemberIds.remove(sid);
                changed = true;
            }
            if (changed) {
                team.put("memberIds", otherMemberIds);
                String otherLeaderId = stateService.stringValue(team.get("leaderId"));
                if (!otherLeaderId.isBlank() && !otherMemberIds.contains(otherLeaderId)) {
                    team.put("leaderId", "");
                }
            }
        }

        for (Map<String, Object> staff : stateService.getStaffList()) {
            String sid = stateService.stringValue(staff.get("staffId"));
            String currentTeamId = stateService.stringValue(staff.get("teamId"));
            if (memberIds.contains(sid)) {
                staff.put("teamId", teamId);
            } else if (teamId.equals(currentTeamId)) {
                staff.put("teamId", "");
            }
        }
    }
}

