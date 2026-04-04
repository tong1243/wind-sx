package com.wut.screenwebsx.Service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.wut.screenwebsx.Service.WindControlPersistenceService.*;

@Service
/**
 * 风区管控中心核心服务（覆盖文档 4.1-4.5）。
 * 采用“内存快照 + 数据库持久化”模式：
 * 1) 运行时读写内存结构，降低接口响应复杂度；
 * 2) 关键写操作后同步到数据库，保证重启可恢复。
 */
public class WindControlCenterService {
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    // 4.1 / 4.2：风区分段与风力阈值
    private final List<Map<String, Object>> fullLineWindSections = new CopyOnWriteArrayList<>();
    private final Map<Integer, Map<String, Object>> speedThresholdByWindLevel = new ConcurrentHashMap<>();

    // 4.3：资源库（设施、设备、人员、班组）
    private final List<Map<String, Object>> publishFacilities = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> closureDevices = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> staffList = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> dutyTeams = new CopyOnWriteArrayList<>();

    // 4.4：预案库（分级方案、VMS 文案、调度方案）
    private final Map<Integer, Map<String, Object>> controlPlanLibrary = new ConcurrentHashMap<>();
    private final Map<Integer, String> vmsContentLibrary = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> dispatchPlanLibrary = new ConcurrentHashMap<>();

    // 4.5：执行态（当前级别、生成方案、事件记录）
    private final Map<String, Integer> currentControlLevelBySegment = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> generatedPlans = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> windEventRecords = new CopyOnWriteArrayList<>();
    private final WindControlPersistenceService persistenceService;

    public WindControlCenterService(WindControlPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @PostConstruct
    public void init() {
        // 启动优先从数据库恢复快照；首次运行则使用种子数据初始化。
        loadFromDbOrSeed();
    }

    /**
     * 获取风区分段可视化结果。
     */
    public Map<String, Object> getWindVisualization(long timestamp, String mode) {
        String finalMode = mode == null ? "real" : mode.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> seg : fullLineWindSections) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("segmentId", seg.get("segmentId"));
            row.put("segmentName", seg.get("segmentName"));
            row.put("direction", seg.get("direction"));
            row.put("color", seg.get("color"));
            if ("forecast".equals(finalMode)) {
                row.put("windLevel", seg.get("forecastWindLevel"));
            } else if ("max72h".equals(finalMode)) {
                row.put("windLevel", seg.get("max72hWindLevel"));
            } else {
                row.put("windLevel", seg.get("realWindLevel"));
            }
            rows.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("mode", finalMode);
        data.put("sections", rows);
        return data;
    }

    public Map<String, Object> getRoadRunOverview(long timestamp) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("digitalTwinEnabled", true);
        data.put("interchangeCount", 5);
        data.put("serviceAreaCount", 2);
        data.put("sections", getWindVisualization(timestamp, "real").get("sections"));
        return data;
    }

    public List<Map<String, Object>> getServiceAreaVehicleStats(long timestamp) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(
                "serviceArea", "Hongshan Service",
                "timestamp", timestamp,
                "inboundVehicle", 34,
                "outboundVehicle", 28,
                "insideVehicle", 96
        ));
        rows.add(row(
                "serviceArea", "Wutong Service",
                "timestamp", timestamp,
                "inboundVehicle", 26,
                "outboundVehicle", 19,
                "insideVehicle", 82
        ));
        return rows;
    }

    public List<Map<String, Object>> getTrafficStateAnalysis(long timestamp) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("segment", "HS-G30 K3010-K3020", "direction", "toWH", "vehPerHour", 1380, "updatedEveryMin", 5));
        rows.add(row("segment", "HS-G30 K3020-K3030", "direction", "toWH", "vehPerHour", 1265, "updatedEveryMin", 5));
        rows.add(row("segment", "HS-G30 K3030-K3040", "direction", "toEZ", "vehPerHour", 1188, "updatedEveryMin", 5));
        rows.add(row("segment", "HS-G30 K3040-K3050", "direction", "toEZ", "vehPerHour", 1106, "updatedEveryMin", 5));
        return rows;
    }

    public List<Map<String, Object>> getSpeedThresholds() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Integer level : new TreeSet<>(speedThresholdByWindLevel.keySet())) {
            rows.add(new LinkedHashMap<>(speedThresholdByWindLevel.get(level)));
        }
        return rows;
    }

    /**
     * 更新指定风力等级的三类车限速阈值。
     */
    public Map<String, Object> updateSpeedThreshold(Map<String, Object> body) {
        int windLevel = intValue(body.get("windLevel"), -1);
        if (windLevel < 1 || windLevel > 12) {
            throw new IllegalArgumentException("windLevel must be between 1 and 12");
        }
        Map<String, Object> existing = speedThresholdByWindLevel.computeIfAbsent(windLevel, this::defaultThresholdRow);
        mergeIfPresent(existing, body, "passengerSpeedLimit");
        mergeIfPresent(existing, body, "freightSpeedLimit");
        mergeIfPresent(existing, body, "dangerousGoodsSpeedLimit");
        persistSnapshot();
        return new LinkedHashMap<>(existing);
    }

    public Map<String, Object> evaluateSpatiotemporalImpact(long timestamp) {
        List<Map<String, Object>> segments = new ArrayList<>();
        for (Map<String, Object> row : fullLineWindSections) {
            String segmentName = stringValue(row.get("segmentName"));
            int real = intValue(row.get("realWindLevel"), 0);
            int forecast = intValue(row.get("forecastWindLevel"), 0);
            int maxWind = Math.max(real, forecast);
            int recommendedLevel = mapWindToControlLevel(maxWind);
            int currentLevel = currentControlLevelBySegment.getOrDefault(segmentName, 4);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("segmentName", segmentName);
            item.put("realWindLevel", real);
            item.put("forecastWindLevel", forecast);
            item.put("maxWindLevel4h", maxWind);
            item.put("recommendedControlLevel", recommendedLevel);
            item.put("currentControlLevel", currentLevel);
            item.put("needAdjust", recommendedLevel != currentLevel);
            segments.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("segments", segments);
        return data;
    }

    /**
     * 查询风观测数据。
     * 当前阶段根据 period 构造样例序列，后续可替换为真实观测源。
     */
    public Map<String, Object> queryWindData(long timestamp, String period, String direction) {
        String p = period == null ? "real" : period.toLowerCase(Locale.ROOT);
        int hours = "real".equals(p) ? 1 : 72;
        long stepMs = "real".equals(p) ? 5L * 60 * 1000 : 60L * 60 * 1000;
        List<Map<String, Object>> records = new ArrayList<>();
        int i = 0;
        while (i < hours) {
            long t = timestamp - (long) i * stepMs;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", dtf.format(Instant.ofEpochMilli(t)));
            row.put("direction", direction == null ? "toWH" : direction);
            row.put("windLevel", 7 + (i % 4));
            row.put("windDirection", i % 2 == 0 ? "NW" : "W");
            row.put("durationMin", "real".equals(p) ? 5 : 60);
            records.add(row);
            i++;
        }
        Collections.reverse(records);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("period", p);
        data.put("records", records);
        return data;
    }

    /**
     * 使用简化规则估算阻断时长：
     * severeSegmentCount * 25 分钟。
     */
    public Map<String, Object> predictBlockDuration(long timestamp) {
        int severeCount = 0;
        for (Map<String, Object> row : fullLineWindSections) {
            int maxWind = Math.max(intValue(row.get("realWindLevel"), 0), intValue(row.get("forecastWindLevel"), 0));
            if (maxWind >= 11) {
                severeCount++;
            }
        }
        int predictedMinutes = severeCount * 25;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("severeSegmentCount", severeCount);
        data.put("predictedBlockDurationMin", predictedMinutes);
        return data;
    }

    public List<Map<String, Object>> listPublishFacilities() {
        return copyList(publishFacilities);
    }

    public Map<String, Object> upsertPublishFacility(String id, Map<String, Object> body) {
        Map<String, Object> row = upsertById(publishFacilities, "facilityId", id, body);
        persistSnapshot();
        return row;
    }

    public boolean removePublishFacility(String id) {
        boolean ok = removeById(publishFacilities, "facilityId", id);
        persistSnapshot();
        return ok;
    }

    public List<Map<String, Object>> listClosureDevices() {
        return copyList(closureDevices);
    }

    public Map<String, Object> upsertClosureDevice(String id, Map<String, Object> body) {
        Map<String, Object> row = upsertById(closureDevices, "deviceId", id, body);
        persistSnapshot();
        return row;
    }

    public boolean removeClosureDevice(String id) {
        boolean ok = removeById(closureDevices, "deviceId", id);
        persistSnapshot();
        return ok;
    }

    public List<Map<String, Object>> listStaff() {
        return copyList(staffList);
    }

    public Map<String, Object> upsertStaff(String id, Map<String, Object> body) {
        Map<String, Object> row = upsertById(staffList, "staffId", id, body);
        persistSnapshot();
        return row;
    }

    public boolean removeStaff(String id) {
        boolean ok = removeById(staffList, "staffId", id);
        persistSnapshot();
        return ok;
    }

    public List<Map<String, Object>> listTeams() {
        return copyList(dutyTeams);
    }

    public Map<String, Object> upsertTeam(String id, Map<String, Object> body) {
        Map<String, Object> team = upsertById(dutyTeams, "teamId", id, body);
        if (!team.containsKey("memberIds")) {
            team.put("memberIds", new ArrayList<String>());
        }
        persistSnapshot();
        return team;
    }

    /**
     * 班组成员重排时，同时回写 staff.teamId，保证两处数据一致。
     */
    public Map<String, Object> assignTeamMembers(String teamId, List<String> memberIds) {
        Map<String, Object> team = findById(dutyTeams, "teamId", teamId);
        if (team == null) {
            throw new IllegalArgumentException("team not found: " + teamId);
        }
        List<String> ids = memberIds == null ? new ArrayList<>() : new ArrayList<>(memberIds);
        team.put("memberIds", ids);
        for (Map<String, Object> staff : staffList) {
            String sid = stringValue(staff.get("staffId"));
            staff.put("teamId", ids.contains(sid) ? teamId : "");
        }
        persistSnapshot();
        return new LinkedHashMap<>(team);
    }

    public List<String> getControlPrinciples() {
        return List.of(
                "Risk segments use graded control levels.",
                "VMS publishes level-based speed limits by vehicle type.",
                "The plan can only be edited to become stricter, never looser.",
                "Personnel and equipment dispatch follows segment ownership."
        );
    }

    public List<Map<String, Object>> listControlPlans() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Integer level : new TreeSet<>(controlPlanLibrary.keySet())) {
            rows.add(new LinkedHashMap<>(controlPlanLibrary.get(level)));
        }
        return rows;
    }

    /**
     * 更新管控预案时只允许“更严格”方向，防止误放宽。
     */
    public Map<String, Object> updateControlPlanLevel(int level, Map<String, Object> body) {
        Map<String, Object> existing = controlPlanLibrary.get(level);
        if (existing == null) {
            throw new IllegalArgumentException("level not found: " + level);
        }
        int oldMinWind = intValue(existing.get("minWindLevel"), 99);
        int oldPassenger = intValue(existing.get("passengerSpeedLimit"), 999);
        int oldFreight = intValue(existing.get("freightSpeedLimit"), 999);

        int newMinWind = intValue(body.getOrDefault("minWindLevel", oldMinWind), oldMinWind);
        int newPassenger = intValue(body.getOrDefault("passengerSpeedLimit", oldPassenger), oldPassenger);
        int newFreight = intValue(body.getOrDefault("freightSpeedLimit", oldFreight), oldFreight);

        if (newMinWind > oldMinWind || newPassenger > oldPassenger || newFreight > oldFreight) {
            throw new IllegalArgumentException("plan update must be stricter, not looser");
        }

        mergeIfPresent(existing, body, "minWindLevel");
        mergeIfPresent(existing, body, "maxWindLevel");
        mergeIfPresent(existing, body, "passengerSpeedLimit");
        mergeIfPresent(existing, body, "freightSpeedLimit");
        mergeIfPresent(existing, body, "description");
        persistSnapshot();
        return new LinkedHashMap<>(existing);
    }

    public List<Map<String, Object>> listVmsContent() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Integer level : new TreeSet<>(vmsContentLibrary.keySet())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("level", level);
            row.put("content", vmsContentLibrary.get(level));
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> updateVmsContent(int level, String content) {
        if (!controlPlanLibrary.containsKey(level)) {
            throw new IllegalArgumentException("level not found: " + level);
        }
        vmsContentLibrary.put(level, content == null ? "" : content);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("level", level);
        row.put("content", vmsContentLibrary.get(level));
        persistSnapshot();
        return row;
    }

    public List<Map<String, Object>> listDispatchPlans() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String segment : new TreeSet<>(dispatchPlanLibrary.keySet())) {
            rows.add(new LinkedHashMap<>(dispatchPlanLibrary.get(segment)));
        }
        return rows;
    }

    public Map<String, Object> updateDispatchPlan(String segment, Map<String, Object> body) {
        Map<String, Object> existing = dispatchPlanLibrary.computeIfAbsent(segment, key -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("segment", key);
            m.put("contactStaff", "");
            m.put("teamId", "");
            m.put("warehouse", "");
            return m;
        });
        mergeIfPresent(existing, body, "contactStaff");
        mergeIfPresent(existing, body, "teamId");
        mergeIfPresent(existing, body, "warehouse");
        persistSnapshot();
        return new LinkedHashMap<>(existing);
    }

    public List<String> getExecutionFlow() {
        return List.of(
                "Calculate wind level from realtime wind speed.",
                "Map wind level to control level from control plan library.",
                "Combine next 24h forecast and generate control plan draft.",
                "Publish plan to staff through SMS, email and phone channels.",
                "Track execution status and keep event records."
        );
    }

    /**
     * 生成方案草稿：根据实时/预测风力映射管控等级并加载模板。
     */
    public Map<String, Object> generateControlPlan(long timestamp, Map<String, Object> body) {
        String segment = stringValue(body.getOrDefault("segment", "HS-G30 K3010-K3030"));
        int realtimeWind = intValue(body.get("realtimeWindLevel"), 7);
        int forecastWind = intValue(body.get("forecastMaxWindLevel"), realtimeWind);
        int maxWind = Math.max(realtimeWind, forecastWind);
        int level = mapWindToControlLevel(maxWind);

        Map<String, Object> template = controlPlanLibrary.get(level);
        if (template == null) {
            throw new IllegalStateException("control plan template missing: level=" + level);
        }

        String planId = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planId", planId);
        plan.put("timestamp", timestamp);
        plan.put("segment", segment);
        plan.put("realtimeWindLevel", realtimeWind);
        plan.put("forecastMaxWindLevel", forecastWind);
        plan.put("recommendedControlLevel", level);
        plan.put("currentControlLevel", currentControlLevelBySegment.getOrDefault(segment, 4));
        plan.put("template", new LinkedHashMap<>(template));
        plan.put("vmsContent", vmsContentLibrary.getOrDefault(level, ""));
        plan.put("dispatch", new LinkedHashMap<>(dispatchPlanLibrary.getOrDefault(segment, Collections.emptyMap())));
        plan.put("status", "DRAFT");
        generatedPlans.put(planId, plan);
        persistenceService.upsertPlan(plan);
        return plan;
    }

    /**
     * 发布方案：
     * 1) 更新方案状态；
     * 2) 回写路段当前管控等级；
     * 3) 生成风事件记录。
     */
    public Map<String, Object> publishPlan(String planId) {
        Map<String, Object> plan = generatedPlans.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("plan not found: " + planId);
        }
        plan.put("status", "PUBLISHED");
        String segment = stringValue(plan.get("segment"));
        int level = intValue(plan.get("recommendedControlLevel"), 4);
        currentControlLevelBySegment.put(segment, level);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("eventId", "EVT-" + UUID.randomUUID().toString().substring(0, 6));
        record.put("startTime", dtf.format(Instant.now()));
        record.put("segment", segment);
        record.put("direction", "toWH");
        record.put("maxWindLevel", Math.max(intValue(plan.get("realtimeWindLevel"), 0), intValue(plan.get("forecastMaxWindLevel"), 0)));
        record.put("controlLevel", level);
        record.put("durationMin", 0);
        record.put("status", "RUNNING");
        windEventRecords.add(record);
        persistenceService.upsertPlan(plan);
        persistenceService.upsertEvent(record);
        persistSnapshot();
        return new LinkedHashMap<>(plan);
    }

    /**
     * 生成调级建议（当前级别 vs 推荐级别）。
     */
    public Map<String, Object> autoUpdate(long timestamp) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Map<String, Object> row : fullLineWindSections) {
            String segment = stringValue(row.get("segmentName"));
            int recommended = mapWindToControlLevel(Math.max(
                    intValue(row.get("realWindLevel"), 0),
                    intValue(row.get("forecastWindLevel"), 0)
            ));
            int current = currentControlLevelBySegment.getOrDefault(segment, 4);
            if (recommended != current) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("segment", segment);
                item.put("currentLevel", current);
                item.put("recommendedLevel", recommended);
                item.put("eventType", recommended < current ? "UPGRADE_CONTROL" : "DOWNGRADE_CONTROL");
                item.put("controlStartTime", findRunningStartTime(segment));
                item.put("controlDurationMin", estimateDurationMin(segment));
                suggestions.add(item);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", timestamp);
        data.put("suggestions", suggestions);
        return data;
    }

    /**
     * 查询风事件记录并按条件过滤。
     */
    public List<Map<String, Object>> listWindEventRecords(String segment, String direction, Integer controlLevel) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> record : windEventRecords) {
            if (segment != null && !segment.isBlank() && !segment.equals(stringValue(record.get("segment")))) {
                continue;
            }
            if (direction != null && !direction.isBlank() && !direction.equals(stringValue(record.get("direction")))) {
                continue;
            }
            if (controlLevel != null && controlLevel != intValue(record.get("controlLevel"), -1)) {
                continue;
            }
            rows.add(new LinkedHashMap<>(record));
        }
        return rows;
    }

    /**
     * 以 CSV 文本导出风事件记录。
     */
    public String exportWindEventRecordsCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("eventId,startTime,segment,direction,maxWindLevel,controlLevel,durationMin,status").append('\n');
        for (Map<String, Object> r : windEventRecords) {
            sb.append(csv(r.get("eventId"))).append(',')
                    .append(csv(r.get("startTime"))).append(',')
                    .append(csv(r.get("segment"))).append(',')
                    .append(csv(r.get("direction"))).append(',')
                    .append(csv(r.get("maxWindLevel"))).append(',')
                    .append(csv(r.get("controlLevel"))).append(',')
                    .append(csv(r.get("durationMin"))).append(',')
                    .append(csv(r.get("status"))).append('\n');
        }
        return sb.toString();
    }

    private void initWindSections() {
        fullLineWindSections.add(newWindSection("S1", "HS-G30 K3010-K3020", "toWH", "green", 7, 8, 9));
        fullLineWindSections.add(newWindSection("S2", "HS-G30 K3020-K3030", "toWH", "yellow", 9, 10, 10));
        fullLineWindSections.add(newWindSection("S3", "HS-G30 K3030-K3040", "toEZ", "red", 11, 10, 11));
        fullLineWindSections.add(newWindSection("S4", "HS-G30 K3040-K3050", "toEZ", "green", 8, 8, 9));
    }

    private void initSpeedThresholds() {
        for (int level = 7; level <= 12; level++) {
            speedThresholdByWindLevel.put(level, defaultThresholdRow(level));
        }
    }

    private Map<String, Object> defaultThresholdRow(int windLevel) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("windLevel", windLevel);
        row.put("passengerSpeedLimit", Math.max(30, 80 - (windLevel - 7) * 10));
        row.put("freightSpeedLimit", Math.max(20, 70 - (windLevel - 7) * 10));
        row.put("dangerousGoodsSpeedLimit", Math.max(20, 60 - (windLevel - 7) * 10));
        return row;
    }

    private void initResourceLibrary() {
        publishFacilities.add(row("facilityId", "VMS-01", "pileNo", "K3015+200", "type", "VMS", "segment", "S1"));
        publishFacilities.add(row("facilityId", "VMS-02", "pileNo", "K3036+800", "type", "VMS", "segment", "S3"));

        closureDevices.add(row("deviceId", "CD-01", "warehouse", "Hongshan Exit", "deviceType", "Cone", "quantity", 200, "available", true));
        closureDevices.add(row("deviceId", "CD-02", "warehouse", "Wutong Service", "deviceType", "Barrier", "quantity", 36, "available", true));

        staffList.add(row("staffId", "ST-01", "name", "Zhang San", "onDuty", true, "teamId", "T-01", "phone", "13800000001"));
        staffList.add(row("staffId", "ST-02", "name", "Li Si", "onDuty", true, "teamId", "T-01", "phone", "13800000002"));
        staffList.add(row("staffId", "ST-03", "name", "Wang Wu", "onDuty", false, "teamId", "", "phone", "13800000003"));

        dutyTeams.add(row("teamId", "T-01", "name", "Alpha", "leaderId", "ST-01", "node", "Hongshan Interchange", "dispatchState", "READY", "memberIds", new ArrayList<>(List.of("ST-01", "ST-02"))));
        dutyTeams.add(row("teamId", "T-02", "name", "Bravo", "leaderId", "", "node", "Wutong Service", "dispatchState", "READY", "memberIds", new ArrayList<String>()));
    }

    private void initPlanLibrary() {
        controlPlanLibrary.put(1, row("level", 1, "minWindLevel", 11, "maxWindLevel", 12, "passengerSpeedLimit", 40, "freightSpeedLimit", 30, "description", "Close vulnerable lanes and strict speed limit"));
        controlPlanLibrary.put(2, row("level", 2, "minWindLevel", 9, "maxWindLevel", 10, "passengerSpeedLimit", 50, "freightSpeedLimit", 40, "description", "Restrict freight and reduce overall speed"));
        controlPlanLibrary.put(3, row("level", 3, "minWindLevel", 7, "maxWindLevel", 8, "passengerSpeedLimit", 60, "freightSpeedLimit", 50, "description", "Publish warning and moderate speed limit"));
        controlPlanLibrary.put(4, row("level", 4, "minWindLevel", 0, "maxWindLevel", 6, "passengerSpeedLimit", 80, "freightSpeedLimit", 70, "description", "Normal operation"));

        vmsContentLibrary.put(1, "Strong wind warning. Keep speed <= 40km/h. Follow traffic police.");
        vmsContentLibrary.put(2, "High wind risk. Keep speed <= 50km/h and increase distance.");
        vmsContentLibrary.put(3, "Wind warning. Keep speed <= 60km/h.");
        vmsContentLibrary.put(4, "Normal traffic condition.");

        dispatchPlanLibrary.put("HS-G30 K3010-K3020", row("segment", "HS-G30 K3010-K3020", "contactStaff", "ST-01", "teamId", "T-01", "warehouse", "Hongshan Exit"));
        dispatchPlanLibrary.put("HS-G30 K3020-K3030", row("segment", "HS-G30 K3020-K3030", "contactStaff", "ST-02", "teamId", "T-01", "warehouse", "Wutong Service"));
        dispatchPlanLibrary.put("HS-G30 K3030-K3040", row("segment", "HS-G30 K3030-K3040", "contactStaff", "ST-03", "teamId", "T-02", "warehouse", "Wutong Service"));
    }

    private void initExecutionState() {
        currentControlLevelBySegment.put("HS-G30 K3010-K3020", 4);
        currentControlLevelBySegment.put("HS-G30 K3020-K3030", 4);
        currentControlLevelBySegment.put("HS-G30 K3030-K3040", 4);
        currentControlLevelBySegment.put("HS-G30 K3040-K3050", 4);
    }

    private int mapWindToControlLevel(int windLevel) {
        // 分级规则：
        // 11-12 => 一级；9-10 => 二级；7-8 => 三级；其余 => 四级（常态）。
        if (windLevel >= 11) {
            return 1;
        }
        if (windLevel >= 9) {
            return 2;
        }
        if (windLevel >= 7) {
            return 3;
        }
        return 4;
    }

    private Map<String, Object> newWindSection(String segmentId, String segmentName, String direction, String color, int real, int forecast, int max72h) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("segmentId", segmentId);
        row.put("segmentName", segmentName);
        row.put("direction", direction);
        row.put("color", color);
        row.put("realWindLevel", real);
        row.put("forecastWindLevel", forecast);
        row.put("max72hWindLevel", max72h);
        return row;
    }

    private Map<String, Object> upsertById(List<Map<String, Object>> rows, String idKey, String id, Map<String, Object> body) {
        // 统一“按 ID 新增或更新”逻辑，减少各资源类型重复代码。
        String finalId = (id == null || id.isBlank()) ? UUID.randomUUID().toString().substring(0, 8) : id;
        Map<String, Object> existing = findById(rows, idKey, finalId);
        if (existing == null) {
            existing = new LinkedHashMap<>();
            existing.put(idKey, finalId);
            rows.add(existing);
        }
        if (body != null) {
            for (Map.Entry<String, Object> e : body.entrySet()) {
                existing.put(e.getKey(), e.getValue());
            }
        }
        existing.put(idKey, finalId);
        return new LinkedHashMap<>(existing);
    }

    private Map<String, Object> findById(List<Map<String, Object>> rows, String idKey, String id) {
        for (Map<String, Object> row : rows) {
            if (id.equals(stringValue(row.get(idKey)))) {
                return row;
            }
        }
        return null;
    }

    private boolean removeById(List<Map<String, Object>> rows, String idKey, String id) {
        return rows.removeIf(row -> id.equals(stringValue(row.get(idKey))));
    }

    private List<Map<String, Object>> copyList(List<Map<String, Object>> source) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : source) {
            rows.add(new LinkedHashMap<>(row));
        }
        return rows;
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        int i = 0;
        while (i + 1 < kv.length) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
            i += 2;
        }
        return m;
    }

    private void mergeIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source != null && source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String csv(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String findRunningStartTime(String segment) {
        for (int i = windEventRecords.size() - 1; i >= 0; i--) {
            Map<String, Object> record = windEventRecords.get(i);
            if (segment.equals(stringValue(record.get("segment"))) && "RUNNING".equals(stringValue(record.get("status")))) {
                return stringValue(record.get("startTime"));
            }
        }
        return "";
    }

    private int estimateDurationMin(String segment) {
        String startTime = findRunningStartTime(segment);
        if (startTime.isBlank()) {
            return 0;
        }
        return 10;
    }

    private void loadFromDbOrSeed() {
        // 判断是否为首次初始化：核心类别为空则走种子数据。
        List<Map<String, Object>> sections = persistenceService.listByCategory(CAT_WIND_SECTION);
        if (sections.isEmpty()) {
            initWindSections();
            initSpeedThresholds();
            initResourceLibrary();
            initPlanLibrary();
            initExecutionState();
            persistSnapshot();
            return;
        }

        fullLineWindSections.clear();
        fullLineWindSections.addAll(sections);

        speedThresholdByWindLevel.clear();
        for (Map<String, Object> row : persistenceService.listByCategory(CAT_SPEED_THRESHOLD)) {
            int level = intValue(row.get("windLevel"), -1);
            if (level > 0) {
                speedThresholdByWindLevel.put(level, new LinkedHashMap<>(row));
            }
        }

        publishFacilities.clear();
        publishFacilities.addAll(persistenceService.listByCategory(CAT_PUBLISH_FACILITY));
        closureDevices.clear();
        closureDevices.addAll(persistenceService.listByCategory(CAT_CLOSURE_DEVICE));
        staffList.clear();
        staffList.addAll(persistenceService.listByCategory(CAT_STAFF));
        dutyTeams.clear();
        dutyTeams.addAll(persistenceService.listByCategory(CAT_TEAM));

        controlPlanLibrary.clear();
        for (Map<String, Object> row : persistenceService.listByCategory(CAT_CONTROL_PLAN)) {
            int level = intValue(row.get("level"), -1);
            if (level > 0) {
                controlPlanLibrary.put(level, new LinkedHashMap<>(row));
            }
        }

        vmsContentLibrary.clear();
        for (Map<String, Object> row : persistenceService.listByCategory(CAT_VMS_CONTENT)) {
            int level = intValue(row.get("level"), -1);
            if (level > 0) {
                vmsContentLibrary.put(level, stringValue(row.get("content")));
            }
        }

        dispatchPlanLibrary.clear();
        for (Map<String, Object> row : persistenceService.listByCategory(CAT_DISPATCH_PLAN)) {
            String segment = stringValue(row.get("segment"));
            if (!segment.isBlank()) {
                dispatchPlanLibrary.put(segment, new LinkedHashMap<>(row));
            }
        }

        currentControlLevelBySegment.clear();
        for (Map<String, Object> row : persistenceService.listByCategory(CAT_CONTROL_STATE)) {
            String segment = stringValue(row.get("segment"));
            if (!segment.isBlank()) {
                currentControlLevelBySegment.put(segment, intValue(row.get("level"), 4));
            }
        }

        generatedPlans.clear();
        for (Map<String, Object> row : persistenceService.listLatestPlanPayloads(200)) {
            String planId = stringValue(row.get("planId"));
            if (!planId.isBlank()) {
                generatedPlans.put(planId, new LinkedHashMap<>(row));
            }
        }

        windEventRecords.clear();
        windEventRecords.addAll(persistenceService.listAllEvents());
    }

    private void persistSnapshot() {
        // 把当前内存快照完整同步到 kv 表，避免重启后状态丢失。
        syncListCategory(CAT_WIND_SECTION, fullLineWindSections, "segmentId");
        syncListCategory(CAT_SPEED_THRESHOLD, new ArrayList<>(speedThresholdByWindLevel.values()), "windLevel");
        syncListCategory(CAT_PUBLISH_FACILITY, publishFacilities, "facilityId");
        syncListCategory(CAT_CLOSURE_DEVICE, closureDevices, "deviceId");
        syncListCategory(CAT_STAFF, staffList, "staffId");
        syncListCategory(CAT_TEAM, dutyTeams, "teamId");
        syncListCategory(CAT_CONTROL_PLAN, new ArrayList<>(controlPlanLibrary.values()), "level");

        List<Map<String, Object>> vmsRows = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : vmsContentLibrary.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("level", entry.getKey());
            row.put("content", entry.getValue());
            vmsRows.add(row);
        }
        syncListCategory(CAT_VMS_CONTENT, vmsRows, "level");

        syncListCategory(CAT_DISPATCH_PLAN, new ArrayList<>(dispatchPlanLibrary.values()), "segment");

        List<Map<String, Object>> controlStateRows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : currentControlLevelBySegment.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("segment", entry.getKey());
            row.put("level", entry.getValue());
            controlStateRows.add(row);
        }
        syncListCategory(CAT_CONTROL_STATE, controlStateRows, "segment");

        for (Map<String, Object> plan : generatedPlans.values()) {
            persistenceService.upsertPlan(plan);
        }
        for (Map<String, Object> event : windEventRecords) {
            persistenceService.upsertEvent(event);
        }
    }

    private void syncListCategory(String category, List<Map<String, Object>> rows, String keyField) {
        // 双向同步策略：
        // 1) 内存中存在的逐条 upsert；
        // 2) 数据库中有但内存中已删除的做清理。
        Set<String> inMemoryKeys = new HashSet<>();
        for (Map<String, Object> row : rows) {
            String key = stringValue(row.get(keyField));
            if (key.isBlank()) {
                continue;
            }
            inMemoryKeys.add(key);
            persistenceService.upsertCategory(category, key, row);
        }
        List<Map<String, Object>> dbRows = persistenceService.listByCategory(category);
        for (Map<String, Object> dbRow : dbRows) {
            String key = stringValue(dbRow.get(keyField));
            if (!key.isBlank() && !inMemoryKeys.contains(key)) {
                persistenceService.deleteCategory(category, key);
            }
        }
    }
}
