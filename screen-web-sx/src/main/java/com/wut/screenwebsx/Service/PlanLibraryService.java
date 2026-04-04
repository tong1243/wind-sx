package com.wut.screenwebsx.Service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 管控预案库模块服务（4.4）。
 */
@Component
public class PlanLibraryService {
    /** 第四章聚合服务。 */
    private final ControlModuleService controlModuleService;

    public PlanLibraryService(ControlModuleService controlModuleService) {
        this.controlModuleService = controlModuleService;
    }

    /** 4.4.1 管控总体原则。 */
    public Map<String, Object> collectOverallPrinciples() {
        return controlModuleService.collectOverallPrinciples();
    }

    /** 4.4.2 管控方案预案库。 */
    public Map<String, Object> collectPlanLibrary() {
        return controlModuleService.collectPlanLibrary();
    }

    /** 4.4.3 可变信息发布内容。 */
    public Map<String, Object> collectVmsContent(String controlLevel) {
        return controlModuleService.collectVmsContent(controlLevel);
    }

    /** 4.4.4 人员设备调用预案库。 */
    public Map<String, Object> collectResourceDeploymentPlan() {
        return controlModuleService.collectResourceDeploymentPlan();
    }
}
