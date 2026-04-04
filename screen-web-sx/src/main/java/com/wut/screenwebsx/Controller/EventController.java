package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.EventProcessReq;
import com.wut.screencommonsx.Request.EventTrackReq;
import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.DefaultMsgResp;
import com.wut.screencommonsx.Response.Event.EventCountDataResp;
import com.wut.screencommonsx.Response.Event.EventDataResp;
import com.wut.screencommonsx.Response.Event.EventDetectionResp;
import com.wut.screencommonsx.Response.Event.EventInfoDataResp;
import com.wut.screencommonsx.Response.Event.LanelineResp;
import com.wut.screencommonsx.Response.Track.EventTrackDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.EventWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 事件模块接口控制器。
 * 提供事件查询、处置、明细与检测信息等接口。
 */
@RestController
@RequestMapping("/api/v1/event")
public class EventController {
    /** 事件业务服务。 */
    private final EventWebService eventWebService;

    @Autowired
    public EventController(EventWebService eventWebService) {
        this.eventWebService = eventWebService;
    }

    /**
     * 获取当日事件数据。
     *
     * @param timestamp 毫秒时间戳
     * @return 事件统计与列表
     */
    @GetMapping("/data")
    public DefaultDataResp getEventData(@RequestParam("timestamp") String timestamp) {
        EventDataResp data = eventWebService.collectEventData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("事件检测当日事件数据", data);
    }

    /**
     * 获取事件轨迹详情。
     *
     * @param req 事件轨迹请求参数
     * @return 事件轨迹信息
     */
    @PostMapping("/track")
    public DefaultDataResp getEventTrackData(@RequestBody EventTrackReq req) {
        EventTrackDataResp data = eventWebService.collectEventTrackData(req);
        return ModelTransformUtil.getDefaultDataInstance("事件检测事件详情信息", data);
    }

    /**
     * 提交事件处置结果。
     *
     * @param req 事件处置请求
     * @return 处理结果
     */
    @PostMapping("/process")
    public DefaultMsgResp makeEventProcess(@RequestBody EventProcessReq req) {
        boolean flag = eventWebService.makeEventProcess(req);
        return ModelTransformUtil.getDefaultMsgInstance(flag, "事件检测事件状态更新", flag ? "事件状态更新成功" : "事件状态更新失败");
    }

    /**
     * 获取指定时间事件数据。
     *
     * @param req 指定时间请求参数
     * @return 指定时间事件结果
     */
    @PostMapping("/data/target")
    public DefaultDataResp getEventTargetData(@RequestBody TargetDataReq req) {
        EventDataResp data = eventWebService.collectEventTargetData(req);
        return ModelTransformUtil.getDefaultDataInstance("事件检测指定时间事件数据", data);
    }

    /**
     * 获取当日事件信息卡片。
     *
     * @param timestamp 毫秒时间戳
     * @return 事件信息
     */
    @GetMapping("/info")
    public DefaultDataResp getEventInfoData(@RequestParam("timestamp") String timestamp) {
        EventInfoDataResp data = eventWebService.collectEventInfoData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("事件检测当日事件信息", data);
    }

    /**
     * 获取事件数量统计。
     *
     * @param timestamp 毫秒时间戳
     * @return 事件数量统计
     */
    @GetMapping("/count")
    public DefaultDataResp getEventCountData(@RequestParam("timestamp") String timestamp) {
        List<EventCountDataResp> data = eventWebService.collectEventCountData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("事件检测事故数量", data);
    }

    /**
     * 获取事件检测信息。
     *
     * @param timestamp 毫秒时间戳
     * @return 事件检测记录
     */
    @GetMapping("/detection/info")
    public DefaultDataResp getEventDetectionData(@RequestParam("timestamp") String timestamp) {
        EventDetectionResp data = eventWebService.collectEventDetectionData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("事件检测信息数据", data);
    }

    /**
     * 获取事件所在车道线坐标。
     *
     * @param timestamp 毫秒时间戳
     * @return 车道线数据
     */
    @GetMapping("/laneline")
    public DefaultDataResp getLaneLineData(@RequestParam("timestamp") String timestamp) {
        List<LanelineResp> data = eventWebService.collectLanelineData(Long.parseLong(timestamp));
        return ModelTransformUtil.getDefaultDataInstance("", data);
    }
}
