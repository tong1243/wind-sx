package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.PlanAutoPublishService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4.5 方案自动生成发布模块接口。
 */
@RestController
@RequestMapping("/api/v1/plan-auto-publish")
public class PlanAutoPublishController {
    /** 方案自动生成发布业务服务。 */
    private final PlanAutoPublishService planAutoPublishService;

    public PlanAutoPublishController(PlanAutoPublishService planAutoPublishService) {
        this.planAutoPublishService = planAutoPublishService;
    }

    /**
     * 4.5.1 管控执行流程。
     */
    @GetMapping("/process")
    public DefaultDataResp getExecutionProcess() {
        Object data = planAutoPublishService.collectExecutionProcess();
        return ModelTransformUtil.getDefaultDataInstance("管控执行流程数据", data);
    }

    /**
     * 4.5.2 管控方案生成。
     */
    @GetMapping("/plan-generation")
    public DefaultDataResp getPlanGeneration(@RequestParam("timestamp") String timestamp,
                                             @RequestParam(value = "direction", required = false) String direction) {
        Object data = planAutoPublishService.collectPlanGeneration(Long.parseLong(timestamp), direction);
        return ModelTransformUtil.getDefaultDataInstance("管控方案生成数据", data);
    }

    /**
     * 4.5.3 方案自动更新。
     */
    @GetMapping("/plan-auto-update")
    public DefaultDataResp getPlanAutoUpdate(@RequestParam("timestamp") String timestamp,
                                             @RequestParam(value = "direction", required = false) String direction) {
        Object data = planAutoPublishService.collectPlanAutoUpdate(Long.parseLong(timestamp), direction);
        return ModelTransformUtil.getDefaultDataInstance("方案自动更新数据", data);
    }

    /**
     * 4.5.4 大风事件记录。
     */
    @GetMapping("/wind-events")
    public DefaultDataResp getWindEventRecords(@RequestParam(value = "startStake", required = false) String startStake,
                                               @RequestParam(value = "endStake", required = false) String endStake,
                                               @RequestParam(value = "direction", required = false) String direction,
                                               @RequestParam(value = "controlPlan", required = false) String controlPlan,
                                               @RequestParam(value = "startTime", required = false) String startTime,
                                               @RequestParam(value = "endTime", required = false) String endTime) {
        Object data = planAutoPublishService.collectWindEventRecords(startStake, endStake, direction, controlPlan, startTime, endTime);
        return ModelTransformUtil.getDefaultDataInstance("大风事件记录数据", data);
    }
}
