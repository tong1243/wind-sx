package com.wut.screenwebsx.Service.DataPreSubService;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Response.Section.SectionTimeData;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.MessagePrintUtil;
import com.wut.screendbmysqlsx.Model.SecInfo;
import com.wut.screendbmysqlsx.Model.Section;
import com.wut.screendbmysqlsx.Service.SectionService;
import com.wut.screendbmysqlsx.Util.DbModelTransformUtil;
import com.wut.screenwebsx.Context.SecInfoDataContext;
import com.wut.screenwebsx.Model.SectionIntervalModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class SectionDataPreService {
    private final SecInfoDataContext secInfoDataContext;
    private final SectionService sectionService;

    @Autowired
    public SectionDataPreService(SecInfoDataContext secInfoDataContext, SectionService sectionService) {
        this.secInfoDataContext = secInfoDataContext;
        this.sectionService = sectionService;
    }

    public List<Section> initRealTimeSectionList(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<Section> sectionList = new ArrayList<>();
        try {
            List<Section> data = sectionService.getLatestList(tableDateStr);
            if (!CollectionEmptyUtil.forList(data)) {
                sectionList.addAll(data);
                sectionList.sort(Comparator.comparingDouble(Section::getXsecValue));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initRealTimeSectionList"); }
        return sectionList;
    }

    public List<Section> initPeriodSectionList(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<Section> sectionList = new ArrayList<>();
        try {
            List<Section> data = sectionService.getListByDate(tableDateStr);
            if (!CollectionEmptyUtil.forList(data)) {
                sectionList.addAll(data);
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initPeriodSectionList"); }
        return sectionList;
    }

    public List<Section> initTargetSectionList(TargetTimeModel targetTime) {
        List<Section> sectionList = new ArrayList<>();
        try {
            List<Section> data = sectionService.getListByTarget(targetTime.getTableDateStr(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp());
            if (!CollectionEmptyUtil.forList(data)) {
                sectionList.addAll(data);
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initTargetSectionList"); }
        return sectionList;
    }

    public List<Section> initPeriodSectionListBySec(long timestamp, double sec) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<Section> sectionList = new ArrayList<>();
        try {
            List<Section> data = sectionService.getListByDateAndSec(tableDateStr, sec);
            if (!CollectionEmptyUtil.forList(data)) {
                sectionList.addAll(data);
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initPeriodSectionList"); }
        return sectionList;
    }

    public List<Section> initTargetSectionListBySec(TargetTimeModel targetTime, double sec) {
        List<Section> sectionList = new ArrayList<>();
        try {
            List<Section> data = sectionService.getListByTargetAndSec(targetTime.getTableDateStr(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp(), sec);
            if (!CollectionEmptyUtil.forList(data)) {
                sectionList.addAll(data);
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initTargetSectionListBySec"); }
        return sectionList;
    }

    public List<SectionTimeData> initSectionTimeDataList() {
        List<SectionIntervalModel> sectionIntervalList = secInfoDataContext.getSecIntervalList();
        return sectionIntervalList.stream().map(interval -> {
            return new SectionTimeData(
                    interval.getXsecName(),
                    interval.getXsecValue(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        }).toList();
    }

    public List<Section> initRadarRealTimeTargetList(TargetTimeModel targetTime) {
        List<Section> sectionList = new ArrayList<>();
        try {
            List<Section> data = sectionService.getRadarRealTimeTargetList(targetTime.getTableDateStr(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp());
            if (!CollectionEmptyUtil.forList(data)) {
                sectionList.addAll(data);
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initRadarRealTimeTargetList"); }
        return sectionList;
    }

}
