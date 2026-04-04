package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.ResourceLibraryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4.3 人员与设备信息库模块接口。
 */
@RestController
@RequestMapping("/api/v1/resource-library")
public class ResourceLibraryController {
    /** 人员设备信息库业务服务。 */
    private final ResourceLibraryService resourceLibraryService;

    public ResourceLibraryController(ResourceLibraryService resourceLibraryService) {
        this.resourceLibraryService = resourceLibraryService;
    }

    /**
     * 4.3.1 信息发布设施管理。
     */
    @GetMapping("/publish-facilities")
    public DefaultDataResp getPublishFacilities() {
        Object data = resourceLibraryService.collectPublishFacilities();
        return ModelTransformUtil.getDefaultDataInstance("信息发布设施管理数据", data);
    }

    /**
     * 4.3.2 封路设备信息管理。
     */
    @GetMapping("/closure-devices")
    public DefaultDataResp getClosureDevices() {
        Object data = resourceLibraryService.collectClosureDevices();
        return ModelTransformUtil.getDefaultDataInstance("封路设备信息管理数据", data);
    }

    /**
     * 4.3.3 执勤人员信息管理。
     */
    @GetMapping("/duty-staff")
    public DefaultDataResp getDutyStaff() {
        Object data = resourceLibraryService.collectDutyStaff();
        return ModelTransformUtil.getDefaultDataInstance("执勤人员信息管理数据", data);
    }

    /**
     * 4.3.4 执勤班组信息编组。
     */
    @GetMapping("/duty-teams")
    public DefaultDataResp getDutyTeams() {
        Object data = resourceLibraryService.collectDutyTeams();
        return ModelTransformUtil.getDefaultDataInstance("执勤班组信息编组数据", data);
    }
}
