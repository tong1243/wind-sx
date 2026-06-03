package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.Wind.CreateVmsContentTemplateReq;
import com.wut.screencommonsx.Request.Wind.RenderVmsContentTemplatePreviewReq;
import com.wut.screencommonsx.Request.Wind.UpdateVmsContentTemplateReq;
import com.wut.screencommonsx.Request.Wind.UpdateVmsContentTemplateStatusReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.DefaultMsgResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.VmsContentTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 4.4.3 可变信息发布内容模板接口。
 */
@RestController
@RequestMapping("/api/v1")
public class VmsContentTemplateController {
    private final VmsContentTemplateService templateService;

    public VmsContentTemplateController(VmsContentTemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * 按管控等级查询模板列表（单次返回该等级全部记录）。
     */
    @GetMapping("/vms-content-templates")
    public DefaultDataResp listByControlLevel(@RequestParam("controlLevel") String controlLevel,
                                              @RequestParam(value = "isEnabled", required = false, defaultValue = "1") Integer isEnabled) {
        return ModelTransformUtil.getDefaultDataInstance(
                "vms content templates by level",
                templateService.listByControlLevel(controlLevel, isEnabled)
        );
    }

    /**
     * 按模板编码查询单条详情。
     */
    @GetMapping("/vms-content-templates/{templateCode}")
    public DefaultDataResp detail(@PathVariable("templateCode") String templateCode) {
        return ModelTransformUtil.getDefaultDataInstance(
                "vms content template detail",
                templateService.detailByTemplateCode(templateCode)
        );
    }

    /**
     * 新增模板。
     */
    @PostMapping("/vms-content-templates")
    public DefaultDataResp create(@Valid @RequestBody CreateVmsContentTemplateReq req) {
        return ModelTransformUtil.getDefaultDataInstance(
                "vms content template created",
                templateService.create(req)
        );
    }

    /**
     * 更新模板。
     */
    @PutMapping("/vms-content-templates/{templateCode}")
    public DefaultDataResp update(@PathVariable("templateCode") String templateCode,
                                  @Valid @RequestBody UpdateVmsContentTemplateReq req) {
        return ModelTransformUtil.getDefaultDataInstance(
                "vms content template updated",
                templateService.update(templateCode, req)
        );
    }

    /**
     * 启停模板。
     */
    @PatchMapping("/vms-content-templates/{templateCode}/status")
    public DefaultDataResp updateStatus(@PathVariable("templateCode") String templateCode,
                                        @Valid @RequestBody UpdateVmsContentTemplateStatusReq req) {
        return ModelTransformUtil.getDefaultDataInstance(
                "vms content template status updated",
                templateService.updateStatus(templateCode, req.getIsEnabled())
        );
    }

    /**
     * 删除模板。
     */
    @DeleteMapping("/vms-content-templates/{templateCode}")
    public DefaultMsgResp delete(@PathVariable("templateCode") String templateCode) {
        boolean ok = templateService.delete(templateCode);
        return ModelTransformUtil.getDefaultMsgInstance(ok, "vms content template deleted", ok ? "ok" : "not found");
    }

    /**
     * 条件匹配模板（优先车型精确命中，再回退 ALL）。
     */
    @GetMapping("/vms-content-templates/match")
    public DefaultDataResp match(@RequestParam("controlLevel") String controlLevel,
                                 @RequestParam("publishPosition") String publishPosition,
                                 @RequestParam("vehicleType") String vehicleType) {
        return ModelTransformUtil.getDefaultDataInstance(
                "vms content template matched",
                templateService.matchTemplate(controlLevel, publishPosition, vehicleType)
        );
    }

    /**
     * 模板渲染预览。
     */
    @PostMapping("/vms-content-templates/render-preview")
    public DefaultDataResp renderPreview(@Valid @RequestBody RenderVmsContentTemplatePreviewReq req) {
        return ModelTransformUtil.getDefaultDataInstance(
                "vms content preview",
                templateService.renderPreview(req)
        );
    }
}

