package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.FiberMetricMapper;
import com.wut.screendbmysqlsx.Model.FiberMetric;
import com.wut.screendbmysqlsx.Service.FiberMetricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_POSTURE_LIMIT;
import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Service
public class FiberMetricServiceImpl extends ServiceImpl<FiberMetricMapper, FiberMetric> implements FiberMetricService {
    private final FiberMetricMapper fiberMetricMapper;

    @Autowired
    public FiberMetricServiceImpl(FiberMetricMapper fiberMetricMapper) {
        this.fiberMetricMapper = fiberMetricMapper;
    }

    @Override
    public List<FiberMetric> getTargetList(String date, long startTimestamp, long endTimestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<FiberMetric> wrapper = new LambdaQueryWrapper<>();
        if (startTimestamp != 0L) { wrapper.ge(FiberMetric::getTimestampStart, startTimestamp); }
        if (endTimestamp != 0L) { wrapper.le(FiberMetric::getTimestampStart, endTimestamp); }
        wrapper.orderByAsc(FiberMetric::getTimestampStart);
        return fiberMetricMapper.selectList(wrapper);
    }

    @Override
    public List<FiberMetric> getLatestList(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<FiberMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(FiberMetric::getTimestampStart);
        wrapper.last("LIMIT " + TABLE_POSTURE_LIMIT);
        return fiberMetricMapper.selectList(wrapper);
    }
}
