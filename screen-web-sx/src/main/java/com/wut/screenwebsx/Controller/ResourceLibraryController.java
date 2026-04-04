package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.Wind.AssignTeamMembersReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.DefaultMsgResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.WindControlCenterService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
/**
 * 4.3 人员与设备信息库接口。
 */
public class ResourceLibraryController {
    private final WindControlCenterService windControlCenterService;

    public ResourceLibraryController(WindControlCenterService windControlCenterService) {
        this.windControlCenterService = windControlCenterService;
    }

    /**
     * 获取信息发布设施列表。
     */
    @GetMapping("/publish-facilities")
    public DefaultDataResp listPublishFacilities() {
        return ModelTransformUtil.getDefaultDataInstance("publish facilities", windControlCenterService.listPublishFacilities());
    }

    /**
     * 新增或更新信息发布设施。
     */
    @PutMapping("/publish-facilities/{facilityId}")
    public DefaultDataResp upsertPublishFacility(@PathVariable("facilityId") String facilityId,
                                                 @RequestBody Map<String, Object> body) {
        return ModelTransformUtil.getDefaultDataInstance("publish facility updated", windControlCenterService.upsertPublishFacility(facilityId, body));
    }

    /**
     * 删除信息发布设施。
     */
    @DeleteMapping("/publish-facilities/{facilityId}")
    public DefaultMsgResp deletePublishFacility(@PathVariable("facilityId") String facilityId) {
        boolean ok = windControlCenterService.removePublishFacility(facilityId);
        return ModelTransformUtil.getDefaultMsgInstance(ok, "publish facility deleted", ok ? "ok" : "not found");
    }

    /**
     * 获取封路设备列表。
     */
    @GetMapping("/closure-devices")
    public DefaultDataResp listClosureDevices() {
        return ModelTransformUtil.getDefaultDataInstance("closure devices", windControlCenterService.listClosureDevices());
    }

    /**
     * 新增或更新封路设备。
     */
    @PutMapping("/closure-devices/{deviceId}")
    public DefaultDataResp upsertClosureDevice(@PathVariable("deviceId") String deviceId,
                                               @RequestBody Map<String, Object> body) {
        return ModelTransformUtil.getDefaultDataInstance("closure device updated", windControlCenterService.upsertClosureDevice(deviceId, body));
    }

    /**
     * 删除封路设备。
     */
    @DeleteMapping("/closure-devices/{deviceId}")
    public DefaultMsgResp deleteClosureDevice(@PathVariable("deviceId") String deviceId) {
        boolean ok = windControlCenterService.removeClosureDevice(deviceId);
        return ModelTransformUtil.getDefaultMsgInstance(ok, "closure device deleted", ok ? "ok" : "not found");
    }

    /**
     * 获取人员列表。
     */
    @GetMapping("/staff")
    public DefaultDataResp listStaff() {
        return ModelTransformUtil.getDefaultDataInstance("staff list", windControlCenterService.listStaff());
    }

    /**
     * 新增或更新人员。
     */
    @PutMapping("/staff/{staffId}")
    public DefaultDataResp upsertStaff(@PathVariable("staffId") String staffId,
                                       @RequestBody Map<String, Object> body) {
        return ModelTransformUtil.getDefaultDataInstance("staff updated", windControlCenterService.upsertStaff(staffId, body));
    }

    /**
     * 删除人员。
     */
    @DeleteMapping("/staff/{staffId}")
    public DefaultMsgResp deleteStaff(@PathVariable("staffId") String staffId) {
        boolean ok = windControlCenterService.removeStaff(staffId);
        return ModelTransformUtil.getDefaultMsgInstance(ok, "staff deleted", ok ? "ok" : "not found");
    }

    /**
     * 获取班组列表。
     */
    @GetMapping("/teams")
    public DefaultDataResp listTeams() {
        return ModelTransformUtil.getDefaultDataInstance("teams", windControlCenterService.listTeams());
    }

    /**
     * 新增或更新班组。
     */
    @PutMapping("/teams/{teamId}")
    public DefaultDataResp upsertTeam(@PathVariable("teamId") String teamId,
                                      @RequestBody Map<String, Object> body) {
        return ModelTransformUtil.getDefaultDataInstance("team updated", windControlCenterService.upsertTeam(teamId, body));
    }

    /**
     * 更新班组成员关系。
     */
    @PutMapping("/teams/{teamId}/members")
    public DefaultDataResp assignTeamMembers(@PathVariable("teamId") String teamId,
                                             @Valid @RequestBody AssignTeamMembersReq req) {
        return ModelTransformUtil.getDefaultDataInstance("team members updated", windControlCenterService.assignTeamMembers(teamId, req.getMemberIds()));
    }
}
