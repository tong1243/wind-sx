package com.wut.screenwebsx.Context;

import com.google.common.collect.Range;
import com.wut.screendbmysqlsx.Model.SecInfo;
import com.wut.screendbmysqlsx.Service.SecInfoService;
import com.wut.screenwebsx.Model.SectionIntervalModel;
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
public class SecInfoDataContext {
    @Qualifier("webTaskAsyncPool")
    private final Executor webTaskAsyncPool;
    private final SecInfoService secInfoService;

    // 旧断面信息列表
    @Getter
    private final List<SecInfo> secInfoList = new ArrayList<>();

    // 新断面信息列表
    @Getter
    private final List<SectionIntervalModel> secIntervalList = new ArrayList<>();
    @Getter
    private final Map<Integer, SectionIntervalModel> secIntervalMap = new HashMap<>();

    @Autowired
    public SecInfoDataContext(Executor webTaskAsyncPool, SecInfoService secInfoService) {
        this.webTaskAsyncPool = webTaskAsyncPool;
        this.secInfoService = secInfoService;
    }

    @PostConstruct
    public void initSecInfoList() {
        updateSecInfoList().thenRunAsync(() -> {});
    }

    public CompletableFuture<Void> updateSecInfoList() {
        return CompletableFuture.runAsync(() -> {
            secInfoList.clear();
            secIntervalList.clear();
            secIntervalMap.clear();
            secInfoList.addAll(secInfoService.getAllSecInfo());
            for (int i = 1; i < secInfoList.size(); i++) {
                SecInfo prev = secInfoList.get(i - 1);
                SecInfo curr = secInfoList.get(i);
                // 路段区间取桩号更大的作为基准,拼接首尾两个断面的桩号名,建立两个桩号之间的闭区间
                SectionIntervalModel model = new SectionIntervalModel(
                        curr.getSid(),
                        prev.getXsecName()+"-"+curr.getXsecName(),
                        curr.getXsecValue(),
                        Range.closed(prev.getXsecValue(), curr.getXsecValue())
                );
                secIntervalList.add(model);
                secIntervalMap.put(curr.getSid(), model);
            }
        }, webTaskAsyncPool);
    }

}
