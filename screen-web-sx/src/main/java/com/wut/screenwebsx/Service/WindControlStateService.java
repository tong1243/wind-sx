package com.wut.screenwebsx.Service;

import com.wut.screendbmysqlsx.Model.ControlIntervalStatic;
import com.wut.screendbmysqlsx.Model.ControlPlanStatic;
import com.wut.screendbmysqlsx.Model.ClosureDeviceStatic;
import com.wut.screendbmysqlsx.Model.DutyStaffStatic;
import com.wut.screendbmysqlsx.Model.DutyTeamStatic;
import com.wut.screendbmysqlsx.Model.PublishFacilityStatic;
import com.wut.screendbmysqlsx.Model.RoadSegmentStatic;
import com.wut.screendbmysqlsx.Model.SpeedThresholdStatic;
import com.wut.screendbmysqlsx.Service.ClosureDeviceStaticService;
import com.wut.screendbmysqlsx.Service.ControlIntervalStaticService;
import com.wut.screendbmysqlsx.Service.ControlPlanStaticService;
import com.wut.screendbmysqlsx.Service.DutyStaffStaticService;
import com.wut.screendbmysqlsx.Service.DutyTeamStaticService;
import com.wut.screendbmysqlsx.Service.PublishFacilityStaticService;
import com.wut.screendbmysqlsx.Service.RoadSegmentStaticService;
import com.wut.screendbmysqlsx.Service.SpeedThresholdStaticService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wut.screenwebsx.Service.WindControlPersistenceService.CAT_CLOSURE_DEVICE;
import static com.wut.screenwebsx.Service.WindControlPersistenceService.CAT_CONTROL_PLAN;
import static com.wut.screenwebsx.Service.WindControlPersistenceService.CAT_CONTROL_STATE;
import static com.wut.screenwebsx.Service.WindControlPersistenceService.CAT_DISPATCH_PLAN;
import static com.wut.screenwebsx.Service.WindControlPersistenceService.CAT_PUBLISH_FACILITY;
import static com.wut.screenwebsx.Service.WindControlPersistenceService.CAT_SPEED_THRESHOLD;
import static com.wut.screenwebsx.Service.WindControlPersistenceService.CAT_STAFF;
import static com.wut.screenwebsx.Service.WindControlPersistenceService.CAT_TEAM;
import static com.wut.screenwebsx.Service.WindControlPersistenceService.CAT_VMS_CONTENT;
import static com.wut.screenwebsx.Service.WindControlPersistenceService.CAT_WIND_SECTION;

/**
 * 风区管控共享状态服务。
 *
 * 说明：
 * 1. 本服务是 4.1-4.5 的统一内存状态中心。
 * 2. 所有模块更新状态后，统一通过 persistSnapshot() 写入快照。
 * 3. 初始化遵循“先快照、后静态表”，不使用代码硬编码默认静态数据。
 */
@Service
public class WindControlStateService {
    /** 下行方向（哈密）。 */
    private static final int DIRECTION_HAMI = 2;

    /** 上行方向（吐鲁番）。 */
    private static final int DIRECTION_TURPAN = 1;

    /** 最小风级。 */
    private static final int WIND_LEVEL_MIN = 1;

    /** 最大风级。 */
    private static final int WIND_LEVEL_MAX = 12;

    /** 首次加载默认风级。 */
    private static final int DEFAULT_WIND_LEVEL = 6;

    /** 解析等级数字的正则。 */
    private static final Pattern CONTROL_LEVEL_DIGIT_PATTERN = Pattern.compile("(\\d+)");

    /** 全线风区路段状态。 */
    private final List<Map<String, Object>> fullLineWindSections = new CopyOnWriteArrayList<>();

    /** 风级到阈值映射。 */
    private final Map<Integer, Map<String, Object>> speedThresholdByWindLevel = new ConcurrentHashMap<>();

    /** 信息发布设施。 */
    private final List<Map<String, Object>> publishFacilities = new CopyOnWriteArrayList<>();

    /** 封路设备。 */
    private final List<Map<String, Object>> closureDevices = new CopyOnWriteArrayList<>();

    /** 执勤人员。 */
    private final List<Map<String, Object>> staffList = new CopyOnWriteArrayList<>();

    /** 执勤班组。 */
    private final List<Map<String, Object>> dutyTeams = new CopyOnWriteArrayList<>();

    /** 管控预案库。 */
    private final Map<Integer, Map<String, Object>> controlPlanLibrary = new ConcurrentHashMap<>();

    /** VMS 文案库。 */
    private final Map<Integer, String> vmsContentLibrary = new ConcurrentHashMap<>();

    /** 人员设备调用预案库。 */
    private final Map<String, Map<String, Object>> dispatchPlanLibrary = new ConcurrentHashMap<>();

    /** 当前路段管控等级。 */
    private final Map<String, Integer> currentControlLevelBySegment = new ConcurrentHashMap<>();

    /** 已生成方案。 */
    private final Map<String, Map<String, Object>> generatedPlans = new ConcurrentHashMap<>();

    /** 大风事件记录。 */
    private final List<Map<String, Object>> windEventRecords = new CopyOnWriteArrayList<>();

    /** 快照持久化服务。 */
    private final WindControlPersistenceService persistenceService;

    /** 表1-4 路段静态表服务。 */
    private final RoadSegmentStaticService roadSegmentStaticService;

    /** 表1-6 限速阈值静态表服务。 */
    private final SpeedThresholdStaticService speedThresholdStaticService;

    /** 表1-7 管控预案静态表服务。 */
    private final ControlPlanStaticService controlPlanStaticService;

    /** 表1-5 管控区间静态表服务。 */
    private final ControlIntervalStaticService controlIntervalStaticService;

    /** 表4.3.1 信息发布设施静态表服务。 */
    private final PublishFacilityStaticService publishFacilityStaticService;

    /** 表4.3.2 封路设备静态表服务。 */
    private final ClosureDeviceStaticService closureDeviceStaticService;

    /** 表4.3.3 执勤人员静态表服务。 */
    private final DutyStaffStaticService dutyStaffStaticService;

    /** 表4.3.4 执勤班组静态表服务。 */
    private final DutyTeamStaticService dutyTeamStaticService;

    /**
     * 构造函数。
     *
     * @param persistenceService 快照持久化
     * @param roadSegmentStaticService 路段静态服务
     * @param speedThresholdStaticService 阈值静态服务
     * @param controlPlanStaticService 预案静态服务
     * @param controlIntervalStaticService 区间静态服务
     * @param publishFacilityStaticService 信息发布设施静态服务
     * @param closureDeviceStaticService 封路设备静态服务
     * @param dutyStaffStaticService 执勤人员静态服务
     * @param dutyTeamStaticService 执勤班组静态服务
     */
    public WindControlStateService(WindControlPersistenceService persistenceService,
                                   RoadSegmentStaticService roadSegmentStaticService,
                                   SpeedThresholdStaticService speedThresholdStaticService,
                                   ControlPlanStaticService controlPlanStaticService,
                                   ControlIntervalStaticService controlIntervalStaticService,
                                   PublishFacilityStaticService publishFacilityStaticService,
                                   ClosureDeviceStaticService closureDeviceStaticService,
                                   DutyStaffStaticService dutyStaffStaticService,
                                   DutyTeamStaticService dutyTeamStaticService) {
        this.persistenceService = persistenceService;
        this.roadSegmentStaticService = roadSegmentStaticService;
        this.speedThresholdStaticService = speedThresholdStaticService;
        this.controlPlanStaticService = controlPlanStaticService;
        this.controlIntervalStaticService = controlIntervalStaticService;
        this.publishFacilityStaticService = publishFacilityStaticService;
        this.closureDeviceStaticService = closureDeviceStaticService;
        this.dutyStaffStaticService = dutyStaffStaticService;
        this.dutyTeamStaticService = dutyTeamStaticService;
    }

    /**
     * 启动初始化入口。
     * 先加载快照，若快照为空则走静态表初始化。
     */
    @PostConstruct
    public void init() {
        loadFromDbOrSeed();
    }

    /** @return 全线风区路段列表。 */
    public List<Map<String, Object>> getFullLineWindSections() { return fullLineWindSections; }

    /** @return 风级阈值映射。 */
    public Map<Integer, Map<String, Object>> getSpeedThresholdByWindLevel() { return speedThresholdByWindLevel; }

    /** @return 信息发布设施列表。 */
    public List<Map<String, Object>> getPublishFacilities() { return publishFacilities; }

    /** @return 封路设备列表。 */
    public List<Map<String, Object>> getClosureDevices() { return closureDevices; }

    /** @return 人员列表。 */
    public List<Map<String, Object>> getStaffList() { return staffList; }

    /** @return 班组列表。 */
    public List<Map<String, Object>> getDutyTeams() { return dutyTeams; }

    /** @return 管控预案库。 */
    public Map<Integer, Map<String, Object>> getControlPlanLibrary() { return controlPlanLibrary; }

    /** @return VMS 文案库。 */
    public Map<Integer, String> getVmsContentLibrary() { return vmsContentLibrary; }

    /** @return 调度预案库。 */
    public Map<String, Map<String, Object>> getDispatchPlanLibrary() { return dispatchPlanLibrary; }

    /** @return 当前路段管控等级。 */
    public Map<String, Integer> getCurrentControlLevelBySegment() { return currentControlLevelBySegment; }

    /** @return 已生成方案快照。 */
    public Map<String, Map<String, Object>> getGeneratedPlans() { return generatedPlans; }

    /** @return 大风事件记录。 */
    public List<Map<String, Object>> getWindEventRecords() { return windEventRecords; }

    /** @return 持久化服务。 */
    public WindControlPersistenceService getPersistenceService() { return persistenceService; }

    /**
     * 获取默认管控等级。
     * 规则：取预案库中最大的等级值；为空时默认 5。
     *
     * @return 默认等级
     */
    public int getDefaultControlLevel() {
        if (controlPlanLibrary.isEmpty()) {
            return 5;
        }
        return Collections.max(controlPlanLibrary.keySet());
    }

    /**
     * 将当前内存态完整持久化为快照。
     */
    public void persistSnapshot() {
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

    /**
     * 创建有序 Map（按 key-value 成对传参）。
     *
     * @param kv 成对参数
     * @return 结果 Map
     */
    public Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        int i = 0;
        while (i + 1 < kv.length) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
            i += 2;
        }
        return m;
    }

    /**
     * source 包含指定 key 时才覆盖 target。
     *
     * @param target 目标
     * @param source 来源
     * @param key 字段
     */
    public void mergeIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source != null && source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    /**
     * 安全读取 int。
     *
     * @param value 原始对象
     * @param defaultValue 默认值
     * @return int 值
     */
    public int intValue(Object value, int defaultValue) {
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

    /**
     * 安全读取字符串。
     *
     * @param value 原始对象
     * @return 非 null 字符串
     */
    public String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * CSV 安全转义。
     *
     * @param value 原始值
     * @return 转义结果
     */
    public String csv(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /**
     * 风级转管控等级。
     *
     * 规则：12->1，11->2，9-10->3，7-8->4，<=6->5。
     *
     * @param windLevel 风级
     * @return 管控等级
     */
    public int mapWindToControlLevel(int windLevel) {
        if (windLevel >= 12) {
            return 1;
        }
        if (windLevel >= 11) {
            return 2;
        }
        if (windLevel >= 9) {
            return 3;
        }
        if (windLevel >= 7) {
            return 4;
        }
        return 5;
    }

    /**
     * 按主键 upsert 列表中的记录。
     *
     * @param rows 列表
     * @param idKey 主键字段
     * @param id 主键值
     * @param body 更新内容
     * @return upsert 后记录副本
     */
    public Map<String, Object> upsertById(List<Map<String, Object>> rows, String idKey, String id, Map<String, Object> body) {
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

    /**
     * 按主键查找记录。
     *
     * @param rows 列表
     * @param idKey 主键字段
     * @param id 主键值
     * @return 命中记录；未命中返回 null
     */
    public Map<String, Object> findById(List<Map<String, Object>> rows, String idKey, String id) {
        for (Map<String, Object> row : rows) {
            if (id.equals(stringValue(row.get(idKey)))) {
                return row;
            }
        }
        return null;
    }

    /**
     * 按主键删除记录。
     *
     * @param rows 列表
     * @param idKey 主键字段
     * @param id 主键值
     * @return 是否删除成功
     */
    public boolean removeById(List<Map<String, Object>> rows, String idKey, String id) {
        return rows.removeIf(row -> id.equals(stringValue(row.get(idKey))));
    }

    /**
     * 对 Map 列表做浅拷贝。
     *
     * @param source 原列表
     * @return 拷贝结果
     */
    public List<Map<String, Object>> copyList(List<Map<String, Object>> source) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : source) {
            rows.add(new LinkedHashMap<>(row));
        }
        return rows;
    }

    /**
     * 查询路段最近运行中事件开始时间。
     *
     * @param segment 路段
     * @return 开始时间；无则空串
     */
    public String findRunningStartTime(String segment) {
        for (int i = windEventRecords.size() - 1; i >= 0; i--) {
            Map<String, Object> record = windEventRecords.get(i);
            if (segment.equals(stringValue(record.get("segment"))) && "RUNNING".equals(stringValue(record.get("status")))) {
                return stringValue(record.get("startTime"));
            }
        }
        return "";
    }

    /**
     * 估算管控持续时间。
     *
     * @param segment 路段
     * @return 分钟数
     */
    public int estimateDurationMin(String segment) {
        String startTime = findRunningStartTime(segment);
        if (startTime.isBlank()) {
            return 0;
        }
        return 10;
    }

    /**
     * 加载状态总流程。
     *
     * 先尝试恢复快照；若快照为空则执行静态表初始化。
     */
    private void loadFromDbOrSeed() {
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
        for (Map<String, Object> section : fullLineWindSections) {
            normalizeDirectionField(section);
        }

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
        int defaultLevel = getDefaultControlLevel();
        for (Map<String, Object> row : persistenceService.listByCategory(CAT_CONTROL_STATE)) {
            String segment = stringValue(row.get("segment"));
            if (!segment.isBlank()) {
                currentControlLevelBySegment.put(segment, intValue(row.get("level"), defaultLevel));
            }
        }

        generatedPlans.clear();
        for (Map<String, Object> row : persistenceService.listLatestPlanPayloads(200)) {
            String planId = stringValue(row.get("planId"));
            if (!planId.isBlank()) {
                normalizeDirectionField(row);
                generatedPlans.put(planId, new LinkedHashMap<>(row));
            }
        }

        windEventRecords.clear();
        windEventRecords.addAll(persistenceService.listAllEvents());
        for (Map<String, Object> event : windEventRecords) {
            normalizeDirectionField(event);
        }
    }

    /**
     * 将某个分类的内存列表和快照表进行双向对齐。
     *
     * @param category 分类名
     * @param rows 内存行集合
     * @param keyField 主键字段
     */
    private void syncListCategory(String category, List<Map<String, Object>> rows, String keyField) {
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

    /**
     * 初始化全线风区路段。
     *
     * 数据来源：SQL 表1-4（road_segment_static）。
     */
    private void initWindSections() {
        List<RoadSegmentStatic> segments = roadSegmentStaticService.getEnabledSegments();
        if (segments.isEmpty()) {
            throw new IllegalStateException("road_segment_static 未查询到启用数据，请先执行静态表初始化 SQL。");
        }

        fullLineWindSections.clear();
        int index = 1;
        for (RoadSegmentStatic segment : segments) {
            int direction = normalizeDirectionValue(segment.getDirection(), DIRECTION_HAMI);
            String segmentId = segment.getId() == null ? "SEG-" + index : String.valueOf(segment.getId());
            String segmentName = buildSegmentName(segment, direction);
            fullLineWindSections.add(newWindSection(
                    segmentId,
                    segmentName,
                    direction,
                    colorByWindLevel(DEFAULT_WIND_LEVEL),
                    DEFAULT_WIND_LEVEL,
                    DEFAULT_WIND_LEVEL,
                    DEFAULT_WIND_LEVEL
            ));
            index++;
        }
    }

    /**
     * 初始化风力限速阈值。
     *
     * 数据来源：SQL 表1-6（speed_threshold_static）。
     */
    private void initSpeedThresholds() {
        List<SpeedThresholdStatic> thresholds = speedThresholdStaticService.getEnabledThresholds();
        if (thresholds.isEmpty()) {
            throw new IllegalStateException("speed_threshold_static 未查询到启用数据，请先执行静态表初始化 SQL。");
        }

        speedThresholdByWindLevel.clear();
        for (SpeedThresholdStatic cfg : thresholds) {
            int level = parseControlLevelName(cfg.getControlLevelName(), -1);
            int min = cfg.getMinWindLevel() == null ? WIND_LEVEL_MIN : cfg.getMinWindLevel();
            int max = cfg.getMaxWindLevel() == null ? WIND_LEVEL_MAX : cfg.getMaxWindLevel();
            if (max < min) {
                int t = min;
                min = max;
                max = t;
            }
            min = Math.max(WIND_LEVEL_MIN, min);
            max = Math.min(WIND_LEVEL_MAX, max);

            for (int windLevel = min; windLevel <= max; windLevel++) {
                speedThresholdByWindLevel.put(windLevel, buildThresholdRow(windLevel, cfg, level));
            }
        }

        for (int windLevel = WIND_LEVEL_MIN; windLevel <= WIND_LEVEL_MAX; windLevel++) {
            speedThresholdByWindLevel.computeIfAbsent(windLevel, this::defaultThresholdRow);
        }
    }

    /**
     * 初始化资源库。
     */
    private void initResourceLibrary() {
        List<PublishFacilityStatic> facilities = publishFacilityStaticService.getEnabledFacilities();
        if (facilities.isEmpty()) {
            throw new IllegalStateException("publish_facility_static 未查询到启用数据，请先执行静态表初始化 SQL。");
        }

        publishFacilities.clear();
        for (PublishFacilityStatic facility : facilities) {
            String facilityId = stringValue(facility.getFacilityId());
            if (facilityId.isBlank()) {
                throw new IllegalStateException("publish_facility_static 存在空 facility_id，请修正后重试。");
            }
            publishFacilities.add(row(
                    "facilityId", facilityId,
                    "pileNo", stringValue(facility.getPileNo()),
                    "direction", normalizeDirectionValue(facility.getDirection(), DIRECTION_HAMI),
                    "type", stringValue(facility.getFacilityType()),
                    "segment", stringValue(facility.getSegment())
            ));
        }

        List<ClosureDeviceStatic> devices = closureDeviceStaticService.getEnabledDevices();
        if (devices.isEmpty()) {
            throw new IllegalStateException("closure_device_static 未查询到启用数据，请先执行静态表初始化 SQL。");
        }
        closureDevices.clear();
        for (ClosureDeviceStatic device : devices) {
            String deviceId = stringValue(device.getDeviceId());
            if (deviceId.isBlank()) {
                throw new IllegalStateException("closure_device_static 存在空 device_id，请修正后重试。");
            }
            closureDevices.add(row(
                    "deviceId", deviceId,
                    "warehouse", stringValue(device.getWarehouse()),
                    "deviceType", stringValue(device.getDeviceType()),
                    "quantity", intValue(device.getQuantity(), 0),
                    "available", intValue(device.getAvailable(), 0) == 1
            ));
        }

        List<DutyStaffStatic> staffRows = dutyStaffStaticService.getEnabledStaff();
        if (staffRows.isEmpty()) {
            throw new IllegalStateException("duty_staff_static 未查询到启用数据，请先执行静态表初始化 SQL。");
        }
        staffList.clear();
        for (DutyStaffStatic staff : staffRows) {
            String staffId = stringValue(staff.getStaffId());
            if (staffId.isBlank()) {
                throw new IllegalStateException("duty_staff_static 存在空 staff_id，请修正后重试。");
            }
            staffList.add(row(
                    "staffId", staffId,
                    "name", stringValue(staff.getName()),
                    "onDuty", intValue(staff.getOnDuty(), 0) == 1,
                    "teamId", stringValue(staff.getTeamId()),
                    "phone", stringValue(staff.getPhone())
            ));
        }

        List<DutyTeamStatic> teams = dutyTeamStaticService.getEnabledTeams();
        if (teams.isEmpty()) {
            throw new IllegalStateException("duty_team_static 未查询到启用数据，请先执行静态表初始化 SQL。");
        }
        dutyTeams.clear();
        for (DutyTeamStatic team : teams) {
            String teamId = stringValue(team.getTeamId());
            if (teamId.isBlank()) {
                throw new IllegalStateException("duty_team_static 存在空 team_id，请修正后重试。");
            }
            String dispatchState = stringValue(team.getDispatchState());
            if (dispatchState.isBlank()) {
                dispatchState = "READY";
            }
            dutyTeams.add(row(
                    "teamId", teamId,
                    "name", stringValue(team.getName()),
                    "leaderId", stringValue(team.getLeaderId()),
                    "node", stringValue(team.getNode()),
                    "dispatchState", dispatchState,
                    "memberIds", parseMemberIds(team.getMemberIds())
            ));
        }
    }

    /**
     * 初始化预案库。
     *
     * SQL 优先来源：
     * 1. control_plan_static（表1-7）
     * 2. speed_threshold_static（表1-6）
     * 3. control_interval_static（表1-5）
     */
    private void initPlanLibrary() {
        List<ControlPlanStatic> plans = controlPlanStaticService.getEnabledPlans();
        if (plans.isEmpty()) {
            throw new IllegalStateException("control_plan_static 未查询到启用数据，请先执行静态表初始化 SQL。");
        }

        Map<Integer, SpeedThresholdStatic> thresholdByLevel = new LinkedHashMap<>();
        for (SpeedThresholdStatic threshold : speedThresholdStaticService.getEnabledThresholds()) {
            int level = parseControlLevelName(threshold.getControlLevelName(), -1);
            if (level > 0) {
                thresholdByLevel.put(level, threshold);
            }
        }

        controlPlanLibrary.clear();
        vmsContentLibrary.clear();
        for (ControlPlanStatic plan : plans) {
            int level = parseControlLevelName(plan.getControlLevelName(), -1);
            if (level <= 0) {
                continue;
            }

            SpeedThresholdStatic threshold = thresholdByLevel.get(level);
            int minWind = threshold == null || threshold.getMinWindLevel() == null ? defaultMinWindByLevel(level) : threshold.getMinWindLevel();
            int maxWind = threshold == null || threshold.getMaxWindLevel() == null ? defaultMaxWindByLevel(level) : threshold.getMaxWindLevel();
            int passengerLimit = threshold == null ? defaultPassengerLimitByLevel(level) : intValue(threshold.getLightVehicleSpeedLimit(), defaultPassengerLimitByLevel(level));
            int freightLimit = threshold == null ? defaultFreightLimitByLevel(level) : intValue(threshold.getHeavyVehicleSpeedLimit(), defaultFreightLimitByLevel(level));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("level", level);
            row.put("levelName", stringValue(plan.getControlLevelName()));
            row.put("windLevelDesc", stringValue(plan.getWindLevelDesc()));
            row.put("minWindLevel", minWind);
            row.put("maxWindLevel", maxWind);
            row.put("passengerSpeedLimit", passengerLimit);
            row.put("freightSpeedLimit", freightLimit);
            row.put("description", stringValue(plan.getRiskSectionPlan()));
            row.put("riskSectionPlan", stringValue(plan.getRiskSectionPlan()));
            row.put("upstreamExitPlan", stringValue(plan.getUpstreamExitPlan()));
            row.put("upstreamEntryPlan", stringValue(plan.getUpstreamEntryPlan()));
            row.put("upstreamServiceAreaPlan", stringValue(plan.getUpstreamServiceAreaPlan()));
            controlPlanLibrary.put(level, row);

            vmsContentLibrary.put(level, buildVmsContent(plan));
        }

        dispatchPlanLibrary.clear();
        List<ControlIntervalStatic> intervals = controlIntervalStaticService.getEnabledIntervals();
        if (intervals.isEmpty()) {
            throw new IllegalStateException("control_interval_static 未查询到启用数据，请先执行静态表初始化 SQL。");
        }
        for (ControlIntervalStatic interval : intervals) {
            int direction = normalizeDirectionValue(interval.getDirection(), DIRECTION_HAMI);
            String startStake = stringValue(interval.getStartStake());
            String endStake = stringValue(interval.getEndStake());
            if (startStake.isBlank() || endStake.isBlank()) {
                throw new IllegalStateException("control_interval_static 存在空起止桩号，请修正后重试。");
            }
            String segment = stringValue(interval.getIntervalName());
            if (segment.isBlank()) {
                segment = buildIntervalName(interval, direction);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("segment", segment);
            row.put("controlPoint", startStake);
            row.put("startStake", startStake);
            row.put("endStake", endStake);
            row.put("direction", direction);
            row.put("contactStaff", "");
            row.put("teamId", "");
            row.put("warehouse", "");
            dispatchPlanLibrary.put(segment, row);
        }
    }

    /**
     * 初始化执行态管控等级。
     *
     * 默认等级使用预案库中的“最常规等级”。
     */
    private void initExecutionState() {
        currentControlLevelBySegment.clear();
        int defaultLevel = getDefaultControlLevel();
        for (Map<String, Object> section : fullLineWindSections) {
            String segment = stringValue(section.get("segmentName"));
            if (!segment.isBlank()) {
                currentControlLevelBySegment.put(segment, defaultLevel);
            }
        }
    }

    /**
     * 创建风区路段标准行。
     */
    private Map<String, Object> newWindSection(String segmentId, String segmentName, int direction, String color, int real, int forecast, int max72h) {
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

    /**
     * 根据阈值静态行构建风级阈值记录。
     */
    private Map<String, Object> buildThresholdRow(int windLevel, SpeedThresholdStatic threshold, int controlLevel) {
        Map<String, Object> row = new LinkedHashMap<>();
        int passenger = intValue(threshold.getLightVehicleSpeedLimit(), defaultPassengerLimitByLevel(controlLevel));
        int freight = intValue(threshold.getHeavyVehicleSpeedLimit(), defaultFreightLimitByLevel(controlLevel));
        row.put("windLevel", windLevel);
        row.put("controlLevel", controlLevel > 0 ? controlLevel : mapWindToControlLevel(windLevel));
        row.put("controlLevelName", stringValue(threshold.getControlLevelName()));
        row.put("windLevelDesc", stringValue(threshold.getWindLevelDesc()));
        row.put("passengerSpeedLimit", passenger);
        row.put("freightSpeedLimit", freight);
        row.put("dangerousGoodsSpeedLimit", freight);
        return row;
    }

    /**
     * 风级阈值默认行（静态表缺失时兜底）。
     */
    private Map<String, Object> defaultThresholdRow(int windLevel) {
        int controlLevel = mapWindToControlLevel(windLevel);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("windLevel", windLevel);
        row.put("controlLevel", controlLevel);
        row.put("controlLevelName", levelName(controlLevel));
        row.put("windLevelDesc", windLevel + "级");
        row.put("passengerSpeedLimit", defaultPassengerLimitByLevel(controlLevel));
        row.put("freightSpeedLimit", defaultFreightLimitByLevel(controlLevel));
        row.put("dangerousGoodsSpeedLimit", defaultFreightLimitByLevel(controlLevel));
        return row;
    }

    /**
     * 将 direction 字段归一化成 1 或 2。
     */
    private void normalizeDirectionField(Map<String, Object> row) {
        if (row == null || !row.containsKey("direction")) {
            return;
        }
        row.put("direction", normalizeDirectionValue(row.get("direction"), DIRECTION_HAMI));
    }

    /**
     * 方向值归一化（支持数字、中文、英文）。
     */
    private int normalizeDirectionValue(Object raw, int defaultDirection) {
        if (raw == null) {
            return defaultDirection;
        }
        if (raw instanceof Number n) {
            int value = n.intValue();
            return value == DIRECTION_TURPAN ? DIRECTION_TURPAN : DIRECTION_HAMI;
        }
        return normalizeDirectionValue(String.valueOf(raw), defaultDirection);
    }

    /**
     * 方向文本归一化。
     */
    private int normalizeDirectionValue(String text, int defaultDirection) {
        if (text == null || text.isBlank()) {
            return defaultDirection;
        }
        String s = text.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(s)
                || "上行".equals(s)
                || "吐鲁番".equals(s)
                || "turpan".equals(s)
                || "toez".equals(s)
                || "to_ez".equals(s)
                || "右幅".equals(s)
                || "right".equals(s)) {
            return DIRECTION_TURPAN;
        }
        if ("2".equals(s)
                || "下行".equals(s)
                || "哈密".equals(s)
                || "hami".equals(s)
                || "towh".equals(s)
                || "to_wh".equals(s)
                || "左幅".equals(s)
                || "left".equals(s)) {
            return DIRECTION_HAMI;
        }
        return defaultDirection;
    }

    /**
     * 解析“一级/二级/三级/四级/五级/数字”到等级数值。
     */
    private int parseControlLevelName(String controlLevelName, int defaultValue) {
        if (controlLevelName == null || controlLevelName.isBlank()) {
            return defaultValue;
        }
        String text = controlLevelName.trim();
        if (text.contains("一级")) {
            return 1;
        }
        if (text.contains("二级")) {
            return 2;
        }
        if (text.contains("三级")) {
            return 3;
        }
        if (text.contains("四级")) {
            return 4;
        }
        if (text.contains("五级")) {
            return 5;
        }

        Matcher matcher = CONTROL_LEVEL_DIGIT_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                if (value >= 1 && value <= 5) {
                    return value;
                }
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 构建路段展示名称。
     */
    private String buildSegmentName(RoadSegmentStatic segment, int direction) {
        String start = stringValue(segment.getStartStake());
        String end = stringValue(segment.getEndStake());
        String segmentType = stringValue(segment.getSegmentType());
        String directionName = direction == DIRECTION_TURPAN ? "吐鲁番" : "哈密";
        if (segmentType.isBlank()) {
            return directionName + " " + start + "-" + end;
        }
        return directionName + " " + start + "-" + end + "（" + segmentType + "）";
    }

    /**
     * 构建区间展示名称。
     */
    private String buildIntervalName(ControlIntervalStatic interval, int direction) {
        String directionName = direction == DIRECTION_TURPAN ? "吐鲁番方向" : "哈密方向";
        return directionName + stringValue(interval.getStartStake()) + "-" + stringValue(interval.getEndStake());
    }

    /**
     * 解析班组成员 ID：支持 CSV 和 ["A","B"] 格式。
     */
    private List<String> parseMemberIds(String memberIdsRaw) {
        if (memberIdsRaw == null || memberIdsRaw.isBlank()) {
            return new ArrayList<>();
        }
        String normalized = memberIdsRaw.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        normalized = normalized.replace("，", ",").replace(";", ",");

        List<String> memberIds = new ArrayList<>();
        for (String token : normalized.split(",")) {
            String id = trimOuterQuotes(token);
            if (!id.isBlank() && !memberIds.contains(id)) {
                memberIds.add(id);
            }
        }
        return memberIds;
    }

    /**
     * 去除字段外围成对引号。
     */
    private String trimOuterQuotes(String text) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        while (value.length() >= 2) {
            boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
            boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
            if (!doubleQuoted && !singleQuoted) {
                break;
            }
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    /**
     * 拼装 VMS 发布文案。
     */
    private String buildVmsContent(ControlPlanStatic plan) {
        return "区段内：" + stringValue(plan.getRiskSectionPlan())
                + "；上游出口：" + stringValue(plan.getUpstreamExitPlan())
                + "；上游入口：" + stringValue(plan.getUpstreamEntryPlan())
                + "；上游服务区：" + stringValue(plan.getUpstreamServiceAreaPlan());
    }

    /**
     * 根据风级映射前端色块颜色。
     */
    private String colorByWindLevel(int windLevel) {
        if (windLevel >= 11) {
            return "red";
        }
        if (windLevel >= 9) {
            return "yellow";
        }
        return "green";
    }

    /**
     * 等级数字转中文名称。
     */
    private String levelName(int level) {
        return switch (level) {
            case 1 -> "一级";
            case 2 -> "二级";
            case 3 -> "三级";
            case 4 -> "四级";
            case 5 -> "五级";
            default -> "未知";
        };
    }

    /**
     * 不同管控等级对应的默认最小风级。
     */
    private int defaultMinWindByLevel(int level) {
        return switch (level) {
            case 1 -> 12;
            case 2 -> 11;
            case 3 -> 9;
            case 4 -> 7;
            default -> 1;
        };
    }

    /**
     * 不同管控等级对应的默认最大风级。
     */
    private int defaultMaxWindByLevel(int level) {
        return switch (level) {
            case 1 -> 12;
            case 2 -> 11;
            case 3 -> 10;
            case 4 -> 8;
            default -> 6;
        };
    }

    /**
     * 不同管控等级对应的小客车限速默认值。
     */
    private int defaultPassengerLimitByLevel(int level) {
        return switch (level) {
            case 1 -> 0;
            case 2 -> 60;
            case 3 -> 60;
            case 4 -> 80;
            default -> 120;
        };
    }

    /**
     * 不同管控等级对应的客货车限速默认值。
     */
    private int defaultFreightLimitByLevel(int level) {
        return switch (level) {
            case 1 -> 0;
            case 2 -> 0;
            case 3 -> 40;
            case 4 -> 60;
            default -> 80;
        };
    }
}
