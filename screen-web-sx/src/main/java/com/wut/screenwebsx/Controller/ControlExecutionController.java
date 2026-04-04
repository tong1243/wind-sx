package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.Wind.GenerateControlPlanReq;
import com.wut.screencommonsx.Request.Wind.UpdateControlPlanStatusReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.WindControlCenterService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
/**
 * 4.5 管控方案自动生成与执行接口。
 */
public class ControlExecutionController {
    private final WindControlCenterService windControlCenterService;

    public ControlExecutionController(WindControlCenterService windControlCenterService) {
        this.windControlCenterService = windControlCenterService;
    }

    /**
     * 获取执行流程说明。
     */
    @GetMapping("/control-flows")
    public DefaultDataResp listControlFlows() {
        return ModelTransformUtil.getDefaultDataInstance("control flow", windControlCenterService.getExecutionFlow());
    }

    /**
     * 根据风况生成管控方案草稿。
     */
    @PostMapping("/control-plans")
    public DefaultDataResp createControlPlan(@Valid @RequestBody GenerateControlPlanReq req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", req.getTimestamp());
        body.put("segment", req.getSegment());
        body.put("realtimeWindLevel", req.getRealtimeWindLevel());
        body.put("forecastMaxWindLevel", req.getForecastMaxWindLevel());
        long timestamp = req.getTimestamp();
        return ModelTransformUtil.getDefaultDataInstance("control plan generated", windControlCenterService.generateControlPlan(timestamp, body));
    }

    /**
     * 部分更新方案状态。
     */
    @PatchMapping("/control-plans/{planId}")
    public DefaultDataResp updateControlPlanStatus(@PathVariable("planId") String planId,
                                                   @Valid @RequestBody UpdateControlPlanStatusReq req) {
        String status = req.getStatus().trim().toUpperCase();
        if (!"PUBLISHED".equals(status)) {
            throw new IllegalArgumentException("only PUBLISHED status is supported currently");
        }
        return ModelTransformUtil.getDefaultDataInstance("control plan published", windControlCenterService.publishPlan(planId));
    }

    /**
     * 获取自动调级建议。
     */
    @GetMapping("/control-plan-recommendations")
    public DefaultDataResp listControlPlanRecommendations(@RequestParam("timestamp") long timestamp) {
        return ModelTransformUtil.getDefaultDataInstance("control plan recommendations", windControlCenterService.autoUpdate(timestamp));
    }

    /**
     * 查询风事件记录，支持 CSV 导出。
     */
    @GetMapping("/wind-events")
    public DefaultDataResp listWindEvents(@RequestParam(value = "segment", required = false) String segment,
                                          @RequestParam(value = "direction", required = false) String direction,
                                          @RequestParam(value = "controlLevel", required = false) Integer controlLevel,
                                          @RequestParam(value = "format", required = false) String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return ModelTransformUtil.getDefaultDataInstance("wind events csv", windControlCenterService.exportWindEventRecordsCsv());
        }
        return ModelTransformUtil.getDefaultDataInstance("wind events", windControlCenterService.listWindEventRecords(segment, direction, controlLevel));
    }
}
