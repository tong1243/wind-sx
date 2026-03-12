package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Request.RadarTargetDataReq;
import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Response.Device.*;
import com.wut.screencommonsx.Response.PositionRecordData;
import com.wut.screencommonsx.Response.TimeRecordData;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.MessagePrintUtil;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screendbmysqlsx.Model.*;
import com.wut.screendbmysqlsx.Util.DbModelTransformUtil;
import com.wut.screenwebsx.Config.DockingInterfaceConfig.Docking;
import com.wut.screenwebsx.Context.RadarInfoDataContext;
import com.wut.screenwebsx.Context.SecInfoDataContext;
import com.wut.screenwebsx.Model.SectionIntervalModel;
import com.wut.screenwebsx.Service.DataPreSubService.DeviceDataPreService;
import com.wut.screenwebsx.Service.DataPreSubService.SectionDataPreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.wut.screencommonsx.Static.WebModuleStatic.*;

@Component
public class DeviceWebService {
    @Qualifier("webTaskAsyncPool")
    private final Executor webTaskAsyncPool;
    private final SecInfoDataContext secInfoDataContext;
    private final RadarInfoDataContext radarInfoDataContext;
    private final SectionDataPreService sectionDataPreService;
    private final DeviceDataPreService deviceDataPreService;

    @Autowired
    public DeviceWebService(Executor webTaskAsyncPool, SecInfoDataContext secInfoDataContext, RadarInfoDataContext radarInfoDataContext, SectionDataPreService sectionDataPreService, DeviceDataPreService deviceDataPreService) {
        this.webTaskAsyncPool = webTaskAsyncPool;
        this.secInfoDataContext = secInfoDataContext;
        this.radarInfoDataContext = radarInfoDataContext;
        this.sectionDataPreService = sectionDataPreService;
        this.deviceDataPreService = deviceDataPreService;
    }

    @Docking
    public DeviceDataResp collectDeviceInfoData(long timestamp) {
        List<RadarInfo> allRadarInfoList = new ArrayList<>();
        List<RadarMetric> radarMetricList = new ArrayList<>();
        try {
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> allRadarInfoList.addAll(deviceDataPreService.initRadarInfoList(0)), webTaskAsyncPool),
                    CompletableFuture.runAsync(() -> radarMetricList.addAll(deviceDataPreService.initRadarMetricRealTimeList(timestamp)), webTaskAsyncPool)
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) { MessagePrintUtil.printException(e, "collectDeviceInfoData"); }
        DeviceStatisticData statisticData = ModelTransformUtil.getDeviceStatisticInstance();
        List<DeviceInfoData> deviceInfoList = new ArrayList<>();
        if (!CollectionEmptyUtil.forList(allRadarInfoList)) {
            allRadarInfoList.stream().forEach(radarInfo -> {
                recordStatistic(statisticData, radarInfo);
                deviceInfoList.add(recordRadarInfoToDeviceInfoData(radarInfo));
            });
        }
        if (!CollectionEmptyUtil.forList(radarMetricList)) {
            long avgTimeout = radarMetricList.stream().mapToLong(RadarMetric::getAvgTimeout).filter(i -> i != 0L).reduce(0L, Long::sum);
            statisticData.setAvgTimeout(avgTimeout);
        }
        return new DeviceDataResp(statisticData, deviceInfoList);
    }

    @Docking
    public RadarRealTimeDataResp collectRadarRealTimeData(long timestamp) {
        RadarRealTimeDataResp resp = ModelTransformUtil.getRadarRealTimeDataRespInstance();
        List<Section> sectionList = sectionDataPreService.initRealTimeSectionList(timestamp);
        if (CollectionEmptyUtil.forList(sectionList)) { return resp; }
        long targetTimestamp = sectionList.get(0).getTimestampStart();
        List<FiberSecMetric> fiberSecMetricList = new ArrayList<>();
        List<RadarAllSecMetric> radarAllSecMetricList = new ArrayList<>();
        try {
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> fiberSecMetricList.addAll(deviceDataPreService.initLatestFiberSecMetricList(targetTimestamp, 0)), webTaskAsyncPool),
                    CompletableFuture.runAsync(() -> radarAllSecMetricList.addAll(deviceDataPreService.initLatestRadarAllSecMetricList(targetTimestamp, 0)), webTaskAsyncPool)
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
            if (CollectionEmptyUtil.forList(fiberSecMetricList) || CollectionEmptyUtil.forList(radarAllSecMetricList)) { return resp; }
            CompletableFuture.allOf(
                    recordSectionPartRealTimeData(sectionList, resp.getSectionFlowRecordList(), resp.getSectionSpeedRecordList()),
                    recordFiberPartRealTimeData(fiberSecMetricList, resp.getFiberFlowRecordList(), resp.getFiberSpeedRecordList()),
                    recordRadarPartRealTimeData(radarAllSecMetricList, resp.getRadarFlowRecordList(), resp.getRadarSpeedRecordList())
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) { MessagePrintUtil.printException(e, "collectRadarRealTimeData"); }
        return resp;
    }

    @Docking
    public RadarRealTimeDataResp collectRadarRealTimeDataWithTarget(TargetDataReq req) {
        RadarRealTimeDataResp resp = ModelTransformUtil.getRadarRealTimeDataRespInstance();
        TargetTimeModel targetTimeModel = DateParamParseUtil.getRadarRealTimeTargetDataTime(req);
        List<Section> sectionList = new ArrayList<>();
        List<FiberSecMetric> fiberSecMetricList = new ArrayList<>();
        List<RadarAllSecMetric> radarAllSecMetricList = new ArrayList<>();
        try {
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> sectionList.addAll(sectionDataPreService.initRadarRealTimeTargetList(targetTimeModel)), webTaskAsyncPool),
                    CompletableFuture.runAsync(() -> fiberSecMetricList.addAll(deviceDataPreService.initRadarRealTimeFiberSecMetricList(targetTimeModel)), webTaskAsyncPool),
                    CompletableFuture.runAsync(() -> radarAllSecMetricList.addAll(deviceDataPreService.initRadarRealTimeRadarAllSecMetricList(targetTimeModel)), webTaskAsyncPool)
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
            if (CollectionEmptyUtil.forList(sectionList) || CollectionEmptyUtil.forList(fiberSecMetricList) || CollectionEmptyUtil.forList(radarAllSecMetricList)) { return resp; }
            CompletableFuture.allOf(
                    recordSectionPartRealTimeData(sectionList, resp.getSectionFlowRecordList(), resp.getSectionSpeedRecordList()),
                    recordFiberPartRealTimeData(fiberSecMetricList, resp.getFiberFlowRecordList(), resp.getFiberSpeedRecordList()),
                    recordRadarPartRealTimeData(radarAllSecMetricList, resp.getRadarFlowRecordList(), resp.getRadarSpeedRecordList())
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) { MessagePrintUtil.printException(e, "collectRadarRealTimeDataWithTargetData"); }
        return resp;
    }

    @Docking
    public RadarPeriodDataResp collectRadarPeriodData(long timestamp, int rid) {
        RadarPeriodDataResp resp = ModelTransformUtil.getRadarPeriodDataRespInstance();
        SectionIntervalModel sectionIntervalModel = null;
        // 如果选择的设备是激光雷达,直接取第一个断面区间
        // 如果选择的设备是微波雷达,查找偏移表获取对应的断面区间
        if (DEVICE_LASER_RANGE.contains(rid)) {
            sectionIntervalModel = secInfoDataContext.getSecIntervalMap().get(DEVICE_LASER_SID);
        } else {
            String rip = radarInfoDataContext.getRadarIPMap().get(rid);
            int sid = radarInfoDataContext.getRotationMap().get(rip).getSid();
            sectionIntervalModel = secInfoDataContext.getSecIntervalMap().get(sid);
        }
        List<Section> sectionList = sectionDataPreService.initPeriodSectionListBySec(timestamp, sectionIntervalModel.getXsecValue()).stream().sorted(Comparator.comparingDouble(Section::getTimestampStart)).toList();
        if (CollectionEmptyUtil.forList(sectionList)) { return resp; }
        long startTimestamp = sectionList.get(0).getTimestampStart();
        long endTimestamp = sectionList.get(sectionList.size()-1).getTimestampStart();
        List<FiberSecMetric> fiberSecMetricList = new ArrayList<>();
        List<RadarSecMetric> radarSecMetricList = new ArrayList<>();
        try {
            SectionIntervalModel finalSectionIntervalModel = sectionIntervalModel;
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> fiberSecMetricList.addAll(deviceDataPreService.initPeriodFiberSecMetricList(startTimestamp, endTimestamp, finalSectionIntervalModel.getXsecValue())), webTaskAsyncPool),
                    CompletableFuture.runAsync(() -> radarSecMetricList.addAll(deviceDataPreService.initPeriodRadarSecMetricList(startTimestamp, endTimestamp, rid)), webTaskAsyncPool)
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
            if (CollectionEmptyUtil.forList(fiberSecMetricList) || CollectionEmptyUtil.forList(radarSecMetricList)) { return resp; }
            CompletableFuture.allOf(
                    recordSectionPartPeriodData(sectionList, resp.getSectionFlowTimeList(), resp.getSectionSpeedTimeList()),
                    recordFiberPartPeriodData(fiberSecMetricList, resp.getFiberFlowTimeList(), resp.getFiberSpeedTimeList()),
                    recordRadarPartPeriodData(radarSecMetricList, resp.getRadarFlowTimeList(), resp.getRadarSpeedTimeList())
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) { MessagePrintUtil.printException(e, "collectRadarPeriodData"); }
        return resp;
    }

    @Docking
    public RadarPeriodDataResp collectRadarTargetData(RadarTargetDataReq req) {
        RadarPeriodDataResp resp = ModelTransformUtil.getRadarPeriodDataRespInstance();
        TargetTimeModel targetTime = DateParamParseUtil.getTargetDataTime(ModelTransformUtil.radarTargetDataToTargetData(req));
        SectionIntervalModel sectionIntervalModel = null;
        // 如果选择的设备是激光雷达,直接取第一个断面区间
        // 如果选择的设备是微波雷达,查找偏移表获取对应的断面区间
        if (DEVICE_LASER_RANGE.contains(req.getRid())) {
            sectionIntervalModel = secInfoDataContext.getSecIntervalMap().get(DEVICE_LASER_SID);
        } else {
            String rip = radarInfoDataContext.getRadarIPMap().get(req.getRid());
            int sid = radarInfoDataContext.getRotationMap().get(rip).getSid();
            sectionIntervalModel = secInfoDataContext.getSecIntervalMap().get(sid);
        }
        List<Section> sectionList = new ArrayList<>();
        List<FiberSecMetric> fiberSecMetricList = new ArrayList<>();
        List<RadarSecMetric> radarSecMetricList = new ArrayList<>();
        try {
            SectionIntervalModel finalSectionIntervalModel = sectionIntervalModel;
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> sectionList.addAll(sectionDataPreService.initTargetSectionListBySec(targetTime, finalSectionIntervalModel.getXsecValue())), webTaskAsyncPool),
                    CompletableFuture.runAsync(() -> fiberSecMetricList.addAll(deviceDataPreService.initTargetFiberSecMetricList(targetTime, finalSectionIntervalModel.getXsecValue())), webTaskAsyncPool),
                    CompletableFuture.runAsync(() -> radarSecMetricList.addAll(deviceDataPreService.initTargetRadarSecMetricList(targetTime, req.getRid())), webTaskAsyncPool)
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
            if (CollectionEmptyUtil.forList(sectionList) || CollectionEmptyUtil.forList(fiberSecMetricList) || CollectionEmptyUtil.forList(radarSecMetricList)) { return resp; }
            CompletableFuture.allOf(
                    recordSectionPartPeriodData(sectionList, resp.getSectionFlowTimeList(), resp.getSectionSpeedTimeList()),
                    recordFiberPartPeriodData(fiberSecMetricList, resp.getFiberFlowTimeList(), resp.getFiberSpeedTimeList()),
                    recordRadarPartPeriodData(radarSecMetricList, resp.getRadarFlowTimeList(), resp.getRadarSpeedTimeList())
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) { MessagePrintUtil.printException(e, "collectRadarTargetData"); }
        return resp;
    }

    @Docking
    public DeviceRealTimeDataResp collectDeviceRealTimeData(long timestamp) {
        DeviceRealTimeDataResp resp = ModelTransformUtil.getDeviceRealTimeDataRespInstance();
        try {
            List<FiberSecMetric> fiberSecMetricList = new ArrayList<>();
            List<RadarAllSecMetric> radarAllSecMetricList = new ArrayList<>();
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> fiberSecMetricList.addAll(deviceDataPreService.initLatestFiberSecMetricList(timestamp, 1)), webTaskAsyncPool),
                    CompletableFuture.runAsync(() -> radarAllSecMetricList.addAll(deviceDataPreService.initLatestRadarAllSecMetricList(timestamp, 1)), webTaskAsyncPool)
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
            if (CollectionEmptyUtil.forList(fiberSecMetricList) || CollectionEmptyUtil.forList(radarAllSecMetricList)) { return resp; }
            // 时延参数不区分方向,对象转换时时延数据均存储在鄂州至武汉方向上
            CompletableFuture.allOf(
                    recordFiberPartRealTimeTimeoutData(fiberSecMetricList, resp.getFiberRecordList()),
                    recordRadarPartRealTimeTimeoutData(radarAllSecMetricList, resp.getRadarRecordList())
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) { MessagePrintUtil.printException(e, "collectDeviceRealTimeData"); }
        return resp;
    }

    @Docking
    public DevicePeriodDataResp collectDevicePeriodData(long timestamp, int rid) {
        List<TimeRecordData> radarTimeList = new ArrayList<>();
        List<RadarMetric> radarMetricList = deviceDataPreService.initLatestRadarMetricList(timestamp, rid);
        if (!CollectionEmptyUtil.forList(radarMetricList)) {
            radarTimeList.addAll(radarMetricList.stream().map(DbModelTransformUtil::radarMetricToTimeoutTimeRecord).toList());
        }
        return new DevicePeriodDataResp(radarTimeList);
    }

    @Docking
    public DevicePeriodDataResp collectDeviceTargetData(RadarTargetDataReq req) {
        TargetTimeModel targetTime = DateParamParseUtil.getTargetDataTime(ModelTransformUtil.radarTargetDataToTargetData(req));
        List<RadarMetric> radarMetricList = deviceDataPreService.initTargetRadarMetricList(targetTime, req.getRid());
        if (CollectionEmptyUtil.forList(radarMetricList)) { return null; }
        List<TimeRecordData> radarTimeList = radarMetricList.stream().map(DbModelTransformUtil::radarMetricToTimeoutTimeRecord).toList();
        return new DevicePeriodDataResp(radarTimeList);
    }

    @Docking
    public DevicePeriodDataResp collectDeviceFiberPeriodData(long timestamp) {
        List<TimeRecordData> fiberTimeList = new ArrayList<>();
        List<FiberMetric> fiberMetricList = deviceDataPreService.initLatestFiberMetricList(timestamp);
        if (!CollectionEmptyUtil.forList(fiberMetricList)) {
            fiberTimeList.addAll(fiberMetricList.stream().map(DbModelTransformUtil::fiberMetricToTimeoutTimeRecord).toList());
        }
        return new DevicePeriodDataResp(fiberTimeList);
    }

    @Docking
    public DevicePeriodDataResp collectDeviceFiberTargetData(TargetDataReq req) {
        List<TimeRecordData> fiberTimeList = new ArrayList<>();
        TargetTimeModel targetTime = DateParamParseUtil.getTargetDataTime(req);
        List<FiberMetric> fiberMetricList = deviceDataPreService.initTargetFiberMetricList(targetTime);
        if (!CollectionEmptyUtil.forList(fiberMetricList)) {
            fiberTimeList.addAll(fiberMetricList.stream().map(DbModelTransformUtil::fiberMetricToTimeoutTimeRecord).toList());
        }
        return new DevicePeriodDataResp(fiberTimeList);
    }

    public void recordStatistic(DeviceStatisticData statisticData, RadarInfo radarInfo) {
        statisticData.setTotal(statisticData.getTotal() + 1);
        switch (radarInfo.getState()) {
            case DEVICE_STATE_DISABLED -> statisticData.setFaulty(statisticData.getFaulty() + 1);
            case DEVICE_STATE_OFFLINE -> statisticData.setOffline(statisticData.getOffline() + 1);
            case DEVICE_STATE_ONLINE -> statisticData.setOnline(statisticData.getOnline() + 1);
            case DEVICE_STATE_HIGH_TIMEOUT -> statisticData.setHighTimeout(statisticData.getHighTimeout() + 1);
        }
    }

    public DeviceInfoData recordRadarInfoToDeviceInfoData(RadarInfo radarInfo) {
        String radarNameInTable = switch(radarInfo.getType()) {
            // 激光雷达设备名称
            case RADAR_TYPE_LASER -> "激光雷达/" + radarInfo.getRoadDirect();
            // 微波雷达设备名称
            case RADAR_TYPE_WAVE -> "微波雷达/" + radarInfo.getIp().substring(0,4);
            default -> null;
        };
        String radarPositionNameInTable = switch(radarInfo.getType()) {
            // 激光雷达设备安装断面名称
            case RADAR_TYPE_LASER -> secInfoDataContext.getSecIntervalMap().get(DEVICE_LASER_SID).getXsecName();
            // 微波雷达设备安装断面名称
            case RADAR_TYPE_WAVE -> radarInfoDataContext.getRotationMap().get(radarInfo.getIp()).getSid() != 0
                    ? secInfoDataContext.getSecIntervalMap().get(radarInfoDataContext.getRotationMap().get(radarInfo.getIp()).getSid()).getXsecName()
                    : DEFAULT_POSITION_TARGET;
            default -> null;
        };
        assert radarNameInTable != null;
        assert radarPositionNameInTable != null;
        return new DeviceInfoData(
                radarInfo.getRid(),
                radarInfo.getType(),
                radarInfo.getIp(),
                radarInfo.getRoadDirect(),
                radarNameInTable,
                radarPositionNameInTable,
                radarInfo.getState()
        );
    }

    public CompletableFuture<Void> recordSectionPartRealTimeData(List<Section> sectionList, List<PositionRecordData> sectionFlowRecordList, List<PositionRecordData> sectionSpeedRecordList) {
        return CompletableFuture.runAsync(() -> {
            if (CollectionEmptyUtil.forList(sectionList)) { return; }
            sectionList.stream().forEach(section -> {
                sectionFlowRecordList.add(DbModelTransformUtil.sectionToFlowPositionRecord(section));
                sectionSpeedRecordList.add(DbModelTransformUtil.sectionToSpeedPositionRecord(section));
            });
        }, webTaskAsyncPool);
    }

    public CompletableFuture<Void> recordFiberPartRealTimeData(List<FiberSecMetric> fiberSecMetricList, List<PositionRecordData> fiberFlowRecordList, List<PositionRecordData> fiberSpeedRecordList) {
        return CompletableFuture.runAsync(() -> {
            if (CollectionEmptyUtil.forList(fiberSecMetricList)) { return; }
            fiberSecMetricList.stream().forEach(fiberSecMetric -> {
                fiberFlowRecordList.add(DbModelTransformUtil.fiberSecMetricToFlowPositionRecord(fiberSecMetric));
                fiberSpeedRecordList.add(DbModelTransformUtil.fiberSecMetricToSpeedPositionRecord(fiberSecMetric));
            });
        }, webTaskAsyncPool);
    }

    public CompletableFuture<Void> recordRadarPartRealTimeData(List<RadarAllSecMetric> radarAllSecMetricList, List<PositionRecordData> radarFlowRecordList, List<PositionRecordData> radarSpeedRecordList) {
        return CompletableFuture.runAsync(() -> {
            if (CollectionEmptyUtil.forList(radarAllSecMetricList)) { return; }
            radarAllSecMetricList.stream().forEach(radarAllSecMetric -> {
                radarFlowRecordList.add(DbModelTransformUtil.radarAllSecMetricToFlowPositionRecord(radarAllSecMetric));
                radarSpeedRecordList.add(DbModelTransformUtil.radarAllSecMetricToSpeedPositionRecord(radarAllSecMetric));
            });
        }, webTaskAsyncPool);
    }

    public CompletableFuture<Void> recordSectionPartPeriodData(List<Section> sectionList, List<TimeRecordData> sectionFlowTimeList, List<TimeRecordData> sectionSpeedTimeList) {
        return CompletableFuture.runAsync(() -> {
            if (CollectionEmptyUtil.forList(sectionList)) { return; }
            sectionList.stream().forEach(section -> {
                sectionFlowTimeList.add(DbModelTransformUtil.sectionToFlowTimeRecord(section));
                sectionSpeedTimeList.add(DbModelTransformUtil.sectionToSpeedTimeRecord(section));
            });
        }, webTaskAsyncPool);
    }

    public CompletableFuture<Void> recordFiberPartPeriodData(List<FiberSecMetric> fiberSecMetricList, List<TimeRecordData> fiberFlowTimeList, List<TimeRecordData> fiberSpeedTimeList) {
        return CompletableFuture.runAsync(() -> {
            if (CollectionEmptyUtil.forList(fiberSecMetricList)) { return; }
            fiberSecMetricList.stream().forEach(fiberSecMetric -> {
                fiberFlowTimeList.add(DbModelTransformUtil.fiberSecMetricToFlowTimeRecord(fiberSecMetric));
                fiberSpeedTimeList.add(DbModelTransformUtil.fiberSecMetricToSpeedTimeRecord(fiberSecMetric));
            });
        }, webTaskAsyncPool);
    }

    public CompletableFuture<Void> recordRadarPartPeriodData(List<RadarSecMetric> radarSecMetricList, List<TimeRecordData> radarFlowTimeList, List<TimeRecordData> radarSpeedTimeList) {
        return CompletableFuture.runAsync(() -> {
            if (CollectionEmptyUtil.forList(radarSecMetricList)) { return; }
            radarSecMetricList.stream().forEach(radarSecMetric -> {
                radarFlowTimeList.add(DbModelTransformUtil.radarSecMetricToFlowTimeRecord(radarSecMetric));
                radarSpeedTimeList.add(DbModelTransformUtil.radarSecMetricToSpeedTimeRecord(radarSecMetric));
            });
        }, webTaskAsyncPool);
    }

    public CompletableFuture<Void> recordFiberPartRealTimeTimeoutData(List<FiberSecMetric> fiberSecMetricList, List<PositionRecordData> fiberTimeoutPositionList) {
        return CompletableFuture.runAsync(() -> {
            if (CollectionEmptyUtil.forList(fiberSecMetricList)) { return; }
            fiberTimeoutPositionList.addAll(fiberSecMetricList.stream().map(DbModelTransformUtil::fiberSecMetricToTimeoutPositionRecord).toList());
        }, webTaskAsyncPool);
    }

    public CompletableFuture<Void> recordRadarPartRealTimeTimeoutData(List<RadarAllSecMetric> radarAllSecMetricList, List<PositionRecordData> radarTimeoutPositionList) {
        return CompletableFuture.runAsync(() -> {
            if (CollectionEmptyUtil.forList(radarAllSecMetricList)) { return; }
            radarTimeoutPositionList.addAll(radarAllSecMetricList.stream().map(DbModelTransformUtil::radarAllSecMetricToTimeoutPositionRecord).toList());
        }, webTaskAsyncPool);
    }

}
