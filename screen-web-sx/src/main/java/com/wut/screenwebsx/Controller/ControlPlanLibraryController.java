package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.Wind.UpdateControlPlanReq;
import com.wut.screencommonsx.Request.Wind.UpdateDispatchPlanReq;
import com.wut.screencommonsx.Request.Wind.UpdateVmsContentReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.WindControlPlanLibraryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
/**
 * 4.4 管控预案库接口。
 */
public class ControlPlanLibraryController {
    private final WindControlPlanLibraryService planLibraryService;

    /**
     * 构造控制器并注入预案库业务服务，负责 4.4 模块 HTTP 请求分发。
     */
    public ControlPlanLibraryController(WindControlPlanLibraryService planLibraryService) {
        this.planLibraryService = planLibraryService;
    }

    /**
     * 获取管控总体原则说明。
     */
    @GetMapping("/control-principles")
    public DefaultDataResp getPrinciples() {
        return ModelTransformUtil.getDefaultDataInstance("control principles", planLibraryService.getControlPrinciples());
    }

    /**
     * 获取分级管控预案列表。
     */
    @GetMapping("/control-plans")
    public DefaultDataResp listControlPlans() {
        return ModelTransformUtil.getDefaultDataInstance("control plans", planLibraryService.listControlPlans());
    }

    /**
     * 更新指定等级的管控预案。
     */
    @PutMapping("/control-plans/{level:\\d+}")
    public DefaultDataResp updateControlPlan(@PathVariable("level") int level,
                                             @Valid @RequestBody UpdateControlPlanReq req) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (req.getMinWindLevel() != null) body.put("minWindLevel", req.getMinWindLevel());
        if (req.getMaxWindLevel() != null) body.put("maxWindLevel", req.getMaxWindLevel());
        if (req.getPassengerSpeedLimit() != null) body.put("passengerSpeedLimit", req.getPassengerSpeedLimit());
        if (req.getFreightSpeedLimit() != null) body.put("freightSpeedLimit", req.getFreightSpeedLimit());
        if (req.getDescription() != null) body.put("description", req.getDescription());
        return ModelTransformUtil.getDefaultDataInstance("control plan updated", planLibraryService.updateControlPlanLevel(level, body));
    }

    /**
     * 获取分级 VMS 发布文案。
     */
    @GetMapping("/vms-contents")
    public DefaultDataResp listVmsContent() {
        return ModelTransformUtil.getDefaultDataInstance("vms contents", planLibraryService.listVmsContent());
    }

    /**
     * 更新指定等级的 VMS 发布文案。
     */
    @PutMapping("/vms-contents/{level}")
    public DefaultDataResp updateVmsContent(@PathVariable("level") int level,
                                            @Valid @RequestBody UpdateVmsContentReq req) {
        return ModelTransformUtil.getDefaultDataInstance("vms content updated", planLibraryService.updateVmsContent(level, req.getContent()));
    }

    /**
     * 获取人员设备调度预案。
     */
    @GetMapping("/dispatch-plans")
    public DefaultDataResp listDispatchPlans() {
        return ModelTransformUtil.getDefaultDataInstance("dispatch plans", planLibraryService.listDispatchPlans());
    }

    /**
     * 更新指定路段调度预案。
     */
    @PutMapping("/dispatch-plans/{segment}")
    public DefaultDataResp updateDispatchPlan(@PathVariable("segment") String segment,
                                              @Valid @RequestBody UpdateDispatchPlanReq req) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (req.getContactStaff() != null) body.put("contactStaff", req.getContactStaff());
        if (req.getTeamId() != null) body.put("teamId", req.getTeamId());
        if (req.getWarehouse() != null) body.put("warehouse", req.getWarehouse());
        return ModelTransformUtil.getDefaultDataInstance("dispatch plan updated", planLibraryService.updateDispatchPlan(segment, body));
    }
}

