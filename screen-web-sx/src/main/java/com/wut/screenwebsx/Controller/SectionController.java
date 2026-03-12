package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.Section.SecInfoDataResp;
import com.wut.screencommonsx.Response.Section.SectionPeriodDataResp;
import com.wut.screencommonsx.Response.Section.SectionRealTimeDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.SectionWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/section")
public class SectionController {
    private final SectionWebService sectionWebService;

    @Autowired
    public SectionController(SectionWebService sectionWebService) {
        this.sectionWebService = sectionWebService;
    }

    @GetMapping("/data/real")
    public DefaultDataResp getSectionRealTimeData(@RequestParam("timestamp") String timestamp) {
        SectionRealTimeDataResp data = sectionWebService.collectSectionRealTimeData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("态势分析实时断面数据", data);
    }

    @GetMapping("/data/period")
    public DefaultDataResp getSectionPeriodData(@RequestParam("timestamp") String timestamp) {
        SectionPeriodDataResp data = sectionWebService.collectSectionPeriodData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("态势分析今日断面数据", data);
    }

    @GetMapping("/info")
    public DefaultDataResp getSecInfoData() {
        SecInfoDataResp data = sectionWebService.collectSecInfoData();
        return ModelTransformUtil.getDefaultDataInstance("断面区间选择项信息", data);
    }

    @PostMapping("/data/target")
    public DefaultDataResp getSectionTargetData(@RequestBody TargetDataReq req) {
        SectionPeriodDataResp data = sectionWebService.collectSectionTargetData(req);
        return ModelTransformUtil.getDefaultDataInstance("态势分析指定时间断面数据", data);
    }

}
