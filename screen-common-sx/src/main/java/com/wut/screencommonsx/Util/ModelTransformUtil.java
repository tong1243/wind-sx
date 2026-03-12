package com.wut.screencommonsx.Util;

import com.wut.screencommonsx.Model.CarTypeModel;
import com.wut.screencommonsx.Request.RadarTargetDataReq;
import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Request.TrajTrackReq;
import com.wut.screencommonsx.Response.DefaultDataResp;
import com.wut.screencommonsx.Response.DefaultMsgResp;
import com.wut.screencommonsx.Response.Device.DeviceRealTimeDataResp;
import com.wut.screencommonsx.Response.Device.DeviceStatisticData;
import com.wut.screencommonsx.Response.Device.RadarPeriodDataResp;
import com.wut.screencommonsx.Response.Device.RadarRealTimeDataResp;
import com.wut.screencommonsx.Response.Event.EventStatisticData;
import com.wut.screencommonsx.Response.Posture.PostureFlowTypeData;
import com.wut.screencommonsx.Response.Posture.PostureStatisticData;
import com.wut.screencommonsx.Response.Section.SectionRealTimeDataResp;

import java.util.ArrayList;

import static com.wut.screencommonsx.Static.WebModuleStatic.WEB_RESP_CODE_FAILURE;
import static com.wut.screencommonsx.Static.WebModuleStatic.WEB_RESP_CODE_SUCCESS;

public class ModelTransformUtil {
    public static EventStatisticData getEventStatisticInstance() {
        return new EventStatisticData(0,0,0,0,0,0);
    }

    public static PostureStatisticData getPostureStatisticInstance() {
        return new PostureStatisticData(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public static DeviceStatisticData getDeviceStatisticInstance() {
        return new DeviceStatisticData(0, 0, 0, 0, 0, 0L);
    }

    public static DefaultDataResp getDefaultDataInstance(String info, Object data) {
        boolean flag = data != null;
        int code = flag ? WEB_RESP_CODE_SUCCESS : WEB_RESP_CODE_FAILURE;
        return new DefaultDataResp(code, flag, info, data);
    }

    public static DefaultMsgResp getDefaultMsgInstance(boolean flag, String info, String msg) {
        int code = flag ? WEB_RESP_CODE_SUCCESS : WEB_RESP_CODE_FAILURE;
        return new DefaultMsgResp(code, flag, info, msg);
    }

    public static PostureFlowTypeData carTypeToPostureFlowTypeData(CarTypeModel entity) {
        return new PostureFlowTypeData(
                entity.getName(),
                entity.getType(),
                0,
                0,
                0.0,
                0.0
        );
    }

    public static TargetDataReq trajTrackReqToTargetData(TrajTrackReq req) {
        return new TargetDataReq(
                req.getTimestamp(),
                req.getDateTarget(),
                req.getTimeStartTarget(),
                req.getTimeEndTarget()
        );
    }

    public static RadarRealTimeDataResp getRadarRealTimeDataRespInstance() {
        return new RadarRealTimeDataResp(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    public static RadarPeriodDataResp getRadarPeriodDataRespInstance() {
        return new RadarPeriodDataResp(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    public static TargetDataReq radarTargetDataToTargetData(RadarTargetDataReq req) {
        return new TargetDataReq(
                req.getTimestamp(),
                req.getDateTarget(),
                req.getTimeStartTarget(),
                req.getTimeEndTarget()
        );
    }

    public static SectionRealTimeDataResp getSectionRealTimeDataRespInstance() {
        return new SectionRealTimeDataResp(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    public static DeviceRealTimeDataResp getDeviceRealTimeDataRespInstance() {
        return new DeviceRealTimeDataResp(
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

}
