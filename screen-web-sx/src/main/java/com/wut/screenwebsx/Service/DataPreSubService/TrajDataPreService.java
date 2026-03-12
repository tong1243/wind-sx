package com.wut.screenwebsx.Service.DataPreSubService;

import com.wut.screencommonsx.Model.TargetTimeModel;
import com.wut.screencommonsx.Model.TrackDistinctModel;
import com.wut.screencommonsx.Model.TrackRecordModel;
import com.wut.screencommonsx.Response.Traj.TrajFrameData;
import com.wut.screencommonsx.Util.CollectionEmptyUtil;
import com.wut.screencommonsx.Util.DateParamParseUtil;
import com.wut.screencommonsx.Util.MessagePrintUtil;
import com.wut.screendbmysqlsx.Model.Traj;
import com.wut.screendbmysqlsx.Service.TrajService;
import com.wut.screendbmysqlsx.Util.DbModelTransformUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.wut.screencommonsx.Static.WebModuleStatic.ASYNC_SERVICE_TIMEOUT;

@Component
public class TrajDataPreService {
    @Qualifier("webTaskAsyncPool")
    private final Executor webTaskAsyncPool;
    private final TrajService trajService;

    @Autowired
    public TrajDataPreService(Executor webTaskAsyncPool, TrajService trajService) {
        this.webTaskAsyncPool = webTaskAsyncPool;
        this.trajService = trajService;
    }

    public List<TrackDistinctModel> initTrajDistinctList(TargetTimeModel targetTime) {
        List<TrackDistinctModel> distinctList = new ArrayList<>();
        try {
            List<Traj> data = trajService.getDistinctCarIdList(targetTime.getTableDateStr(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp());
            if (!CollectionEmptyUtil.forList(data)) {
                distinctList.addAll(data.stream().map(DbModelTransformUtil::trajToDistinctEntity).toList());
                distinctList.sort(Comparator.comparingLong(TrackDistinctModel::getTrajId));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initTrajDistinctList"); }
        return distinctList;
    }

    public Map<Long, List<Traj>> initTrajTrackDataMap(List<TrackRecordModel> recordList, TargetTimeModel targetTime) {
        Map<Long, List<Traj>> trajDataMap = new HashMap<>();
        List<CompletableFuture<List<Traj>>> trajTaskList = recordList.stream().map(record -> {
            return CompletableFuture.supplyAsync(() -> trajService.getListByTrajId(targetTime.getTableDateStr(), record.getTrajId(), targetTime.getStartTimestamp(), targetTime.getEndTimestamp()), webTaskAsyncPool);
        }).toList();
        try {
            CompletableFuture.allOf(trajTaskList.toArray(CompletableFuture[]::new)).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
            trajTaskList.stream().map(completableFutureTask -> {
                try { return completableFutureTask.get(); }
                catch (Exception e) { return null; }
            }).filter(Objects::nonNull).forEach(trajList -> trajDataMap.put(trajList.get(0).getTrajId(), trajList));
        } catch (Exception e) { MessagePrintUtil.printException(e, "initTrajTrackDataMap"); }
        return trajDataMap;
    }

    public List<Traj> initEventTrackTrajList(TargetTimeModel targetTime, long trajId) {
        List<Traj> trajList = new ArrayList<>();
        try {
            List<Traj> data = trajService.getListByTrajIdAndTime(targetTime.getTableDateStr(), trajId, targetTime.getStartTimestamp(), targetTime.getEndTimestamp());
            if (!CollectionEmptyUtil.forList(data)) {
                trajList.addAll(data);
                trajList.sort(Comparator.comparingLong(Traj::getTimestamp));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initTrajFrameListById"); }
        return trajList;
    }

    public List<Traj> initHistoryTrackTrajList(long timestamp, long trajId) {
        String tableDateStr = DateParamParseUtil.getDateTableStr(timestamp);
        List<Traj> trajList = new ArrayList<>();
        try {
            List<Traj> data = trajService.getListByTrajId(tableDateStr, trajId);
            if (!CollectionEmptyUtil.forList(data)) {
                trajList.addAll(data);
                trajList.sort(Comparator.comparingLong(Traj::getTimestamp));
            }
        } catch (Exception e) { MessagePrintUtil.printException(e, "initTrajDetailListById"); }
        return trajList;
    }

}
