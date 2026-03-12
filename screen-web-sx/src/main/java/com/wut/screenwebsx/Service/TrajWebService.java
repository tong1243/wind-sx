package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Model.TrackDistinctModel;
import com.wut.screencommonsx.Model.TrackRecordModel;
import com.wut.screencommonsx.Request.EventTrackFrameReq;
import com.wut.screencommonsx.Request.EventTrackReq;
import com.wut.screencommonsx.Request.HistoryTrackFrameReq;
import com.wut.screencommonsx.Request.TrajTrackReq;
import com.wut.screencommonsx.Response.Track.TrackFrameDataResp;
import com.wut.screencommonsx.Response.Track.TrajTrackDataResp;
import com.wut.screencommonsx.Response.Track.TrajTrackInfoData;
import com.wut.screencommonsx.Response.Traj.TrajDetailDataResp;
import com.wut.screencommonsx.Response.Traj.TrajFrameData;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screendbmysqlsx.Model.CarEvent;
import com.wut.screendbmysqlsx.Model.Traj;
import com.wut.screendbmysqlsx.Util.DbModelTransformUtil;
import com.wut.screenwebsx.Config.DockingInterfaceConfig.Docking;
import com.wut.screenwebsx.Model.EventTrackModel;
import com.wut.screenwebsx.Service.DataPreSubService.EventDataPreService;
import com.wut.screenwebsx.Service.DataPreSubService.TrajDataPreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TrajWebService {
    private final TrajDataPreService trajDataPreService;
    private final EventDataPreService eventDataPreService;

    @Autowired
    public TrajWebService(TrajDataPreService trajDataPreService, EventDataPreService eventDataPreService) {
        this.trajDataPreService = trajDataPreService;
        this.eventDataPreService = eventDataPreService;
    }

    @Docking
    public TrajTrackDataResp collectTrajTrackData(TrajTrackReq req) {
        TargetTimeModel targetTime = DateParamParseUtil.getTargetDataTime(ModelTransformUtil.trajTrackReqToTargetData(req));
        List<TrackDistinctModel> distinctList = trajDataPreService.initTrajDistinctList(targetTime);
        List<TrackRecordModel> matchRecordList = recordMatchTrajId(distinctList, req.getLicense());
        List<TrajTrackInfoData> trackInfoList = new ArrayList<>();
        if (CollectionEmptyUtil.forList(matchRecordList)) { return null; }
        Map<Long, List<Traj>> trajDataMap = trajDataPreService.initTrajTrackDataMap(matchRecordList, targetTime);
        matchRecordList.stream().forEach(record -> {
            List<Traj> trajList = trajDataMap.get(record.getTrajId());
            TrajTrackInfoData trackInfoData = DbModelTransformUtil.trajAndMatchRecordToTrackInfoData(record, trajList.get(0), trajList.get(trajList.size() - 1));
            trackInfoList.add(trackInfoData);
        });
        return new TrajTrackDataResp(trackInfoList);
    }

    @Docking
    public TrajDetailDataResp collectTrackFrameData(EventTrackFrameReq req) {
        EventTrackModel eventTrackModel = eventDataPreService.initEventTrackModel(new EventTrackReq(req.getTimestamp(), req.getEventId()));
        List<Traj> trajList = eventTrackModel.getTrajList();
        if (CollectionEmptyUtil.forList(trajList)) { return null; }
        TrajDetailDataResp detailData = DbModelTransformUtil.trajToDetailData(trajList.get(0), trajList.get(trajList.size() - 1));
        detailData.getFrameList().addAll(trajList.stream().map(DbModelTransformUtil::trajToFrameData).toList());
        return detailData;
    }

    @Docking
    public TrajDetailDataResp collectTrajDetailData(HistoryTrackFrameReq req) {
        List<Traj> trajList = trajDataPreService.initHistoryTrackTrajList(req.getTimestamp(), req.getTrajId());
        if (CollectionEmptyUtil.forList(trajList)) { return null; }
        TrajDetailDataResp detailData = DbModelTransformUtil.trajToDetailData(trajList.get(0), trajList.get(trajList.size() - 1));
        detailData.getFrameList().addAll(trajList.stream().map(DbModelTransformUtil::trajToFrameData).toList());
        return detailData;
    }

    public List<TrackRecordModel> recordMatchTrajId(List<TrackDistinctModel> distinctList, String license) {
        List<TrackRecordModel> recordList = new ArrayList<>();
        if (CollectionEmptyUtil.forList(distinctList)) { return recordList; }
        // 提取与输入车牌号右匹配的所有<轨迹号, 车牌号>
        // 每个有记录的轨迹号对应的匹配车牌号数量只可能是1(未绑定车牌)或2(已绑定车牌)
        Map<Long, List<String>> trajMatchMap = new HashMap<>();

        // 2025.01.02
        // 分设备取车辆轨迹时只会出现假牌照,不需要再进行该筛选流程
        distinctList.stream().filter(entity -> entity.getCarId().matches("^" + license + ".*")).forEach(entity -> {
            trajMatchMap.put(entity.getTrajId(), new ArrayList<>(List.of(entity.getCarId())));
//            if (!trajMatchMap.containsKey(entity.getTrajId())) {
//                trajMatchMap.put(entity.getTrajId(), new ArrayList<>(List.of(entity.getCarId())));
//            } else {
//                trajMatchMap.get(entity.getTrajId()).add(entity.getCarId());
//            }
        });
        if (CollectionEmptyUtil.forMap(trajMatchMap)) { return recordList; }
        // 提取每个有记录的轨迹号对应的所有车牌号
        // 对每个有记录的轨迹号,共维护两个列表: (1)轨迹号所有车牌号中右匹配的集合 (2)轨迹号所有车牌号的集合
        trajMatchMap.entrySet().stream().forEach(entry -> {
//            List<String> licenseList = distinctList.stream()
//                    .filter(entity -> Objects.equals(entry.getKey(), entity.getTrajId()))
//                    .map(TrackDistinctModel::getCarId)
//                    .toList();
//            recordList.add(filterMatchLicense(entry.getKey(), entry.getValue(), licenseList));
            recordList.add(new TrackRecordModel(entry.getKey(), entry.getValue().get(0), entry.getValue().get(0)));
        });
        return recordList;
    }

    public TrackRecordModel filterMatchLicense(long trajId, List<String> matchList, List<String> licenseList) {
        TrackRecordModel record = new TrackRecordModel(trajId, null, null);
        // 如果只包含单一匹配项,取该匹配项作为匹配牌照号/车辆牌照号
        // 如果包含多个匹配项,优先取非默认牌照项(该牌照必然存在)作为匹配牌照号/车辆牌照号
        (matchList.size() < 2 ? Optional.of(matchList.get(0))
                : matchList.stream().filter(str -> !str.contains("*")).findAny())
                .ifPresent(record::setMatchName);
        (licenseList.size() < 2 ? Optional.of(licenseList.get(0))
                : licenseList.stream().filter(str -> !str.contains("*")).findAny())
                .ifPresent(record::setFinalName);
        return record;
    }

}
