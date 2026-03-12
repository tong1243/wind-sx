package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.RadarSecMetric;

import java.util.List;

public interface RadarSecMetricService extends IService<RadarSecMetric> {
    public List<RadarSecMetric> getListByTimestampAndRid(String date, long timestamp, int rid);
    public List<RadarSecMetric> getLatestListByRid(String date, int rid);

    public List<RadarSecMetric> getTargetListByRid(String date, long startTimestamp, long endTimestamp, int rid);

}
