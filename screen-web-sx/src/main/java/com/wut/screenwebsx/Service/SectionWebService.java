package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Response.PositionRecordData;
import com.wut.screencommonsx.Response.Section.MainlineSegmentData;
import com.wut.screencommonsx.Response.Section.MainlineVisualizationResp;
import com.wut.screencommonsx.Response.Section.SecInfoData;
import com.wut.screencommonsx.Response.Section.SecInfoDataResp;
import com.wut.screencommonsx.Response.Section.SectionPeriodDataResp;
import com.wut.screencommonsx.Response.Section.SectionParameterDetectRecord;
import com.wut.screencommonsx.Response.Section.SectionParameterDetectResp;
import com.wut.screencommonsx.Response.Section.SectionRealTimeDataResp;
import com.wut.screencommonsx.Response.Section.SectionTimeData;
import com.wut.screencommonsx.Response.Section.ServiceAreaVehicleRecord;
import com.wut.screencommonsx.Response.Section.ServiceAreaVehicleResp;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DataParamParseUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screendbmysqlsx.Model.Parameters;
import com.wut.screendbmysqlsx.Model.RoadSegmentStatic;
import com.wut.screendbmysqlsx.Model.Section;
import com.wut.screendbmysqlsx.Model.Traj;
import com.wut.screendbmysqlsx.Model.TunnelSecInfo;
import com.wut.screendbmysqlsx.Service.ParametersService;
import com.wut.screendbmysqlsx.Service.RoadSegmentStaticService;
import com.wut.screendbmysqlsx.Service.SecInfoService;
import com.wut.screendbmysqlsx.Service.TrajService;
import com.wut.screendbmysqlsx.Util.DbModelTransformUtil;
import com.wut.screenwebsx.Config.DockingInterfaceConfig.Docking;
import com.wut.screenwebsx.Context.SecInfoDataContext;
import com.wut.screenwebsx.Service.DataPreSubService.SectionDataPreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 断面模块业务服务。
 * 聚合断面实时数据、全线可视化、断面参数检测及服务区车辆统计。
 */
@Component
public class SectionWebService {
    /** 哈密方向。 */
    private static final String DIR_HAMI = "\u54C8\u5BC6";
    /** 吐鲁番方向。 */
    private static final String DIR_TULUFAN = "\u5410\u9C81\u756A";
    /** 通行状态：通畅。 */
    private static final String STATUS_SMOOTH = "\u901A\u7545";
    /** 通行状态：缓行。 */
    private static final String STATUS_SLOW = "\u7F13\u884C";
    /** 通行状态：拥堵。 */
    private static final String STATUS_CONGESTED = "\u62E5\u5835";
    /** 通行状态：数据缺失。 */
    private static final String STATUS_NO_DATA = "\u6570\u636E\u7F3A\u5931";
    /** 服务区关键词。 */
    private static final String KEYWORD_SERVICE_AREA = "\u670D\u52A1\u533A";
    /** 五分钟统计窗口。 */
    private static final long WINDOW_5MIN_MS = 300000L;

    /** 断面信息上下文。 */
    private final SecInfoDataContext secInfoDataContext;
    /** 断面预处理服务。 */
    private final SectionDataPreService sectionDataPreService;
    /** 断面基础信息服务。 */
    private final SecInfoService secInfoService;
    /** 断面参数服务。 */
    private final ParametersService parametersService;
    /** 姿态分析服务。 */
    private final PostureWebService postureWebService;
    /** 路段静态信息服务。 */
    private final RoadSegmentStaticService roadSegmentStaticService;
    /** 轨迹服务。 */
    private final TrajService trajService;

    @Autowired
    public SectionWebService(SecInfoDataContext secInfoDataContext,
                             SectionDataPreService sectionDataPreService,
                             SecInfoService secInfoService,
                             ParametersService parametersService,
                             PostureWebService postureWebService,
                             RoadSegmentStaticService roadSegmentStaticService,
                             TrajService trajService) {
        this.secInfoDataContext = secInfoDataContext;
        this.sectionDataPreService = sectionDataPreService;
        this.secInfoService = secInfoService;
        this.parametersService = parametersService;
        this.postureWebService = postureWebService;
        this.roadSegmentStaticService = roadSegmentStaticService;
        this.trajService = trajService;
    }

    /**
     * 获取断面实时数据（流量、速度）。
     *
     * @param timestamp 毫秒时间戳
     * @return 断面实时数据响应
     */
    @Docking
    public SectionRealTimeDataResp collectSectionRealTimeData(long timestamp) {
        SectionRealTimeDataResp resp = ModelTransformUtil.getSectionRealTimeDataRespInstance();
        resp.setFlowRecordList(collectSecStreamRealTimeData(timestamp));
        resp.setSpeedRecordList(collectSecSpeedRealTimeData(timestamp));
        return resp;
    }

    /**
     * 获取断面周期数据。
     *
     * @param timestamp 毫秒时间戳
     * @return 断面周期数据响应
     */
    @Docking
    public SectionPeriodDataResp collectSectionPeriodData(long timestamp) {
        List<TunnelSecInfo> allTunnelSecInfo = secInfoService.getAllTunnelSecInfo();
        List<SectionTimeData> timeDataList = new ArrayList<>();
        for (TunnelSecInfo tunnelSecInfo : allTunnelSecInfo) {
            SectionTimeData timeData = new SectionTimeData();
            timeData.setXsecName(tunnelSecInfo.getRoad());
            timeData.setXsecValue(tunnelSecInfo.getEnd());
            timeData.setSpeedRecordList(postureWebService.collectAvgSpeed(timestamp));
            timeData.setFlowRecordList(postureWebService.collectAvgStream(timestamp));
            timeData.setCongestionRecordList(postureWebService.getCongestionData(tunnelSecInfo.getSid(), timestamp));
            timeDataList.add(timeData);
        }
        return new SectionPeriodDataResp(timeDataList);
    }

    /**
     * 获取指定时间段断面数据。
     *
     * @param req 指定时间请求参数
     * @return 断面周期数据响应
     */
    @Docking
    public SectionPeriodDataResp collectSectionTargetData(TargetDataReq req) {
        TargetTimeModel targetTime = DateParamParseUtil.getTargetDataTime(req);
        List<Section> sectionList = sectionDataPreService.initTargetSectionList(targetTime);
        List<SectionTimeData> timeDataList = sectionDataPreService.initSectionTimeDataList();
        if (CollectionEmptyUtil.forList(sectionList)) {
            return null;
        }
        recordSectionToTimeRecordList(sectionList, timeDataList);
        return new SectionPeriodDataResp(timeDataList);
    }

    /**
     * 获取断面基础信息。
     *
     * @return 断面信息响应
     */
    @Docking
    public SecInfoDataResp collectSecInfoData() {
        List<TunnelSecInfo> allTunnelSecInfo = secInfoService.getAllTunnelSecInfo();
        List<SecInfoData> secInfoDataList = new ArrayList<>();
        for (TunnelSecInfo tunnelSecInfo : allTunnelSecInfo) {
            secInfoDataList.add(new SecInfoData(tunnelSecInfo.getRoad(), tunnelSecInfo.getEnd()));
        }
        return new SecInfoDataResp(secInfoDataList);
    }

    /**
     * 获取全线状态可视化数据（4.1.1）。
     *
     * @param timestamp 毫秒时间戳
     * @return 全线可视化响应
     */
    @Docking
    public MainlineVisualizationResp collectMainlineVisualizationData(long timestamp) {
        List<RoadSegmentStatic> segmentStaticList = roadSegmentStaticService.getEnabledSegments();
        List<Section> sectionList = sectionDataPreService.initRealTimeSectionList(timestamp);

        Map<Integer, Section> sectionMap = new HashMap<>();
        for (Section section : sectionList) {
            if (section.getXsecValue() != null) {
                sectionMap.put(section.getXsecValue().intValue(), section);
            }
        }

        List<MainlineSegmentData> segmentDataList = new ArrayList<>();
        for (RoadSegmentStatic segment : segmentStaticList) {
            Section section = sectionMap.get(segment.getStartLocationM());
            Double avgSpeedKmh = getAvgSpeedKmh(section, segment.getDirection());

            MainlineSegmentData data = new MainlineSegmentData();
            data.setDirection(segment.getDirection());
            data.setStake(segment.getStartStake());
            data.setStartLocation(segment.getStartLocationM());
            data.setEndLocation(segment.getStartLocationM() == null ? null : segment.getStartLocationM() + 1000);
            data.setStartStake(segment.getStartStake());
            data.setEndStake(segment.getEndStake());
            data.setSegmentType(segment.getSegmentType());
            data.setAverageSpeed(avgSpeedKmh == null ? null : DataParamParseUtil.getRoundValue(avgSpeedKmh));
            data.setCongestionStatus(getCongestionStatus(avgSpeedKmh));
            data.setColor(getColorByStatus(data.getCongestionStatus()));
            segmentDataList.add(data);
        }

        return new MainlineVisualizationResp(timestamp, segmentDataList);
    }

    /**
     * 获取断面参数检测数据（4.1.2）。
     *
     * @param timestamp 毫秒时间戳
     * @return 断面参数检测响应
     */
    @Docking
    public SectionParameterDetectResp collectSectionParameterDetectData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<Parameters> parametersList = parametersService.getListByDate(tableDateStr);
        List<TunnelSecInfo> secInfoList = secInfoService.getAllTunnelSecInfo();
        List<RoadSegmentStatic> segmentStaticList = roadSegmentStaticService.getEnabledSegments();

        Map<Integer, Parameters> latestParamByRoadId = parametersList.stream().collect(
                Collectors.toMap(
                        Parameters::getRoadId,
                        item -> item,
                        (a, b) -> a.getTimeStamp() >= b.getTimeStamp() ? a : b
                )
        );

        Map<Integer, TunnelSecInfo> secInfoByEnd = secInfoList.stream().collect(
                Collectors.toMap(
                        item -> item.getEnd(),
                        item -> item,
                        (a, b) -> a
                )
        );

        List<SectionParameterDetectRecord> records = new ArrayList<>();
        for (RoadSegmentStatic segment : segmentStaticList) {
            Integer endLocation = segment.getStartLocationM() == null ? null : segment.getStartLocationM() + 1000;
            TunnelSecInfo secInfo = endLocation == null ? null : secInfoByEnd.get(endLocation);
            Parameters param = (secInfo == null) ? null : latestParamByRoadId.get(secInfo.getSid());

            Double avgSpeed = param == null ? null : DataParamParseUtil.getRoundValue(param.getSpeed());
            Integer currentVehicleCount = param == null ? null : param.getCarCount();

            SectionParameterDetectRecord record = new SectionParameterDetectRecord();
            record.setDirection(segment.getDirection());
            record.setStartLocation(segment.getStartLocationM());
            record.setEndLocation(endLocation);
            record.setStartStake(segment.getStartStake());
            record.setEndStake(segment.getEndStake());
            record.setCurrentVehicleCount(currentVehicleCount);
            record.setAverageSpeed(avgSpeed);
            record.setCongestionStatus(getCongestionStatus(avgSpeed));
            record.setSegmentType(segment.getSegmentType());
            records.add(record);
        }

        return new SectionParameterDetectResp(timestamp, records);
    }

    /**
     * 获取服务区进出车辆统计（4.1.4）。
     *
     * @param timestamp 毫秒时间戳
     * @return 服务区车辆统计响应
     */
    @Docking
    public ServiceAreaVehicleResp collectServiceAreaVehicleData(long timestamp) {
        List<RoadSegmentStatic> serviceAreaList = roadSegmentStaticService.getEnabledSegments()
                .stream()
                .filter(this::isServiceAreaSegment)
                .toList();

        if (CollectionEmptyUtil.forList(serviceAreaList)) {
            return new ServiceAreaVehicleResp(timestamp, new ArrayList<>());
        }

        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        long windowStart = Math.max(0L, timestamp - WINDOW_5MIN_MS);
        List<Traj> trajWindowList = trajService.getListByTimestampRange(tableDateStr, windowStart, timestamp);

        Map<Long, List<Traj>> trajListById = new HashMap<>();
        for (Traj traj : trajWindowList) {
            if (traj.getTrajId() == null || traj.getFrenetX() == null) {
                continue;
            }
            trajListById.computeIfAbsent(traj.getTrajId(), key -> new ArrayList<>()).add(traj);
        }

        trajListById.values().forEach(trajList ->
                trajList.sort(Comparator.comparingLong(item -> item.getTimestamp() == null ? 0L : item.getTimestamp()))
        );

        List<ServiceAreaVehicleRecord> recordList = new ArrayList<>();
        for (RoadSegmentStatic serviceArea : serviceAreaList) {
            recordList.add(buildServiceAreaVehicleRecord(serviceArea, trajListById));
        }
        return new ServiceAreaVehicleResp(timestamp, recordList);
    }

    /**
     * 将断面记录写入时间序列结构。
     *
     * @param sectionList 原始断面记录
     * @param timeDataList 目标时间序列结构
     */
    public void recordSectionToTimeRecordList(List<Section> sectionList, List<SectionTimeData> timeDataList) {
        List<Section> sortedSectionList = sectionList.stream()
                .sorted(Comparator.comparing(Section::getTimestampStart).thenComparing(Section::getXsecValue))
                .toList();

        timeDataList.forEach(timeData -> sortedSectionList.stream()
                .filter(section -> section.getXsecValue() == timeData.getXsecValue())
                .sorted(Comparator.comparingLong(Section::getTimestampStart))
                .forEach(section -> {
                    timeData.getFlowRecordList().add(DbModelTransformUtil.sectionToFlowTimeRecord(section));
                    timeData.getSpeedRecordList().add(DbModelTransformUtil.sectionToSpeedTimeRecord(section));
                    timeData.getCongestionRecordList().add(DbModelTransformUtil.sectionToCongestionTimeRecord(section));
                }));
    }

    /**
     * 获取断面实时流量点位。
     *
     * @param timestamp 毫秒时间戳
     * @return 点位流量列表
     */
    @Docking
    public List<PositionRecordData> collectSecStreamRealTimeData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<PositionRecordData> positionRecordDataList = new ArrayList<>();
        List<TunnelSecInfo> allTunnelSecInfo = secInfoService.getAllTunnelSecInfo();
        List<Parameters> parametersList = parametersService.getListByDate(tableDateStr);

        for (TunnelSecInfo tunnelSecInfo : allTunnelSecInfo) {
            for (Parameters parameters : parametersList) {
                if (tunnelSecInfo.getSid() == parameters.getRoadId()) {
                    positionRecordDataList.add(new PositionRecordData(
                            tunnelSecInfo.getRoad(),
                            tunnelSecInfo.getEnd(),
                            DataParamParseUtil.getRoundValue(parameters.getStream()),
                            0
                    ));
                }
            }
        }
        return positionRecordDataList;
    }

    /**
     * 获取断面实时速度点位。
     *
     * @param timestamp 毫秒时间戳
     * @return 点位速度列表
     */
    @Docking
    public List<PositionRecordData> collectSecSpeedRealTimeData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<PositionRecordData> positionRecordDataList = new ArrayList<>();
        List<TunnelSecInfo> allTunnelSecInfo = secInfoService.getAllTunnelSecInfo();
        List<Parameters> parametersList = parametersService.getListByDate(tableDateStr);

        for (TunnelSecInfo tunnelSecInfo : allTunnelSecInfo) {
            for (Parameters parameters : parametersList) {
                if (tunnelSecInfo.getSid() == parameters.getRoadId()) {
                    positionRecordDataList.add(new PositionRecordData(
                            tunnelSecInfo.getRoad(),
                            0,
                            DataParamParseUtil.getRoundValue(parameters.getSpeed()),
                            0
                    ));
                }
            }
        }
        return positionRecordDataList;
    }

    /**
     * 计算路段平均速度（km/h）。
     */
    private Double getAvgSpeedKmh(Section section, String direction) {
        if (section == null) {
            return null;
        }
        if (direction == null) {
            return section.getAvgVwh() == null ? null : section.getAvgVwh() * 3.6;
        }
        if (direction.contains(DIR_HAMI) || direction.toUpperCase().contains("HAMI")) {
            return section.getAvgVwh() == null ? null : section.getAvgVwh() * 3.6;
        }
        if (direction.contains(DIR_TULUFAN) || direction.toUpperCase().contains("TULUFAN")) {
            return section.getAvgVez() == null ? null : section.getAvgVez() * 3.6;
        }
        return section.getAvgVwh() == null ? null : section.getAvgVwh() * 3.6;
    }

    /**
     * 根据速度映射拥堵状态。
     */
    private String getCongestionStatus(Double speedKmh) {
        if (speedKmh == null) {
            return STATUS_NO_DATA;
        }
        if (speedKmh > 80) {
            return STATUS_SMOOTH;
        }
        if (speedKmh >= 60) {
            return STATUS_SLOW;
        }
        return STATUS_CONGESTED;
    }

    /**
     * 根据拥堵状态获取渲染颜色。
     */
    private String getColorByStatus(String status) {
        if (STATUS_SMOOTH.equals(status)) {
            return "#00B050";
        }
        if (STATUS_SLOW.equals(status)) {
            return "#FFC000";
        }
        if (STATUS_CONGESTED.equals(status)) {
            return "#C00000";
        }
        return "#9E9E9E";
    }

    /**
     * 构建服务区进出车辆统计记录。
     */
    private ServiceAreaVehicleRecord buildServiceAreaVehicleRecord(RoadSegmentStatic serviceArea, Map<Long, List<Traj>> trajListById) {
        Integer startLocation = serviceArea.getStartLocationM();
        if (startLocation == null) {
            return new ServiceAreaVehicleRecord(getServiceAreaLocationName(serviceArea), 0, 0, 0);
        }

        double start = startLocation;
        double end = start + 1000;
        int inCount = 0;
        int outCount = 0;
        Set<Long> currentVehicleSet = new HashSet<>();

        for (Map.Entry<Long, List<Traj>> entry : trajListById.entrySet()) {
            List<Traj> trajList = entry.getValue();
            if (CollectionEmptyUtil.forList(trajList)) {
                continue;
            }

            boolean prevInside = isInSectionRange(trajList.get(0).getFrenetX(), start, end);
            for (int i = 1; i < trajList.size(); i++) {
                boolean currentInside = isInSectionRange(trajList.get(i).getFrenetX(), start, end);
                if (!prevInside && currentInside) {
                    inCount++;
                } else if (prevInside && !currentInside) {
                    outCount++;
                }
                prevInside = currentInside;
            }

            if (prevInside) {
                currentVehicleSet.add(entry.getKey());
            }
        }

        return new ServiceAreaVehicleRecord(
                getServiceAreaLocationName(serviceArea),
                inCount,
                outCount,
                currentVehicleSet.size()
        );
    }

    /**
     * 获取服务区位置名称。
     */
    private String getServiceAreaLocationName(RoadSegmentStatic serviceArea) {
        String segmentType = serviceArea.getSegmentType();
        if (segmentType != null && !segmentType.isBlank()) {
            return segmentType;
        }
        String direction = serviceArea.getDirection() == null ? "" : serviceArea.getDirection();
        return direction + KEYWORD_SERVICE_AREA;
    }

    /**
     * 判断是否为服务区路段。
     */
    private boolean isServiceAreaSegment(RoadSegmentStatic segment) {
        if (segment == null || segment.getSegmentType() == null) {
            return false;
        }
        String segmentType = segment.getSegmentType();
        return segmentType.contains(KEYWORD_SERVICE_AREA) || segmentType.toUpperCase().contains("SERVICE");
    }

    /**
     * 判断轨迹点是否在区间范围内。
     */
    private boolean isInSectionRange(Double frenetX, double start, double end) {
        if (frenetX == null) {
            return false;
        }
        return frenetX >= start && frenetX < end;
    }
}
