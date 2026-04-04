package com.wut.screenwebsx.Service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 人员与设备信息库模块服务（4.3）。
 */
@Component
public class ResourceLibraryService {
    /** 第四章聚合服务。 */
    private final ControlModuleService controlModuleService;

    public ResourceLibraryService(ControlModuleService controlModuleService) {
        this.controlModuleService = controlModuleService;
    }

    /** 4.3.1 信息发布设施管理。 */
    public Map<String, Object> collectPublishFacilities() {
        return controlModuleService.collectPublishFacilities();
    }

    /** 4.3.2 封路设备信息管理。 */
    public Map<String, Object> collectClosureDevices() {
        return controlModuleService.collectClosureDevices();
    }

    /** 4.3.3 执勤人员信息管理。 */
    public Map<String, Object> collectDutyStaff() {
        return controlModuleService.collectDutyStaff();
    }

    /** 4.3.4 执勤班组信息编组。 */
    public Map<String, Object> collectDutyTeams() {
        return controlModuleService.collectDutyTeams();
    }
}
