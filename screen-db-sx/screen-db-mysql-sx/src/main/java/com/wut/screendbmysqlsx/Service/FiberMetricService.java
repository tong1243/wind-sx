package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.FiberMetric;

import java.util.List;

public interface FiberMetricService extends IService<FiberMetric> {
    public List<FiberMetric> getTargetList(String date, long startTimestamp, long endTimestamp);

    public List<FiberMetric> getLatestList(String date);
}
