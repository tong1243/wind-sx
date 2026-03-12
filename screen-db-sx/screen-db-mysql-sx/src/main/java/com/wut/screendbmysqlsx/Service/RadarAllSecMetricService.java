package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.FiberSecMetric;
import com.wut.screendbmysqlsx.Model.RadarAllSecMetric;

import java.util.List;

public interface RadarAllSecMetricService extends IService<RadarAllSecMetric> {

    public List<RadarAllSecMetric> getListByTimestamp(String date, long timestamp);
    public List<RadarAllSecMetric> getLatestList(String date);

    public List<RadarAllSecMetric> getRadarRealTimeTargetList(String date, long startTimestamp, long endTimestamp);
}
