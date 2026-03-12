package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.RadarMetric;

import java.util.List;

public interface RadarMetricService extends IService<RadarMetric> {
    public List<RadarMetric> getLatestList(String date);
    public List<RadarMetric> getTargetListByRid(String date, long startTimestamp, long endTimestamp, int rid);
    public List<RadarMetric> getLatestListByRid(String date, int rid);

}
