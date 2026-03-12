package com.wut.screenwebsx.Service.DataPreSubService;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.MessagePrintUtil;
import com.wut.screendbmysqlsx.Model.*;
import com.wut.screendbmysqlsx.Service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class DeviceDataPreService {
    private final RadarInfoService radarInfoService;
    private final RadarMetricService radarMetricService;
    private final FiberMetricService fiberMetricService;
    private final FiberSecMetricService fiberSecMetricService;
    private final RadarSecMetricService radarSecMetricService;
    private final RadarAllSecMetricService radarAllSecMetricService;

    @Autowired
    public DeviceDataPreService(RadarInfoService radarInfoService, RadarMetricService radarMetricService, FiberMetricService fiberMetricService, FiberSecMetricService fiberSecMetricService, RadarSecMetricService radarSecMetricService, RadarAllSecMetricService radarAllSecMetricService) {
        this.radarInfoService = radarInfoService;
        this.radarMetricService = radarMetricService;
        this.fiberMetricService = fiberMetricService;
        this.fiberSecMetricService = fiberSecMetricService;
        this.radarSecMetricService = radarSecMetricService;
        this.radarAllSecMetricService = radarAllSecMetricService;
    }

    public List<RadarInfo> initRadarInfoList(int mode) {
        List<RadarInfo> radarInfoList = new ArrayList<>();
        try {
            List<RadarInfo> data = switch (mode) {
                case 0 -> radarInfoService.getAllRadarInfo();
                case 1 -> radarInfoService.getEnabledRadarInfo();
                default -> null;
            };
            if (!CollectionEmptyUtil.forList(data)) {
                assert data != null;
                radarInfoList.addAll(data);
                radarInfoList.sort(Comparator.comparingInt(RadarInfo::getRid));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initRadarInfoList"); }
        return radarInfoList;
    }

    public List<RadarMetric> initRadarMetricRealTimeList(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<RadarMetric> radarMetricList = new ArrayList<>();
        try {
            List<RadarMetric> data = radarMetricService.getLatestList(tableDateStr);
            if (!CollectionEmptyUtil.forList(data)) {
                radarMetricList.addAll(data);
                radarMetricList.sort(Comparator.comparingDouble(RadarMetric::getRid));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initRadarMetricRealTimeList"); }
        return radarMetricList;
    }

    public List<FiberSecMetric> initLatestFiberSecMetricList(long timestamp, int mode) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<FiberSecMetric> fiberSecMetricList = new ArrayList<>();
        try {
            List<FiberSecMetric> data = switch (mode) {
                case 0 -> fiberSecMetricService.getListByTimestamp(tableDateStr, timestamp);
                case 1 -> fiberSecMetricService.getLatestList(tableDateStr);
                default -> null;
            };
            if (!CollectionEmptyUtil.forList(data)) {
                assert data != null;
                fiberSecMetricList.addAll(data);
                fiberSecMetricList.sort(Comparator.comparingDouble(FiberSecMetric::getXsecValue));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initLatestFiberSecMetricList"); }
        return fiberSecMetricList;
    }

    public List<RadarSecMetric> initLatestRadarSecMetricList(long timestamp, int rid, int mode) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<RadarSecMetric> radarSecMetricList = new ArrayList<>();
        if (rid <= 0) { return radarSecMetricList; }
        try {
            List<RadarSecMetric> data = switch (mode) {
                case 0 -> radarSecMetricService.getListByTimestampAndRid(tableDateStr, timestamp, rid);
                case 1 -> radarSecMetricService.getLatestListByRid(tableDateStr, rid);
                default -> null;
            };
            if (!CollectionEmptyUtil.forList(data)) {
                assert data != null;
                radarSecMetricList.addAll(data);
                radarSecMetricList.sort(Comparator.comparingDouble(RadarSecMetric::getXsecValue));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initLatestRadarSecMetricList"); }
        return radarSecMetricList;
    }

    public List<RadarMetric> initTargetRadarMetricList(TargetTimeModel targetTime, int rid) {
        List<RadarMetric> radarMetricList = new ArrayList<>();
        if (rid <= 0) { return radarMetricList; }
        try {
            List<RadarMetric> data = radarMetricService.getTargetListByRid(targetTime.getTableDateStr(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp(), rid);
            if (!CollectionEmptyUtil.forList(data)) {
                radarMetricList.addAll(data);
                radarMetricList.sort(Comparator.comparingDouble(RadarMetric::getTimestampStart));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initTargetRadarMetricList"); }
        return radarMetricList;
    }

    public List<RadarMetric> initLatestRadarMetricList(long timestamp, int rid) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<RadarMetric> radarMetricList = new ArrayList<>();
        try {
            List<RadarMetric> data = radarMetricService.getLatestListByRid(tableDateStr, rid);
            if (!CollectionEmptyUtil.forList(data)) {
                radarMetricList.addAll(data);
                radarMetricList.sort(Comparator.comparingDouble(RadarMetric::getTimestampStart));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initLatestRadarMetricList"); }
        return radarMetricList;
    }

    public List<RadarAllSecMetric> initLatestRadarAllSecMetricList(long timestamp, int mode) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<RadarAllSecMetric> radarAllSecMetricList = new ArrayList<>();
        try {
            List<RadarAllSecMetric> data = switch (mode) {
                case 0 -> radarAllSecMetricService.getListByTimestamp(tableDateStr, timestamp);
                case 1 -> radarAllSecMetricService.getLatestList(tableDateStr);
                default -> null;
            };
            if (!CollectionEmptyUtil.forList(data)) {
                assert data != null;
                radarAllSecMetricList.addAll(data);
                radarAllSecMetricList.sort(Comparator.comparingDouble(RadarAllSecMetric::getXsecValue));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initLatestRadarAllSecMetricList"); }
        return radarAllSecMetricList;
    }

    public List<FiberSecMetric> initPeriodFiberSecMetricList(long startTimestamp, long endTimestamp, double sec) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(startTimestamp);
        List<FiberSecMetric> fiberSecMetricList = new ArrayList<>();
        try {
            List<FiberSecMetric> data = fiberSecMetricService.getTargetListBySec(tableDateStr, startTimestamp, endTimestamp, sec);
            if (!CollectionEmptyUtil.forList(data)) {
                fiberSecMetricList.addAll(data);
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initPeriodFiberSecMetricList"); }
        return fiberSecMetricList;
    }

    public List<RadarSecMetric> initPeriodRadarSecMetricList(long startTimestamp, long endTimestamp, int rid) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(startTimestamp);
        List<RadarSecMetric> radarSecMetricList = new ArrayList<>();
        try {
            List<RadarSecMetric> data = radarSecMetricService.getTargetListByRid(tableDateStr, startTimestamp, endTimestamp, rid);
            if (!CollectionEmptyUtil.forList(data)) {
                radarSecMetricList.addAll(data);
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initPeriodRadarSecMetricList"); }
        return radarSecMetricList;
    }

    public List<FiberSecMetric> initTargetFiberSecMetricList(TargetTimeModel targetTime, double sec) {
        List<FiberSecMetric> fiberSecMetricList = new ArrayList<>();
        try {
            List<FiberSecMetric> data = fiberSecMetricService.getTargetListBySec(targetTime.getTableDateStr(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp(), sec);
            if (!CollectionEmptyUtil.forList(data)) {
                fiberSecMetricList.addAll(data);
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initTargetFiberSecMetricList"); }
        return fiberSecMetricList;
    }

    public List<RadarSecMetric> initTargetRadarSecMetricList(TargetTimeModel targetTime, int rid) {
        List<RadarSecMetric> radarSecMetricList = new ArrayList<>();
        try {
            List<RadarSecMetric> data = radarSecMetricService.getTargetListByRid(targetTime.getTableDateStr(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp(), rid);
            if (!CollectionEmptyUtil.forList(data)) {
                radarSecMetricList.addAll(data);
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initTargetRadarSecMetricList"); }
        return radarSecMetricList;
    }

    public List<FiberSecMetric> initRadarRealTimeFiberSecMetricList(TargetTimeModel targetTime) {
        List<FiberSecMetric> fiberSecMetricList = new ArrayList<>();
        try {
            List<FiberSecMetric> data = fiberSecMetricService.getRadarRealTimeTargetList(targetTime.getTableDateStr(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp());
            if (!CollectionEmptyUtil.forList(data)) {
                fiberSecMetricList.addAll(data);
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initRadarRealTimeFiberSecMetricList"); }
        return fiberSecMetricList;
    }

    public List<RadarAllSecMetric> initRadarRealTimeRadarAllSecMetricList(TargetTimeModel targetTime) {
        List<RadarAllSecMetric> radarAllSecMetricList = new ArrayList<>();
        try {
            List<RadarAllSecMetric> data = radarAllSecMetricService.getRadarRealTimeTargetList(targetTime.getTableDateStr(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp());
            if (!CollectionEmptyUtil.forList(data)) {
                radarAllSecMetricList.addAll(data);
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initRadarRealTimeRadarAllSecMetricList"); }
        return radarAllSecMetricList;
    }

    public List<FiberMetric> initLatestFiberMetricList(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<FiberMetric> fiberMetricList = new ArrayList<>();
        try {
            List<FiberMetric> data = fiberMetricService.getLatestList(tableDateStr);
            if (!CollectionEmptyUtil.forList(data)) {
                fiberMetricList.addAll(data.stream().sorted(Comparator.comparingLong(FiberMetric::getTimestampStart)).toList());
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initLatestFiberMetricList"); }
        return fiberMetricList;
    }

    public List<FiberMetric> initTargetFiberMetricList(TargetTimeModel targetTime) {
        List<FiberMetric> fiberMetricList = new ArrayList<>();
        try {
            List<FiberMetric> data = fiberMetricService.getTargetList(targetTime.getTableDateStr(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp());
            if (!CollectionEmptyUtil.forList(data)) {
                fiberMetricList.addAll(data);
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initTargetFiberMetricList"); }
        return fiberMetricList;
    }

}
