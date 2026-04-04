package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.Section.MainlineVisualizationResp;
import com.wut.screencommonsx.Response.Section.SecInfoDataResp;
import com.wut.screencommonsx.Response.Section.SectionParameterDetectResp;
import com.wut.screencommonsx.Response.Section.SectionPeriodDataResp;
import com.wut.screencommonsx.Response.Section.SectionRealTimeDataResp;
import com.wut.screencommonsx.Response.Section.ServiceAreaVehicleResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.SectionWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 断面模块接口控制器。
 * 对外提供断面实时、周期、可视化及检测能力。
 */
@RestController
@RequestMapping("/api/v1/section")
public class SectionController {
    /** 断面业务服务。 */
    private final SectionWebService sectionWebService;

    @Autowired
    public SectionController(SectionWebService sectionWebService) {
        this.sectionWebService = sectionWebService;
    }

    /**
     * 获取断面实时数据。
     *
     * @param timestamp 毫秒时间戳
     * @return 断面实时数据
     */
    @GetMapping("/data/real")
    public DefaultDataResp getSectionRealTimeData(@RequestParam("timestamp") String timestamp) {
        SectionRealTimeDataResp data = sectionWebService.collectSectionRealTimeData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("断面实时数据", data);
    }

    /**
     * 获取断面周期数据。
     *
     * @param timestamp 毫秒时间戳
     * @return 断面周期数据
     */
    @GetMapping("/data/period")
    public DefaultDataResp getSectionPeriodData(@RequestParam("timestamp") String timestamp) {
        SectionPeriodDataResp data = sectionWebService.collectSectionPeriodData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("断面周期数据", data);
    }

    /**
     * 获取断面静态信息。
     *
     * @return 断面信息数据
     */
    @GetMapping("/info")
    public DefaultDataResp getSecInfoData() {
        SecInfoDataResp data = sectionWebService.collectSecInfoData();
        return ModelTransformUtil.getDefaultDataInstance("断面信息数据", data);
    }

    /**
     * 获取全线状态可视化数据。
     *
     * @param timestamp 毫秒时间戳
     * @return 全线可视化结果
     */
    @GetMapping("/visualization/mainline")
    public DefaultDataResp getMainlineVisualizationData(@RequestParam("timestamp") String timestamp) {
        MainlineVisualizationResp data = sectionWebService.collectMainlineVisualizationData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("全线状态可视化数据", data);
    }

    /**
     * 获取断面参数检测数据。
     *
     * @param timestamp 毫秒时间戳
     * @return 断面参数检测结果
     */
    @GetMapping("/detection/parameters")
    public DefaultDataResp getSectionParameterDetectData(@RequestParam("timestamp") String timestamp) {
        SectionParameterDetectResp data = sectionWebService.collectSectionParameterDetectData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("断面参数检测数据", data);
    }

    /**
     * 获取服务区进出车辆统计。
     *
     * @param timestamp 毫秒时间戳
     * @return 服务区车辆统计结果
     */
    @GetMapping("/service-area/vehicles")
    public DefaultDataResp getServiceAreaVehicleData(@RequestParam("timestamp") String timestamp) {
        ServiceAreaVehicleResp data = sectionWebService.collectServiceAreaVehicleData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("服务区进出车辆数据", data);
    }

    /**
     * 按指定时间段查询断面数据。
     *
     * @param req 指定时间请求参数
     * @return 断面周期数据
     */
    @PostMapping("/data/target")
    public DefaultDataResp getSectionTargetData(@RequestBody TargetDataReq req) {
        SectionPeriodDataResp data = sectionWebService.collectSectionTargetData(req);
        return ModelTransformUtil.getDefaultDataInstance("断面指定时间数据", data);
    }
}
