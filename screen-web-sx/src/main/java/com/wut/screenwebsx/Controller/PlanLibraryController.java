package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.PlanLibraryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4.4 管控预案库模块接口。
 */
@RestController
@RequestMapping("/api/v1/plan-library")
public class PlanLibraryController {
    /** 管控预案库业务服务。 */
    private final PlanLibraryService planLibraryService;

    public PlanLibraryController(PlanLibraryService planLibraryService) {
        this.planLibraryService = planLibraryService;
    }

    /**
     * 4.4.1 管控总体原则。
     */
    @GetMapping("/overall-principles")
    public DefaultDataResp getOverallPrinciples() {
        Object data = planLibraryService.collectOverallPrinciples();
        return ModelTransformUtil.getDefaultDataInstance("管控总体原则数据", data);
    }

    /**
     * 4.4.2 管控方案预案库。
     */
    @GetMapping("/library")
    public DefaultDataResp getPlanLibrary() {
        Object data = planLibraryService.collectPlanLibrary();
        return ModelTransformUtil.getDefaultDataInstance("管控方案预案库数据", data);
    }

    /**
     * 4.4.3 可变信息发布内容。
     */
    @GetMapping("/vms-content")
    public DefaultDataResp getVmsContent(@RequestParam(value = "controlLevel", required = false) String controlLevel) {
        Object data = planLibraryService.collectVmsContent(controlLevel);
        return ModelTransformUtil.getDefaultDataInstance("可变信息发布内容数据", data);
    }

    /**
     * 4.4.4 人员设备调用预案库。
     */
    @GetMapping("/resource-deployment")
    public DefaultDataResp getResourceDeploymentPlan() {
        Object data = planLibraryService.collectResourceDeploymentPlan();
        return ModelTransformUtil.getDefaultDataInstance("人员设备调用预案库数据", data);
    }
}
