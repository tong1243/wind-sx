package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.FiberSecMetric;

import java.util.List;

public interface FiberSecMetricService extends IService<FiberSecMetric> {
    public List<FiberSecMetric> getListByTimestamp(String date, long timestamp);
    public List<FiberSecMetric> getLatestList(String date);

    public List<FiberSecMetric> getTargetListBySec(String date, long startTimestamp, long endTimestamp, double sec);

    public List<FiberSecMetric> getRadarRealTimeTargetList(String date, long startTimestamp, long endTimestamp);
}
