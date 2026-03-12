package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Request.EventProcessReq;
import com.wut.screencommonsx.Request.EventTrackReq;
import com.wut.screencommonsx.Request.TargetDataReq;
import com.wut.screencommonsx.Response.Event.*;
import com.wut.screencommonsx.Response.Track.EventTrackDataResp;
import com.wut.screencommonsx.Response.Track.EventTrackInfoData;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DataParamParseUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.ModelTransformUtil;
import com.wut.screendbmysqlsx.Model.CarEvent;
import com.wut.screendbmysqlsx.Model.Laneline;
import com.wut.screendbmysqlsx.Model.Traj;
import com.wut.screendbmysqlsx.Model.TunnelSecInfo;
import com.wut.screendbmysqlsx.Service.CarEventService;
import com.wut.screendbmysqlsx.Service.LanelineService;
import com.wut.screendbmysqlsx.Service.SecInfoService;
import com.wut.screendbmysqlsx.Service.TrajService;
import com.wut.screendbmysqlsx.Util.DbModelTransformUtil;
import com.wut.screenwebsx.Config.DockingInterfaceConfig.Docking;
import com.wut.screenwebsx.Context.SecInfoDataContext;
import com.wut.screenwebsx.Model.EventTrackModel;
import com.wut.screenwebsx.Service.DataPreSubService.EventDataPreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.wut.screencommonsx.Static.WebModuleStatic.*;

@Component
public class EventWebService {
    private static CarEventService carEventService;
    private final SecInfoDataContext secInfoDataContext;
    private final EventDataPreService eventDataPreService;
    private final TrajService trajService;
    private static SecInfoService secInfoService;
    private  final LanelineService lanelineService;

    @Autowired
    public EventWebService(CarEventService carEventService, SecInfoDataContext secInfoDataContext, EventDataPreService eventDataPreService, TrajService trajService, SecInfoService secInfoService, LanelineService lanelineService) {
        this.carEventService = carEventService;
        this.secInfoDataContext = secInfoDataContext;
        this.eventDataPreService = eventDataPreService;
        this.trajService = trajService;
        this.secInfoService = secInfoService;
        this.lanelineService = lanelineService;
    }

    @Docking
    public EventDataResp collectEventData(long timestamp) {
        List<CarEvent> carEventList = eventDataPreService.initCarEventData(timestamp);
        List<EventRoadRecordData> roadRecordList = initRoadRecordList();
        EventStatisticData statisticData = ModelTransformUtil.getEventStatisticInstance();
        List<EventInfoData> infoDataList = new ArrayList<>();
        if (!CollectionEmptyUtil.forList(carEventList)) {
            carEventList.stream().forEach(carEvent -> {
                recordEventToRoad(roadRecordList, carEvent);
                recordEventToStatistic(statisticData, carEvent);
                if (carEvent.getStatus() != EVENT_STATUS_FINISHED) {
                    infoDataList.add(DbModelTransformUtil.eventToInfoData(carEvent));
                }
            });
        }
        return new EventDataResp(statisticData, roadRecordList, infoDataList);
    }

    @Docking
    public EventTrackDataResp collectEventTrackData(EventTrackReq req) {
        EventTrackModel eventTrackModel = eventDataPreService.initEventTrackModel(req);
        CarEvent event = eventTrackModel.getEvent();
        List<Traj> trajList = eventTrackModel.getTrajList();
        if (event == null || CollectionEmptyUtil.forList(trajList)) {
            return null;
        }
        EventInfoData eventInfo = DbModelTransformUtil.eventToInfoData(event);
        List<EventTrackInfoData> trackInfoList = new ArrayList<>();
        trajList.stream().filter(traj -> Objects.equals(traj.getTimestamp(), event.getEndTimestamp()))
                .findAny().ifPresent((eventTrackInfo) -> trackInfoList.add(DbModelTransformUtil.trajToTrackInfoData(eventTrackInfo)));
        return new EventTrackDataResp(eventInfo, trackInfoList);
    }

    @Docking
    public EventDataResp collectEventTargetData(TargetDataReq req) {
        TargetTimeModel targetTime = DateParamParseUtil.getTargetDataTime(req);
        List<CarEvent> eventList = eventDataPreService.initCarEventTargetData(targetTime);
        List<EventRoadRecordData> roadRecordList = initRoadRecordList();
        EventStatisticData statisticData = ModelTransformUtil.getEventStatisticInstance();
        if (CollectionEmptyUtil.forList(eventList)) {
            return null;
        }
        eventList.stream().forEach(carEvent -> {
            recordEventToRoad(roadRecordList, carEvent);
            recordEventToStatistic(statisticData, carEvent);
        });
        return new EventDataResp(statisticData, roadRecordList, List.of(new EventInfoData()));
    }

    @Docking
    public boolean makeEventProcess(EventProcessReq req) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(req.getTimestamp());
        CarEvent event = carEventService.getOneByUuid(tableDateStr, Long.parseLong(req.getUuid()));
        // 更新事件状态的任务,仅当事件状态已经是已完成时,向客户端报任务失败消息
        if (event.getStatus() == EVENT_STATUS_FINISHED) {
            return false;
        }
        event.setStatus(req.getStatus());
        event.setProcess(req.getProcess());
        carEventService.updateOneByUuid(tableDateStr, event);
        return true;
    }

    public List<EventRoadRecordData> initRoadRecordList() {
        List<TunnelSecInfo> allTunnelSecInfo = secInfoService.getAllTunnelSecInfo();
        return allTunnelSecInfo.stream().map(interval -> {
            return new EventRoadRecordData(
                    interval.getRoad(),
                    interval.getStart(),
                    interval.getEnd(),
                    0,
                    0
            );
        }).toList();
    }

    public void recordEventToRoad(List<EventRoadRecordData> roadRecordList, CarEvent carEvent) {
        roadRecordList.stream().filter(record -> {
            double mileage = Double.parseDouble(carEvent.getStartMileage());
            return mileage >= record.roadStart && mileage <= record.roadEnd;
        }).findFirst().ifPresent(record -> {
            record.setValueToWH(record.getValueToWH() + 1);
        });
    }

    public void recordEventToStatistic(EventStatisticData eventStatisticData, CarEvent carEvent) {
        eventStatisticData.setTotal(eventStatisticData.getTotal() + 1);
        if (carEvent.getStatus() == EVENT_STATUS_PENDING) {
            eventStatisticData.setPending(eventStatisticData.getPending() + 1);
        }
        switch (carEvent.getEventType()) {
            case EVENT_TYPE_PARKING -> eventStatisticData.setParking(eventStatisticData.getParking() + 1);
            case EVENT_TYPE_AGAINST -> eventStatisticData.setAgainst(eventStatisticData.getAgainst() + 1);
            case EVENT_TYPE_FAST -> eventStatisticData.setFast(eventStatisticData.getFast() + 1);
            case EVENT_TYPE_SLOW -> eventStatisticData.setSlow(eventStatisticData.getSlow() + 1);
        }
    }

    public static EventInfoDataResp collectEventInfoData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<TunnelSecInfo> allTunnelSecInfo = secInfoService.getAllTunnelSecInfo();
        List<CarEvent> data = carEventService.getListByDate(tableDateStr);
        // 取最早的一条事件数据，且该事件没有结束
        CarEvent carEvent = data.stream().filter(event -> event.getEndTimestamp() + 5000 > timestamp && event.getEventType() == 2)
                .min(Comparator.comparingDouble(CarEvent::getStartTimestamp)).orElse(null);
        if (carEvent == null) {
            return null;
        }
        EventInfoDataResp eventInfoDataResp = null;
        for (TunnelSecInfo tunnelSecInfo : allTunnelSecInfo) {
            int distance = (int) Double.parseDouble(carEvent.getStartMileage());
            int distanceOutTunnel = 0;
            int distanceInTunnel = 0;
            if (distance >= tunnelSecInfo.getStart() && distance <= tunnelSecInfo.getEnd()) {
                if (tunnelSecInfo.getSid()==3||tunnelSecInfo.getSid()==4){
                    distanceOutTunnel = 2975 - distance;
                    distanceInTunnel = distance - 200;
                }
                if (tunnelSecInfo.getSid()==7){
                    distanceOutTunnel = 5249 - distance;
                    distanceInTunnel = distance - 2585;
                }
                eventInfoDataResp = new EventInfoDataResp();
                eventInfoDataResp.setLicense(carEvent.getId());
                eventInfoDataResp.setFrenetX(distance);
                eventInfoDataResp.setLane(carEvent.getLane());
                eventInfoDataResp.setPosition(DataParamParseUtil.getPositionStr(Double.parseDouble(carEvent.getStartMileage())));
                eventInfoDataResp.setStatus(carEvent.getStatus());
                eventInfoDataResp.setType(carEvent.getEventType());
                eventInfoDataResp.setDuring((int) (carEvent.getEndTimestamp() - carEvent.getStartTimestamp()) / 1000);
                eventInfoDataResp.setTime(DateParamParseUtil.getDateTimePickerStr(carEvent.getStartTimestamp()));
                eventInfoDataResp.setDistanceOutTunnel(distanceOutTunnel);
                eventInfoDataResp.setDistanceInTunnel(distanceInTunnel);
                eventInfoDataResp.setAccidentLevel("潜在事故");
                eventInfoDataResp.setQueueLength(carEvent.getQueueLength());
                eventInfoDataResp.setRoad(tunnelSecInfo.getRoad());
            }
        }
        return eventInfoDataResp;
    }

    public List<EventCountDataResp> collectEventCountData(long timestamp) {
        List<CarEvent> listByTarget = carEventService.getListByTarget(
                DateParamParseUtil.getDateTableStr(timestamp),
                timestamp - 60000,
                timestamp
        );

        List<EventCountDataResp> eventCountDataRespList = new ArrayList<>();
        List<TunnelSecInfo> allTunnelSecInfo = secInfoService.getAllTunnelSecInfo();

        for (TunnelSecInfo tunnelSecInfo : allTunnelSecInfo) {
            if (tunnelSecInfo.getSid() == 3 || tunnelSecInfo.getSid() == 4 || tunnelSecInfo.getSid() == 7) {
                EventCountDataResp eventCountDataResp = new EventCountDataResp();
                eventCountDataResp.setTimestamp(timestamp);
                eventCountDataResp.setSId(tunnelSecInfo.getSid());

                // 修复：将字符串转换为数值类型
                long count = listByTarget.stream()
                        .filter(carEvent -> {
                            double startMileage = Double.parseDouble(carEvent.getStartMileage());
                            return startMileage <= tunnelSecInfo.getEnd()
                                    && startMileage >= tunnelSecInfo.getStart()
                                    && carEvent.getEventType() == 2;
                        })
                        .count();

                eventCountDataResp.setCount((int) count);
                eventCountDataRespList.add(eventCountDataResp);
            }
        }

        return eventCountDataRespList;
    }
    public List<LanelineResp> collectLanelineData(long timestamp){
        EventInfoDataResp eventInfoData = collectEventInfoData(timestamp);
        if (eventInfoData == null){
            return new ArrayList<>();
        }
        List<Laneline> listByFrenetXAndLane = lanelineService.getListByFrenetXAndLane(eventInfoData.getFrenetX(), eventInfoData.getLane());
        List<LanelineResp> lanelineRespList = listByFrenetXAndLane.stream().map(laneline -> {
            return new LanelineResp(
                    laneline.getFrenetX(),
                    laneline.getLongitude(),
                    laneline.getLatitude(),
                    laneline.getLane(),
                    laneline.getHeight()
            );
        }).toList();
        return lanelineRespList;
    }

}
