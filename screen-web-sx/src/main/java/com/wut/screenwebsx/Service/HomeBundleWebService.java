package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Response.Device.DeviceDataResp;
import com.wut.screencommonsx.Response.Event.EventDataResp;
import com.wut.screencommonsx.Response.HomeBundleDataResp;
import com.wut.screencommonsx.Response.PositionRecordData;
import com.wut.screencommonsx.Response.Posture.PostureFlowTypeData;
import com.wut.screencommonsx.Response.TimeRecordData;
import com.wut.screencommonsx.Util.MessagePrintUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.wut.screencommonsx.Static.WebModuleStatic.ASYNC_SERVICE_TIMEOUT;

@Component
public class HomeBundleWebService {
    @Qualifier("webTaskAsyncPool")
    private final Executor webTaskAsyncPool;
    private final DeviceWebService deviceWebService;
    private final EventWebService eventWebService;
    private final PostureWebService postureWebService;
    private final SectionWebService sectionWebService;

    @Autowired
    public HomeBundleWebService(Executor webTaskAsyncPool, DeviceWebService deviceWebService, EventWebService eventWebService, PostureWebService postureWebService, SectionWebService sectionWebService) {
        this.webTaskAsyncPool = webTaskAsyncPool;
        this.deviceWebService = deviceWebService;
        this.eventWebService = eventWebService;
        this.postureWebService = postureWebService;
        this.sectionWebService = sectionWebService;
    }

    public HomeBundleDataResp collectHomeBundleData(long timestamp) {
        try {
            var devicePartDataTask = collectDevicePartData(timestamp);
            var eventPartDataTask = collectEventPartData(timestamp);
            var postureRealTimePartDataTask = collectPostureRealTimePartData(timestamp);
            var posturePeriodPartDataTask = collectPosturePeriodPartData(timestamp);
            var secStreamRealTimePartDataTask = collectSecStreamRealTimePartData(timestamp);
            var secSpeedRealTimePartDataTask = collectSecSpeedRealTimePartData(timestamp);
            CompletableFuture.allOf(
                    devicePartDataTask,
                    eventPartDataTask,
                    posturePeriodPartDataTask,
                    postureRealTimePartDataTask,
                    secStreamRealTimePartDataTask,
                    secSpeedRealTimePartDataTask
            ).get(ASYNC_SERVICE_TIMEOUT, TimeUnit.SECONDS);
            return new HomeBundleDataResp(
                    devicePartDataTask.get().getStatisticData(),
                    eventPartDataTask.get().getStatisticData(),
                    eventPartDataTask.get().getRoadRecordList(),
                    postureRealTimePartDataTask.get(),
                    posturePeriodPartDataTask.get(),
                    secStreamRealTimePartDataTask.get(),
                    secSpeedRealTimePartDataTask.get()
            );
        } catch (Exception e) { MessagePrintUtil.printException(e, "collectHomeBundleData"); }
        return null;
    }

    public CompletableFuture<DeviceDataResp> collectDevicePartData(long timestamp) {
        return CompletableFuture.supplyAsync(() -> {
            return deviceWebService.collectDeviceInfoData(timestamp);
        }, webTaskAsyncPool);
    }

    public CompletableFuture<EventDataResp> collectEventPartData(long timestamp) {
        return CompletableFuture.supplyAsync(() -> {
            return eventWebService.collectEventData(timestamp);
        }, webTaskAsyncPool);
    }

    public CompletableFuture<List<PostureFlowTypeData>> collectPostureRealTimePartData(long timestamp) {
        return CompletableFuture.supplyAsync(() -> {
            return postureWebService.collectPostureRealTimeData(timestamp).getFlowTypeList();
        }, webTaskAsyncPool);
    }

    public CompletableFuture<List<TimeRecordData>> collectPosturePeriodPartData(long timestamp) {
        return CompletableFuture.supplyAsync(() -> {
            return postureWebService.collectAvgSpeed(timestamp);
        }, webTaskAsyncPool);
    }

    public CompletableFuture<List<PositionRecordData>> collectSecStreamRealTimePartData(long timestamp) {
        return CompletableFuture.supplyAsync(() -> {
            return sectionWebService.collectSecStreamRealTimeData(timestamp);
        }, webTaskAsyncPool);
    }
    public CompletableFuture<List<PositionRecordData>> collectSecSpeedRealTimePartData(long timestamp) {
        return CompletableFuture.supplyAsync(() -> {
            return sectionWebService.collectSecSpeedRealTimeData(timestamp);
        }, webTaskAsyncPool);
    }
}
