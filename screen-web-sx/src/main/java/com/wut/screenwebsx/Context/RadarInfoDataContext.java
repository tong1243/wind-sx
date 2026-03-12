package com.wut.screenwebsx.Context;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.wut.screendbmysqlsx.Model.RadarInfo;
import com.wut.screendbmysqlsx.Model.Rotation;
import com.wut.screendbmysqlsx.Service.RadarInfoService;
import com.wut.screendbmysqlsx.Service.RotationService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
public class RadarInfoDataContext {
    @Qualifier("webTaskAsyncPool")
    private final Executor webTaskAsyncPool;
    private final RadarInfoService radarInfoService;
    private final RotationService rotationService;

    // 设备RID与IP转换表(状态不代表最新,不能调取这里的雷达状态)
    @Getter
    private final List<RadarInfo> radarInfoList = new ArrayList<>();
    @Getter
    private final Map<Integer, String> radarIPMap = new HashMap<>();
    @Getter
    private final List<Rotation> rotationList = new ArrayList<>();
    // 设备RID与偏移表转换表(用于获取对应的断面号)
    @Getter
    private final Map<String, Rotation> rotationMap = new HashMap<>();

    @Autowired
    public RadarInfoDataContext(Executor webTaskAsyncPool, RadarInfoService radarInfoService, RotationService rotationService) {
        this.webTaskAsyncPool = webTaskAsyncPool;
        this.radarInfoService = radarInfoService;
        this.rotationService = rotationService;
    }

    @PostConstruct
    public void initRadarInfoData() {
        updateRadarInfoData().thenRunAsync(() -> {});
    }

    public CompletableFuture<Void> updateRadarInfoData() {
        return CompletableFuture.runAsync(() -> {
            this.radarInfoList.clear();
            this.radarIPMap.clear();
            this.rotationList.clear();
            this.rotationMap.clear();
            this.radarInfoList.addAll(radarInfoService.getAllRadarInfo());
            this.rotationList.addAll(rotationService.getAllRotation());
            this.radarInfoList.stream().filter(info -> info.getIp() != null).forEach(radarInfo -> {
                this.radarIPMap.put(radarInfo.getRid(), radarInfo.getIp());
            });
            this.rotationList.stream().forEach(rotation -> {
                this.rotationMap.put(rotation.getIp(), rotation);
            });
        }, webTaskAsyncPool);
    }

}
