package com.wut.screenwebsx.Service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 方案自动生成发布模块服务（4.5）。
 */
@Component
public class PlanAutoPublishService {
    /** 第四章聚合服务。 */
    private final ControlModuleService controlModuleService;

    public PlanAutoPublishService(ControlModuleService controlModuleService) {
        this.controlModuleService = controlModuleService;
    }

    /** 4.5.1 管控执行流程。 */
    public Map<String, Object> collectExecutionProcess() {
        return controlModuleService.collectExecutionProcess();
    }

    /** 4.5.2 管控方案生成。 */
    public Map<String, Object> collectPlanGeneration(long timestamp, String direction) {
        return controlModuleService.collectPlanGeneration(timestamp, direction);
    }

    /** 4.5.3 方案自动更新。 */
    public Map<String, Object> collectPlanAutoUpdate(long timestamp, String direction) {
        return controlModuleService.collectPlanAutoUpdate(timestamp, direction);
    }

    /** 4.5.4 大风事件记录。 */
    public Map<String, Object> collectWindEventRecords(String startStake,
                                                       String endStake,
                                                       String direction,
                                                       String controlPlan,
                                                       String startTime,
                                                       String endTime) {
        return controlModuleService.collectWindEventRecords(startStake, endStake, direction, controlPlan, startTime, endTime);
    }
}
