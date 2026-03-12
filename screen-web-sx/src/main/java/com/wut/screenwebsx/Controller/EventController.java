package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.EventProcessReq;
import com.wut.screencommonsx.Request.EventTrackReq;
import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.DefaultMsgResp;
import com.wut.screencommonsx.Response.Event.EventCountDataResp;
import com.wut.screencommonsx.Response.Event.EventDataResp;
import com.wut.screencommonsx.Response.Event.EventInfoDataResp;
import com.wut.screencommonsx.Response.Event.LanelineResp;
import com.wut.screencommonsx.Response.Track.EventTrackDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screendbmysqlsx.Model.Laneline;
import com.wut.screenwebsx.Service.EventWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/event")
public class EventController {
    private final EventWebService eventWebService;

    @Autowired
    public EventController(EventWebService eventWebService) {
        this.eventWebService = eventWebService;
    }

    @GetMapping("/data")
    public DefaultDataResp getEventData(@RequestParam("timestamp") String timestamp) {
        EventDataResp data = eventWebService.collectEventData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("事件检测今日事件数据", data);
    }

    @PostMapping("/track")
    public DefaultDataResp getEventTrackData(@RequestBody EventTrackReq req) {
        EventTrackDataResp data = eventWebService.collectEventTrackData(req);
        return ModelTransformUtil.getDefaultDataInstance("事件检测事件详细信息", data);
    }

    @PostMapping("/process")
    public DefaultMsgResp makeEventProcess(@RequestBody EventProcessReq req) {
        boolean flag = eventWebService.makeEventProcess(req);
        return ModelTransformUtil.getDefaultMsgInstance(flag, "事件检测事件状态更新", flag ? "事件状态更新成功" : "事件状态更新失败");
    }

    @PostMapping("/data/target")
    public DefaultDataResp getEventTargetData(@RequestBody TargetDataReq req) {
        EventDataResp data = eventWebService.collectEventTargetData(req);
        return ModelTransformUtil.getDefaultDataInstance("事件检测指定时间事件数据", data);
    }
    @GetMapping("/info")
    public DefaultDataResp getEventInfoData(@RequestParam("timestamp") String timestamp) {
        EventInfoDataResp data = eventWebService.collectEventInfoData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("事件检测今日事件信息", data);
    }
    @GetMapping("/count")
    public DefaultDataResp getEventCountData(@RequestParam("timestamp") String timestamp) {
        List<EventCountDataResp> data = eventWebService.collectEventCountData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("事件检测事故数量", data);
    }
    @GetMapping("/laneline")
    public DefaultDataResp getLaneLineData(@RequestParam("timestamp") String timestamp) {
        List<LanelineResp> data = eventWebService.collectLanelineData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("", data);
    }

}
