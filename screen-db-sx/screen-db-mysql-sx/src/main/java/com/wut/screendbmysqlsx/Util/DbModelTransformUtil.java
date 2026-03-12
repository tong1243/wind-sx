package com.wut.screendbmysqlsx.Util;

import com.wut.screencommonsx.Model.TrackDistinctModel;
import com.wut.screencommonsx.Model.TrackRecordModel;
import com.wut.screencommonsx.Response.Device.DeviceInfoData;
import com.wut.screencommonsx.Response.Event.EventInfoData;
import com.wut.screencommonsx.Response.Event.EventRoadRecordData;
import com.wut.screencommonsx.Response.PositionRecordData;
import com.wut.screencommonsx.Response.Section.SecInfoData;
import com.wut.screencommonsx.Response.Section.SectionTimeData;
import com.wut.screencommonsx.Response.TimeRecordData;
import com.wut.screencommonsx.Response.Track.EventTrackInfoData;
import com.wut.screencommonsx.Response.Track.TrajTrackInfoData;
import com.wut.screencommonsx.Response.Traj.TrajDetailDataResp;
import com.wut.screencommonsx.Response.Traj.TrajFrameData;
import com.wut.screencommonsx.Response.Traj.TrajInfoData;
import com.wut.screencommonsx.Util.DataParamParseUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screendbmysqlsx.Model.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DbModelTransformUtil {
    public static TrajFrameData trajToFrameData(Traj traj) {
        return new TrajFrameData(
                traj.getTimestamp(),
                traj.getLongitude(),
                traj.getLatitude(),
                traj.getHeadingAngle(),
                DataParamParseUtil.getPositionStr(traj.getFrenetX()),
                DataParamParseUtil.getRoundValue(traj.getSpeedX()),
                traj.getHeight()
        );
    }

    public static TrajInfoData trajToInfoData(Traj traj, int state) {
        return new TrajInfoData(
                traj.getTrajId(),
                traj.getCarId(),
                traj.getCarType(),
                traj.getRoadDirect(),
                DataParamParseUtil.getRoundValue(traj.getSpeedX() * 3.6),
                DataParamParseUtil.getPositionStr(traj.getFrenetX()),
                traj.getTimestamp(),
                state,
                new ArrayList<>(List.of(DbModelTransformUtil.trajToFrameData(traj)))
        );
    }

    public static EventInfoData eventToInfoData(CarEvent carEvent) {
        return new EventInfoData(
                Long.toString(carEvent.getUuid()),
                carEvent.getId(),
                carEvent.getTrajId(),
                carEvent.getEventType(),
                DateParamParseUtil.getEventDateTimeDataStr(carEvent.getStartTimestamp()),
                DataParamParseUtil.getPositionStr(Double.parseDouble(carEvent.getStartMileage())),
                carEvent.getStatus(),
                carEvent.getProcess()
        );
    }

    public static EventTrackInfoData trajToTrackInfoData(Traj traj) {
        return new EventTrackInfoData(
                traj.getTrajId(),
                traj.getCarId(),
                traj.getCarType(),
                traj.getRoadDirect(),
                DateParamParseUtil.getTimeDataStr(traj.getTimestamp()),
                traj.getTimestamp(),
                DataParamParseUtil.getRoundValue(traj.getSpeedX() * 3.6),
                DataParamParseUtil.getPositionStr(traj.getFrenetX()),
                new ArrayList<>()
        );
    }

    public static TrackDistinctModel trajToDistinctEntity(Traj traj) {
        return new TrackDistinctModel(
                traj.getCarId(),
                traj.getTrajId()
        );
    }

    public static TrajTrackInfoData trajAndMatchRecordToTrackInfoData(TrackRecordModel record, Traj trajStart, Traj trajEnd) {
        return new TrajTrackInfoData(
                record.getTrajId(),
                record.getFinalName(),
                record.getMatchName(),
                trajStart.getCarType(),
                trajStart.getRoadDirect(),
                DateParamParseUtil.getTimeDataStr(trajStart.getTimestamp()),
                trajStart.getTimestamp(),
                DataParamParseUtil.getPositionStr(trajStart.getFrenetX()),
                DateParamParseUtil.getTimeDataStr(trajEnd.getTimestamp()),
                trajEnd.getTimestamp(),
                DataParamParseUtil.getPositionStr(trajEnd.getFrenetX()),
                new ArrayList<>()
        );
    }

    public static SecInfoData secInfoToData(SecInfo secInfo) {
        return new SecInfoData(
                secInfo.getXsecName(),
                secInfo.getXsecValue()
        );
    }

    public static TrajDetailDataResp trajToDetailData(Traj trajStart, Traj trajEnd) {
        return new TrajDetailDataResp(
                trajStart.getTrajId(),
                trajEnd.getCarId(),
                trajStart.getRoadDirect(),
                trajStart.getCarType(),
                DateParamParseUtil.getTimeDataStr(trajStart.getTimestamp()),
                DateParamParseUtil.getTimeDataStr(trajEnd.getTimestamp()),
                DataParamParseUtil.getPositionStr(trajStart.getFrenetX()),
                DataParamParseUtil.getPositionStr(trajEnd.getFrenetX()),
                trajStart.getTimestamp(),
                trajEnd.getTimestamp(),
                DataParamParseUtil.getRoundValue(trajStart.getFrenetX()),
                DataParamParseUtil.getRoundValue(trajEnd.getFrenetX()),
                new ArrayList<>()
        );
    }

    public static PositionRecordData sectionToFlowPositionRecord(Section section) {
        return new PositionRecordData(
                section.getXsecName(),
                section.getXsecValue(),
                DataParamParseUtil.getRoundValue(section.getAvgQwh()),
                DataParamParseUtil.getRoundValue(section.getAvgQez())
        );
    }

    public static PositionRecordData sectionToSpeedPositionRecord(Section section) {
        return new PositionRecordData(
                section.getXsecName(),
                section.getXsecValue(),
                DataParamParseUtil.getRoundValue(section.getAvgVwh() * 3.6),
                DataParamParseUtil.getRoundValue(section.getAvgVez() * 3.6)
        );
    }

    public static PositionRecordData sectionToCongestionPositionRecord(Section section) {
        return new PositionRecordData(
                section.getXsecName(),
                section.getXsecValue(),
                DataParamParseUtil.getRoundValue(section.getAvgKwh()),
                DataParamParseUtil.getRoundValue(section.getAvgKez())
        );
    }

    public static PositionRecordData fiberSecMetricToFlowPositionRecord(FiberSecMetric fiberSecMetric) {
        return new PositionRecordData(
                fiberSecMetric.getXsecName(),
                fiberSecMetric.getXsecValue(),
                DataParamParseUtil.getRoundValue(fiberSecMetric.getAvgQwh()),
                DataParamParseUtil.getRoundValue(fiberSecMetric.getAvgQez())
        );
    }

    public static PositionRecordData fiberSecMetricToSpeedPositionRecord(FiberSecMetric fiberSecMetric) {
        return new PositionRecordData(
                fiberSecMetric.getXsecName(),
                fiberSecMetric.getXsecValue(),
                DataParamParseUtil.getRoundValue(fiberSecMetric.getAvgVwh() * 3.6),
                DataParamParseUtil.getRoundValue(fiberSecMetric.getAvgVez() * 3.6)
        );
    }

    public static PositionRecordData radarAllSecMetricToFlowPositionRecord(RadarAllSecMetric radarAllSecMetric) {
        return new PositionRecordData(
                radarAllSecMetric.getXsecName(),
                radarAllSecMetric.getXsecValue(),
                DataParamParseUtil.getRoundValue(radarAllSecMetric.getAvgQwh()),
                DataParamParseUtil.getRoundValue(radarAllSecMetric.getAvgQez())
        );
    }

    public static PositionRecordData radarAllSecMetricToSpeedPositionRecord(RadarAllSecMetric radarAllSecMetric) {
        return new PositionRecordData(
                radarAllSecMetric.getXsecName(),
                radarAllSecMetric.getXsecValue(),
                DataParamParseUtil.getRoundValue(radarAllSecMetric.getAvgVwh() * 3.6),
                DataParamParseUtil.getRoundValue(radarAllSecMetric.getAvgVez() * 3.6)
        );
    }

    public static TimeRecordData postureToFlowTimeRecord(Posture posture) {
        return new TimeRecordData(
                DateParamParseUtil.getTimeDataStr(posture.getTimestampStart()),
                posture.getTimestampStart(),
                DataParamParseUtil.getRoundValue(posture.getAvgQez()),
                DataParamParseUtil.getRoundValue(posture.getAvgQwh())
        );
    }

    public static TimeRecordData postureToSpeedTimeRecord(Posture posture) {
        return new TimeRecordData(
                DateParamParseUtil.getTimeDataStr(posture.getTimestampStart()),
                posture.getTimestampStart(),
                DataParamParseUtil.getRoundValue( posture.getAvgVez() * 3.6),
                DataParamParseUtil.getRoundValue( posture.getAvgVwh() * 3.6)
        );
    }
    public static TimeRecordData parametersToSpeedTimeRecord(Parameters parameters,double avgSpeed) {
        return new TimeRecordData(
                DateParamParseUtil.timestampToHourMinute(parameters.getTimeStamp()),
                parameters.getTimeStamp(),
                DataParamParseUtil.getRoundValue(0),
                DataParamParseUtil.getRoundValue(avgSpeed)
        );
    }

    public static TimeRecordData postureToCongestionTimeRecord(Posture posture) {
        return new TimeRecordData(
                DateParamParseUtil.getTimeDataStr(posture.getTimestampStart()),
                posture.getTimestampStart(),
                DataParamParseUtil.getRoundValue(posture.getAvgKez()),
                DataParamParseUtil.getRoundValue(posture.getAvgKwh())
        );
    }

    public static TimeRecordData sectionToFlowTimeRecord(Section section) {
        return new TimeRecordData(
                DateParamParseUtil.getTimeDataStr(section.getTimestampStart()),
                section.getTimestampStart(),
                DataParamParseUtil.getRoundValue(section.getAvgQez()),
                DataParamParseUtil.getRoundValue(section.getAvgQwh())
        );
    }

    public static TimeRecordData sectionToSpeedTimeRecord(Section section) {
        return new TimeRecordData(
                DateParamParseUtil.getTimeDataStr(section.getTimestampStart()),
                section.getTimestampStart(),
                DataParamParseUtil.getRoundValue(section.getAvgVez() * 3.6),
                DataParamParseUtil.getRoundValue(section.getAvgVwh() * 3.6)
        );
    }

    public static TimeRecordData sectionToCongestionTimeRecord(Section section) {
        return new TimeRecordData(
                DateParamParseUtil.getTimeDataStr(section.getTimestampStart()),
                section.getTimestampStart(),
                DataParamParseUtil.getRoundValue(section.getAvgKez()),
                DataParamParseUtil.getRoundValue(section.getAvgKwh())
        );
    }

    public static PositionRecordData fiberSecMetricToTimeoutPositionRecord(FiberSecMetric fiberSecMetric) {
        return new PositionRecordData(
                fiberSecMetric.getXsecName(),
                fiberSecMetric.getXsecValue(),
                DataParamParseUtil.getRoundValue(fiberSecMetric.getAvgTimeout()),
                0.0
        );
    }

    public static PositionRecordData radarAllSecMetricToTimeoutPositionRecord(RadarAllSecMetric radarAllSecMetric) {
        return new PositionRecordData(
                radarAllSecMetric.getXsecName(),
                radarAllSecMetric.getXsecValue(),
                DataParamParseUtil.getRoundValue(radarAllSecMetric.getAvgTimeout()),
                0.0
        );
    }

    public static TimeRecordData radarMetricToTimeoutTimeRecord(RadarMetric radarMetric) {
        return new TimeRecordData(
                DateParamParseUtil.getTimeDataStr(radarMetric.getTimestampStart()),
                radarMetric.getTimestampStart(),
                0.0,
                DataParamParseUtil.getRoundValue(radarMetric.getAvgTimeout())
        );
    }

    public static TimeRecordData fiberSecMetricToFlowTimeRecord(FiberSecMetric fiberSecMetric) {
        return new TimeRecordData(
                DateParamParseUtil.getTimeDataStr(fiberSecMetric.getTimestampStart()),
                fiberSecMetric.getTimestampStart(),
                DataParamParseUtil.getRoundValue(fiberSecMetric.getAvgQez()),
                DataParamParseUtil.getRoundValue(fiberSecMetric.getAvgQwh())
        );
    }

    public static TimeRecordData fiberSecMetricToSpeedTimeRecord(FiberSecMetric fiberSecMetric) {
        return new TimeRecordData(
                DateParamParseUtil.getTimeDataStr(fiberSecMetric.getTimestampStart()),
                fiberSecMetric.getTimestampStart(),
                DataParamParseUtil.getRoundValue(fiberSecMetric.getAvgVez() * 3.6),
                DataParamParseUtil.getRoundValue(fiberSecMetric.getAvgVwh() * 3.6)
        );
    }

    public static TimeRecordData radarSecMetricToFlowTimeRecord(RadarSecMetric radarSecMetric) {
        return new TimeRecordData(
                DateParamParseUtil.getTimeDataStr(radarSecMetric.getTimestampStart()),
                radarSecMetric.getTimestampStart(),
                DataParamParseUtil.getRoundValue(radarSecMetric.getAvgQez()),
                DataParamParseUtil.getRoundValue(radarSecMetric.getAvgQwh())
        );
    }

    public static TimeRecordData radarSecMetricToSpeedTimeRecord(RadarSecMetric radarSecMetric) {
        return new TimeRecordData(
                DateParamParseUtil.getTimeDataStr(radarSecMetric.getTimestampStart()),
                radarSecMetric.getTimestampStart(),
                DataParamParseUtil.getRoundValue(radarSecMetric.getAvgVez() * 3.6),
                DataParamParseUtil.getRoundValue(radarSecMetric.getAvgVwh() * 3.6)
        );
    }

    public static TimeRecordData fiberMetricToTimeoutTimeRecord(FiberMetric fiberMetric) {
        return new TimeRecordData(
                DateParamParseUtil.getTimeDataStr(fiberMetric.getTimestampStart()),
                fiberMetric.getTimestampStart(),
                0.0,
                DataParamParseUtil.getRoundValue(fiberMetric.getAvgTimeout())
        );
    }
    public static com.wut.screencommonsx.Response.Risk.RiskEvent convertRiskEvent(com.wut.screendbmysqlsx.Model.RiskEvent modelRiskEvent) {
        com.wut.screencommonsx.Response.Risk.RiskEvent responseRiskEvent = new com.wut.screencommonsx.Response.Risk.RiskEvent();
        // 假设 RiskEvent 响应类有这些属性和对应的 setter 方法
        responseRiskEvent.setTimestamp(modelRiskEvent.getTimestamp());
        responseRiskEvent.setCarId(modelRiskEvent.getCarId());
        responseRiskEvent.setPosition(modelRiskEvent.getPosition());
        responseRiskEvent.setSpeed(DataParamParseUtil.getRoundValue(modelRiskEvent.getSpeed()));
        responseRiskEvent.setInteSpeed(DataParamParseUtil.getRoundValue(Math.abs(modelRiskEvent.getInteSpeed())));
        responseRiskEvent.setDistanceFrontCar(modelRiskEvent.getDistanceFrontCar());
        responseRiskEvent.setRiskType(modelRiskEvent.getRiskType());
        return responseRiskEvent;
    }
    public static com.wut.screencommonsx.Response.Risk.TunnelRisk convertTunnelRisk(com.wut.screendbmysqlsx.Model.TunnelRisk modelTunnelRisk) {
        com.wut.screencommonsx.Response.Risk.TunnelRisk responseTunnelRisk = new com.wut.screencommonsx.Response.Risk.TunnelRisk();
        // 假设 TunnelRisk 响应类有这些属性和对应的 setter 方法
        responseTunnelRisk.setSId(modelTunnelRisk.getSId());
        responseTunnelRisk.setRiskLevel(modelTunnelRisk.getRiskLevel());
        responseTunnelRisk.setMaxRiskLevel(modelTunnelRisk.getMaxRiskLevel());
        responseTunnelRisk.setTimestamp(modelTunnelRisk.getTimestamp());
        responseTunnelRisk.setStream(DataParamParseUtil.getRoundValue(modelTunnelRisk.getStream()));
        responseTunnelRisk.setDensity(DataParamParseUtil.getRoundValue(modelTunnelRisk.getDensity()));
        responseTunnelRisk.setSpeed(DataParamParseUtil.getRoundValue(modelTunnelRisk.getSpeed()));
        responseTunnelRisk.setTSC(modelTunnelRisk.getTSC());
        responseTunnelRisk.setRiskCount(modelTunnelRisk.getRiskCount());
        return responseTunnelRisk;
    }
}
