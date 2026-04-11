package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.Wind.AssignTeamMembersReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.DefaultMsgResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.WindControlResourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 4.3 人员与设备信息库接口。
 *
 * 统一管理发布设施、封路设备、人员、班组以及班组成员关系，
 * 为 4.4 预案配置和 4.5 执行发布提供资源主数据。
 */
@RestController
@RequestMapping("/api/v1")
public class ResourceLibraryController {
    private final WindControlResourceService resourceService;

    /**
     * 构造控制器并注入资源库业务服务。
     *
     * @param resourceService 4.3 资源服务
     */
    public ResourceLibraryController(WindControlResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * 查询信息发布设施列表。
     *
     * @return 发布设施清单
     */
    @GetMapping("/publish-facilities")
    public DefaultDataResp listPublishFacilities() {
        return ModelTransformUtil.getDefaultDataInstance("publish facilities", resourceService.listPublishFacilities());
    }

    /**
     * 按设施 ID 新增或更新发布设施。
     *
     * @param facilityId 设施 ID
     * @param body 设施字段（桩号、类型、所属路段等）
     * @return 更新后的设施记录
     */
    @PutMapping("/publish-facilities/{facilityId}")
    public DefaultDataResp upsertPublishFacility(@PathVariable("facilityId") String facilityId,
                                                 @RequestBody Map<String, Object> body) {
        return ModelTransformUtil.getDefaultDataInstance("publish facility updated", resourceService.upsertPublishFacility(facilityId, body));
    }

    /**
     * 按设施 ID 删除发布设施。
     *
     * @param facilityId 设施 ID
     * @return 删除结果
     */
    @DeleteMapping("/publish-facilities/{facilityId}")
    public DefaultMsgResp deletePublishFacility(@PathVariable("facilityId") String facilityId) {
        boolean ok = resourceService.removePublishFacility(facilityId);
        return ModelTransformUtil.getDefaultMsgInstance(ok, "publish facility deleted", ok ? "ok" : "not found");
    }

    /**
     * 查询封路设备列表。
     *
     * @return 封路设备清单
     */
    @GetMapping("/closure-devices")
    public DefaultDataResp listClosureDevices() {
        return ModelTransformUtil.getDefaultDataInstance("closure devices", resourceService.listClosureDevices());
    }

    /**
     * 按设备 ID 新增或更新封路设备。
     *
     * @param deviceId 设备 ID
     * @param body 设备字段（仓库、类型、数量、可用状态等）
     * @return 更新后的设备记录
     */
    @PutMapping("/closure-devices/{deviceId}")
    public DefaultDataResp upsertClosureDevice(@PathVariable("deviceId") String deviceId,
                                               @RequestBody Map<String, Object> body) {
        return ModelTransformUtil.getDefaultDataInstance("closure device updated", resourceService.upsertClosureDevice(deviceId, body));
    }

    /**
     * 按设备 ID 删除封路设备。
     *
     * @param deviceId 设备 ID
     * @return 删除结果
     */
    @DeleteMapping("/closure-devices/{deviceId}")
    public DefaultMsgResp deleteClosureDevice(@PathVariable("deviceId") String deviceId) {
        boolean ok = resourceService.removeClosureDevice(deviceId);
        return ModelTransformUtil.getDefaultMsgInstance(ok, "closure device deleted", ok ? "ok" : "not found");
    }

    /**
     * 查询人员列表。
     *
     * @return 人员清单
     */
    @GetMapping("/staff")
    public DefaultDataResp listStaff() {
        return ModelTransformUtil.getDefaultDataInstance("staff list", resourceService.listStaff());
    }

    /**
     * 按人员 ID 新增或更新人员信息。
     *
     * @param staffId 人员 ID
     * @param body 人员字段（姓名、在岗状态、所属班组等）
     * @return 更新后的人员记录
     */
    @PutMapping("/staff/{staffId}")
    public DefaultDataResp upsertStaff(@PathVariable("staffId") String staffId,
                                       @RequestBody Map<String, Object> body) {
        return ModelTransformUtil.getDefaultDataInstance("staff updated", resourceService.upsertStaff(staffId, body));
    }

    /**
     * 按人员 ID 删除人员。
     *
     * @param staffId 人员 ID
     * @return 删除结果
     */
    @DeleteMapping("/staff/{staffId}")
    public DefaultMsgResp deleteStaff(@PathVariable("staffId") String staffId) {
        boolean ok = resourceService.removeStaff(staffId);
        return ModelTransformUtil.getDefaultMsgInstance(ok, "staff deleted", ok ? "ok" : "not found");
    }

    /**
     * 查询班组列表。
     *
     * @return 班组清单
     */
    @GetMapping("/teams")
    public DefaultDataResp listTeams() {
        return ModelTransformUtil.getDefaultDataInstance("teams", resourceService.listTeams());
    }

    /**
     * 按班组 ID 新增或更新班组。
     *
     * @param teamId 班组 ID
     * @param body 班组字段（名称、组长、节点、成员等）
     * @return 更新后的班组记录
     */
    @PutMapping("/teams/{teamId}")
    public DefaultDataResp upsertTeam(@PathVariable("teamId") String teamId,
                                      @RequestBody Map<String, Object> body) {
        return ModelTransformUtil.getDefaultDataInstance("team updated", resourceService.upsertTeam(teamId, body));
    }

    /**
     * 更新指定班组成员关系。
     *
     * 该接口只负责成员列表变更，组长仍需满足“组长必须属于成员列表”约束。
     *
     * @param teamId 班组 ID
     * @param req 成员分配请求
     * @return 更新后的班组记录
     */
    @PutMapping("/teams/{teamId}/members")
    public DefaultDataResp assignTeamMembers(@PathVariable("teamId") String teamId,
                                             @Valid @RequestBody AssignTeamMembersReq req) {
        return ModelTransformUtil.getDefaultDataInstance("team members updated", resourceService.assignTeamMembers(teamId, req.getMemberIds()));
    }
}
