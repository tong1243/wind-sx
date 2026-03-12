package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Response.PositionRecordData;
import com.wut.screencommonsx.Response.Section.*;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DataParamParseUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screendbmysqlsx.Model.Parameters;
import com.wut.screendbmysqlsx.Model.Section;
import com.wut.screendbmysqlsx.Model.TunnelSecInfo;
import com.wut.screendbmysqlsx.Service.ParametersService;
import com.wut.screendbmysqlsx.Service.PostureService;
import com.wut.screendbmysqlsx.Service.SecInfoService;
import com.wut.screendbmysqlsx.Util.DbModelTransformUtil;
import com.wut.screenwebsx.Config.DockingInterfaceConfig.Docking;
import com.wut.screenwebsx.Context.SecInfoDataContext;
import com.wut.screenwebsx.Model.SectionIntervalModel;
import com.wut.screenwebsx.Service.DataPreSubService.SectionDataPreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SectionWebService {
    private final SecInfoDataContext secInfoDataContext;
    private final SectionDataPreService sectionDataPreService;
    private final SecInfoService secInfoService;
    private final ParametersService parametersService;
    private final PostureWebService postureWebService;

    @Autowired
    public SectionWebService(SecInfoDataContext secInfoDataContext, SectionDataPreService sectionDataPreService, SecInfoService secInfoService, ParametersService parametersService, PostureWebService postureWebService) {
        this.secInfoDataContext = secInfoDataContext;
        this.sectionDataPreService = sectionDataPreService;
        this.secInfoService = secInfoService;
        this.parametersService = parametersService;
        this.postureWebService = postureWebService;
    }

    @Docking
    public SectionRealTimeDataResp collectSectionRealTimeData(long timestamp) {
        SectionRealTimeDataResp resp = ModelTransformUtil.getSectionRealTimeDataRespInstance();
        resp.setFlowRecordList(collectSecStreamRealTimeData(timestamp));
        resp.setSpeedRecordList(collectSecSpeedRealTimeData(timestamp));
        return resp;
    }

    @Docking
    public SectionPeriodDataResp collectSectionPeriodData(long timestamp) {
        List<TunnelSecInfo> allTunnelSecInfo = secInfoService.getAllTunnelSecInfo();
        List<SectionTimeData> timeDataList = new ArrayList<>();
        for (TunnelSecInfo tunnelSecInfo : allTunnelSecInfo){
            SectionTimeData timeData = new SectionTimeData();
            timeData.setXsecName(tunnelSecInfo.getRoad());
            timeData.setXsecValue(tunnelSecInfo.getEnd());
            timeData.setSpeedRecordList(postureWebService.collectAvgSpeed(timestamp));
            timeData.setFlowRecordList(postureWebService.collectAvgStream(timestamp));
            timeData.setCongestionRecordList(postureWebService.getCongestionData(tunnelSecInfo.getSid(),timestamp));
            timeDataList.add(timeData);
        }
        return new SectionPeriodDataResp(timeDataList);
    }

    @Docking
    public SectionPeriodDataResp collectSectionTargetData(TargetDataReq req) {
        TargetTimeModel targetTime = DateParamParseUtil.getTargetDataTime(req);
        List<Section> sectionList = sectionDataPreService.initTargetSectionList(targetTime);
        List<SectionTimeData> timeDataList = sectionDataPreService.initSectionTimeDataList();
        if (CollectionEmptyUtil.forList(sectionList)) { return null; }
        recordSectionToTimeRecordList(sectionList, timeDataList);
        return new SectionPeriodDataResp(timeDataList);
    }

    @Docking
    public SecInfoDataResp collectSecInfoData() {
        List<TunnelSecInfo> allTunnelSecInfo = secInfoService.getAllTunnelSecInfo();
        List<SecInfoData> secInfoDataList = new ArrayList<>();
        for (TunnelSecInfo tunnelSecInfo : allTunnelSecInfo){
            secInfoDataList.add(new SecInfoData(tunnelSecInfo.getRoad(), tunnelSecInfo.getEnd()));
        }
        return new SecInfoDataResp(secInfoDataList);
    }

    public void recordSectionToTimeRecordList(List<Section> sectionList, List<SectionTimeData> timeDataList) {
        List<Section> sortedSectionList = sectionList.stream().sorted(Comparator.comparing(Section::getTimestampStart).thenComparing(Section::getXsecValue)).toList();
        timeDataList.stream().forEach(timeData -> sortedSectionList.stream()
                .filter(section -> section.getXsecValue() == timeData.getXsecValue())
                .sorted(Comparator.comparingLong(Section::getTimestampStart))
                .forEach(section -> {
                    timeData.getFlowRecordList().add(DbModelTransformUtil.sectionToFlowTimeRecord(section));
                    timeData.getSpeedRecordList().add(DbModelTransformUtil.sectionToSpeedTimeRecord(section));
                    timeData.getCongestionRecordList().add(DbModelTransformUtil.sectionToCongestionTimeRecord(section));
        }));
    }
    @Docking
    public List<PositionRecordData> collectSecStreamRealTimeData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<PositionRecordData> positionRecordDataList = new ArrayList<>();
        List<TunnelSecInfo> allTunnelSecInfo = secInfoService.getAllTunnelSecInfo();
        List<Parameters> parametersList = parametersService.getListByDate(tableDateStr);
        for (TunnelSecInfo tunnelSecInfo : allTunnelSecInfo){
            for (Parameters parameters : parametersList){
                if (tunnelSecInfo.getSid()==parameters.getRoadId()){
                    positionRecordDataList.add(new PositionRecordData(tunnelSecInfo.getRoad(), tunnelSecInfo.getEnd(), DataParamParseUtil.getRoundValue( parameters.getStream()), 0));
                }
            }
        }
        return positionRecordDataList;
    }
    @Docking
    public List<PositionRecordData> collectSecSpeedRealTimeData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<PositionRecordData> positionRecordDataList = new ArrayList<>();
        List<TunnelSecInfo> allTunnelSecInfo = secInfoService.getAllTunnelSecInfo();
        List<Parameters> parametersList = parametersService.getListByDate(tableDateStr);
        for (TunnelSecInfo tunnelSecInfo : allTunnelSecInfo){
            for (Parameters parameters : parametersList){
                if (tunnelSecInfo.getSid()==parameters.getRoadId()){
                    positionRecordDataList.add(new PositionRecordData(tunnelSecInfo.getRoad(), 0, DataParamParseUtil.getRoundValue( parameters.getSpeed()), 0));
                }
            }
        }
        return positionRecordDataList;
    }
}
