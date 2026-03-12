package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.EventTrackFrameReq;
import com.wut.screencommonsx.Request.HistoryTrackFrameReq;
import com.wut.screencommonsx.Request.TrajTrackReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.Track.TrackFrameDataResp;
import com.wut.screencommonsx.Response.Track.TrajTrackDataResp;
import com.wut.screencommonsx.Response.Traj.TrajDetailDataResp;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screenwebsx.Service.TrajWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/traj")
public class TrajController {
    private final TrajWebService trajWebService;

    @Autowired
    public TrajController(TrajWebService trajWebService) {
        this.trajWebService = trajWebService;
    }

    @PostMapping("/track")
    public DefaultDataResp getTrajTrackData(@RequestBody TrajTrackReq req) {
        TrajTrackDataResp data = trajWebService.collectTrajTrackData(req);
        return ModelTransformUtil.getDefaultDataInstance("轨迹查询历史轨迹搜索", data);
    }

    @PostMapping("/track/event")
    public DefaultDataResp getTrackFrameData(@RequestBody EventTrackFrameReq req) {
        TrajDetailDataResp data = trajWebService.collectTrackFrameData(req);
        return ModelTransformUtil.getDefaultDataInstance("轨迹查询事件轨迹数据", data);
    }

    @PostMapping("/track/history")
    public DefaultDataResp getTrajDetailData(@RequestBody HistoryTrackFrameReq req) {
        TrajDetailDataResp data = trajWebService.collectTrajDetailData(req);
        return ModelTransformUtil.getDefaultDataInstance("轨迹查询历史轨迹数据", data);
    }

}
