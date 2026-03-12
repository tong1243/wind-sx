package com.wut.screenwebsx.Service.DataPreSubService;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Request.EventTrackReq;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.MessagePrintUtil;
import com.wut.screendbmysqlsx.Model.CarEvent;
import com.wut.screendbmysqlsx.Model.Traj;
import com.wut.screendbmysqlsx.Service.CarEventService;
import com.wut.screendbmysqlsx.Service.TrajService;
import com.wut.screenwebsx.Model.EventTrackModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class EventDataPreService {
    private final CarEventService carEventService;
    private final TrajService trajService;

    @Autowired
    public EventDataPreService(CarEventService carEventService, TrajService trajService) {
        this.carEventService = carEventService;
        this.trajService = trajService;
    }

    public List<CarEvent> initCarEventData(long timestamp) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<CarEvent> carEventList = new ArrayList<>();
        try {
            List<CarEvent> data = carEventService.getListByDate(tableDateStr);
            if (!CollectionEmptyUtil.forList(data)) {
                carEventList.addAll(data);
                carEventList.sort(Comparator.comparingDouble(CarEvent::getStartTimestamp));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initCarEventData"); }
        return carEventList;
    }

    public EventTrackModel initEventTrackModel(EventTrackReq req){
        String tableDateStr = DateParamParseUtil.getDateTableStr(req.getTimestamp());
        CarEvent carEvent = null;
        List<Traj> trajList = new ArrayList<>();
        try {
            carEvent = carEventService.getOneByUuid(tableDateStr, Long.parseLong(req.getUuid()));
            Optional.ofNullable(carEvent).ifPresent(event -> trajList.addAll(initEventTrackTrajList(tableDateStr, event)));
        } catch (Exception e) { MessagePrintUtil.printException(e, "initEventTrackModel"); }
        return new EventTrackModel(carEvent, trajList);
    }

    public List<Traj> initEventTrackTrajList(String date, CarEvent event) {
        List<Traj> trajList = new ArrayList<>();
        try {
            List<Traj> data = trajService.getListByEventInterval(date, event.getTrajId(), event.getStartTimestamp(), event.getEndTimestamp());
            if (!CollectionEmptyUtil.forList(data)) {
                trajList.addAll(data);
                trajList.sort(Comparator.comparingDouble(Traj::getTimestamp));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initEventTrackTrajList"); }
        return trajList;
    }

    public List<CarEvent> initCarEventTargetData(TargetTimeModel targetTime) {
        List<CarEvent> eventList = new ArrayList<>();
        try {
            List<CarEvent> data = carEventService.getListByTarget(targetTime.getTableDateStr(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp());
            if (!CollectionEmptyUtil.forList(data)) {
                eventList.addAll(data);
                eventList.sort(Comparator.comparingDouble(CarEvent::getStartTimestamp));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initCarEventTargetData"); }
        return eventList;
    }

}
