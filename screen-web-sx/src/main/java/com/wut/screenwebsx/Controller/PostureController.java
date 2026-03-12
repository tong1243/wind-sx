package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.Posture.*;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screendbmysqlsx.Model.Parameters;
import com.wut.screenwebsx.Service.PostureWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posture")
public class PostureController {
    private final PostureWebService postureWebService;

    @Autowired
    public PostureController(PostureWebService postureWebService) {
        this.postureWebService = postureWebService;
    }

    @GetMapping("/data/real")
    public DefaultDataResp getPostureRealTimeData(@RequestParam("timestamp") String timestamp) {
        PostureRealTimeDataResp data = postureWebService.collectPostureRealTimeData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("态势分析实时态势数据", data);
    }

    @GetMapping("/data/period")
    public DefaultDataResp getPosturePeriodData(@RequestParam("timestamp") String timestamp) {
        PosturePeriodDataResp data = postureWebService.collectPosturePeriodData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("态势分析今日态势数据", data);
    }

    @PostMapping("/data/target")
    public DefaultDataResp getPostureTargetData(@RequestBody TargetDataReq req) {
        PosturePeriodDataResp data = postureWebService.collectPostureTargetData(req);
        return ModelTransformUtil.getDefaultDataInstance("态势分析指定时间态势数据", data);
    }
    @GetMapping("/data/bottleneckArea")
    public DefaultDataResp getBottleneckAreaState(@RequestParam("timestamp") String timestamp) {
        BottleneckAreaStateResp data = postureWebService.colletBottleneckAreaState(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("态势分析瓶颈区域状态", data);
    }
    @GetMapping("/data/parameters")
    public DefaultDataResp getParameters(@RequestParam("timestamp") String timestamp) {
        List<ParametersResp> data = postureWebService.collectParameters(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("交通参数数据", data);
    }
    @GetMapping("/data/roadNetwork")
    public DefaultDataResp getRoadNetwork(@RequestParam("timestamp") String timestamp) {
       RoadNetworkResp data = postureWebService.collectRoadNetwork(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("路网基本信息", data);
    }

    @GetMapping("/data/defaultLimitSpeed")
    public DefaultDataResp getDefaultLimitSpeed() {
        LimitSpeedResp data = postureWebService.collectDefaultLimitSpeed();
        return ModelTransformUtil.getDefaultDataInstance("默认交通限速值", data);
    }
    @GetMapping("/data/limitSpeed")
    public DefaultDataResp getLimitSpeed() {
        LimitSpeedResp data = postureWebService.collectLimitSpeed();
        return ModelTransformUtil.getDefaultDataInstance("交通限速值", data);
    }
    @GetMapping("/data/limitSpeedPlan")
    public DefaultDataResp getLimitSpeedPlan(@RequestParam("timestamp") String timestamp) {
        LimitSpeedResp data = postureWebService.collectLimitSpeedPlan(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("交通限速方案", data);
    }
    @GetMapping("/data/accLimitSpeedPlan")
    public DefaultDataResp getAccLimitSpeedPlan(@RequestParam("timestamp") String timestamp) {
        LimitSpeedResp data = postureWebService.collectAccLimitSpeedPlan(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("事件交通限速方案", data);
    }
    @GetMapping("/data/secStream")
    public DefaultDataResp getSecStream(@RequestParam("timestamp") String timestamp,  @RequestParam("sId") int sId) {
        Parameters data = postureWebService.getSecStream(Long.parseLong(timestamp), sId);
        return ModelTransformUtil.getDefaultDataInstance("断面流量", data);
    }
    @GetMapping("/data/originalData")
    public DefaultDataResp getOriginalData(@RequestParam("timestamp") String timestamp) {
        OriginalDataResp data = postureWebService.collectOriginalData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("管控前数据", data);
    }
    @GetMapping("/data/congestion")
    public DefaultDataResp getCongestionData(@RequestParam("timestamp") String timestamp) {
        CongestionDataResp data = postureWebService.collectCongestionData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("拥堵数据", data);
    }
}
