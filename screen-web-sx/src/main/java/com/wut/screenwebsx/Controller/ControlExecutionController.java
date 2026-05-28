package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.Wind.UpdateControlPlanStatusReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.DefaultMsgResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.WindControlExecutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 4.5 方案生成、发布与事件追踪接口。
 *
 * 提供方案草案生成、发布/解除、自动调级建议以及事件查询导出。
 */
@RestController
@RequestMapping("/api/v1")
public class ControlExecutionController {
    private final WindControlExecutionService executionService;

    /**
     * 构造控制器并注入 4.5 业务服务。
     *
     * @param executionService 执行发布业务服务
     */
    public ControlExecutionController(WindControlExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * 查询标准化管控执行流程。
     *
     * @return 执行流程说明
     */
    @GetMapping("/control-flows")
    public DefaultDataResp listControlFlows() {
        return ModelTransformUtil.getDefaultDataInstance("control flow", executionService.getExecutionFlow());
    }

    /**
     * 查询已生成管控方案列表。
     *
     * @param status 可选状态过滤：DRAFT/PUBLISHED/CLOSED
     * @return 方案列表
     */
    @GetMapping("/generated-control-plans")
    public DefaultDataResp listControlPlans(@RequestParam(value = "status", required = false) String status) {
        return ModelTransformUtil.getDefaultDataInstance("control plans", executionService.listGeneratedPlans(status));
    }

    /**
     * 兼容旧路由：仅当携带 status 参数时，视为“已生成方案列表”查询。
     */
    @GetMapping(value = "/control-plans", params = "status")
    public DefaultDataResp listControlPlansCompat(@RequestParam("status") String status) {
        return ModelTransformUtil.getDefaultDataInstance("control plans", executionService.listGeneratedPlans(status));
    }

    /**
     * 查询单个管控方案详情。
     *
     * @param planId 方案ID
     * @return 方案详情
     */
    @GetMapping("/control-plans/{planId:[0-9a-fA-F]{8}}")
    public DefaultDataResp getControlPlan(@PathVariable("planId") String planId) {
        return ModelTransformUtil.getDefaultDataInstance("control plan", executionService.getGeneratedPlan(planId));
    }

    /**
     * 生成管控方案草案。
     *
     * 根据实时/预测风级、路段和方向计算推荐等级，
     * 并保存为 DRAFT 状态方案。
     *
     * @param req 方案生成请求（兼容风级与风速两种口径）
     * @return 新生成的方案草案
     */
    @PostMapping("/control-plans")
    public DefaultDataResp createControlPlan(@RequestBody Map<String, Object> req) {
        if (req == null) {
            req = new LinkedHashMap<>();
        }
        long timestamp = longValue(req.get("timestamp"), -1L);
        if (timestamp <= 0) {
            throw new IllegalArgumentException("timestamp is required");
        }
        String segment = stringValue(req.get("segment"));
        if (segment.isBlank()) {
            throw new IllegalArgumentException("segment is required");
        }
        Integer realtimeWindLevel = intOrNull(req.get("realtimeWindLevel"));
        Integer forecastMaxWindLevel = intOrNull(req.get("forecastMaxWindLevel"));
        Double actualWindSpeedMs = doubleOrNull(req.get("actualWindSpeedMs"));
        Double forecastMaxWindSpeed2hMs = doubleOrNull(req.get("forecastMaxWindSpeed2hMs"));
        List<Double> forecastWindSpeedSeriesMs = doubleListOrEmpty(req.get("forecastWindSpeedSeriesMs"));
        Boolean forecastWindowUpdated = boolOrNull(req.get("forecastWindowUpdated"));
        Integer direction = intOrNull(req.get("direction"));
        Integer durationHours = intOrNull(req.get("durationHours"));

        boolean hasLevelPair = realtimeWindLevel != null && forecastMaxWindLevel != null;
        boolean hasSpeedPair = actualWindSpeedMs != null
                && (forecastMaxWindSpeed2hMs != null || !forecastWindSpeedSeriesMs.isEmpty());
        if (!hasLevelPair && !hasSpeedPair) {
            throw new IllegalArgumentException(
                    "realtimeWindLevel/forecastMaxWindLevel 与 actualWindSpeedMs/forecastMaxWindSpeed2hMs(或forecastWindSpeedSeriesMs) 至少提供一组"
            );
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", timestamp);
        body.put("segment", segment);
        body.put("direction", direction);
        body.put("durationHours", durationHours);
        body.put("realtimeWindLevel", realtimeWindLevel);
        body.put("forecastMaxWindLevel", forecastMaxWindLevel);
        body.put("actualWindSpeedMs", actualWindSpeedMs);
        body.put("forecastMaxWindSpeed2hMs", forecastMaxWindSpeed2hMs);
        body.put("forecastWindSpeedSeriesMs", forecastWindSpeedSeriesMs);
        body.put("forecastWindowUpdated", forecastWindowUpdated);
        return ModelTransformUtil.getDefaultDataInstance(
                "control plan generated",
                executionService.generateControlPlan(timestamp, body)
        );
    }

    /**
     * 编辑草稿方案。
     *
     * 仅允许编辑 DRAFT 状态方案，编辑字段按请求体传入内容覆盖。
     *
     * @param planId 方案ID
     * @param body 编辑内容
     * @return 编辑后的方案
     */
    @PutMapping("/control-plans/{planId:[0-9a-fA-F]{8}}")
    public DefaultDataResp updateControlPlan(@PathVariable("planId") String planId,
                                             @RequestBody Map<String, Object> body) {
        return ModelTransformUtil.getDefaultDataInstance("control plan updated", executionService.updateDraftPlan(planId, body));
    }

    /**
     * 更新方案状态（发布或解除）。
     *
     * status 仅允许：
     * PUBLISHED=发布生效，CLOSED=解除关闭。
     *
     * @param planId 方案 ID
     * @param req 状态变更请求
     * @return 变更后的方案信息
     */
    @PatchMapping("/control-plans/{planId:[0-9a-fA-F]{8}}")
    public DefaultDataResp updateControlPlanStatus(@PathVariable("planId") String planId,
                                                   @Valid @RequestBody UpdateControlPlanStatusReq req) {
        String status = req.getStatus().trim().toUpperCase();
        if ("PUBLISHED".equals(status)) {
            return ModelTransformUtil.getDefaultDataInstance("control plan published", executionService.publishPlan(planId));
        }
        if ("CLOSED".equals(status)) {
            return ModelTransformUtil.getDefaultDataInstance("control plan closed", executionService.closePlan(planId));
        }
        throw new IllegalArgumentException("status must be PUBLISHED or CLOSED");
    }

    /**
     * 删除草稿方案。
     *
     * 仅允许删除 DRAFT 状态；已发布或已关闭方案不可删除。
     *
     * @param planId 方案ID
     * @return 删除结果
     */
    @DeleteMapping("/control-plans/{planId:[0-9a-fA-F]{8}}")
    public DefaultMsgResp deleteControlPlan(@PathVariable("planId") String planId) {
        boolean ok = executionService.deleteDraftPlan(planId);
        return ModelTransformUtil.getDefaultMsgInstance(ok, "control plan deleted", ok ? "ok" : "not found");
    }

    /**
     * 查询自动调级建议。
     *
     * @param timestamp 查询时间戳（毫秒）
     * @return 自动调级建议列表
     */
    @GetMapping("/control-plan-recommendations")
    public DefaultDataResp listControlPlanRecommendations(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance("control plan recommendations", executionService.autoUpdate(timestamp));
    }

    /**
     * 查询或导出大风事件记录。
     *
     * 支持按路段、桩号区间、方向、方案、时间区间和等级筛选。
     * direction 取值：1=去往哈密方向，2=去往吐鲁番方向。
     * format=csv 时返回 CSV 文本。
     *
     * @param segment 路段（可选）
     * @param startStake 起始桩号（可选）
     * @param endStake 结束桩号（可选）
     * @param direction 方向（可选，1=去往哈密方向，2=去往吐鲁番方向）
     * @param controlPlan 方案编码（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param controlLevel 管控等级（可选）
     * @param limit 返回条数（可选）
     * @param format 输出格式（可选）
     * @return 事件列表或 CSV 导出内容
     */
    @GetMapping("/wind-events")
    public DefaultDataResp listWindEvents(@RequestParam(value = "segment", required = false) String segment,
                                          @RequestParam(value = "startStake", required = false) String startStake,
                                          @RequestParam(value = "endStake", required = false) String endStake,
                                          @RequestParam(value = "direction", required = false) Integer direction,
                                          @RequestParam(value = "controlPlan", required = false) String controlPlan,
                                          @RequestParam(value = "startTime", required = false) String startTime,
                                          @RequestParam(value = "endTime", required = false) String endTime,
                                          @RequestParam(value = "controlLevel", required = false) Integer controlLevel,
                                          @RequestParam(value = "limit", required = false) Integer limit,
                                          @RequestParam(value = "format", required = false) String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return ModelTransformUtil.getDefaultDataInstance("wind events csv", executionService.exportWindEventRecordsCsv());
        }
        return ModelTransformUtil.getDefaultDataInstance(
                "wind events",
                executionService.listWindEventRecords(segment, startStake, endStake, direction, controlPlan, startTime, endTime, controlLevel, limit)
        );
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long longValue(Object value, long defaultValue) {
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

    private Integer intOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double doubleOrNull(Object value) {
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

    private Boolean boolOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
            return false;
        }
        return null;
    }

    private List<Double> doubleListOrEmpty(Object value) {
        List<Double> list = new ArrayList<>();
        if (!(value instanceof List<?> source)) {
            return list;
        }
        for (Object item : source) {
            Double parsed = doubleOrNull(item);
            if (parsed != null) {
                list.add(parsed);
            }
        }
        return list;
    }
}
