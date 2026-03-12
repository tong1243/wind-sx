package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.FiberSecMetricMapper;
import com.wut.screendbmysqlsx.Model.FiberMetric;
import com.wut.screendbmysqlsx.Model.FiberSecMetric;
import com.wut.screendbmysqlsx.Service.FiberSecMetricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SECTION_LIST_SIZE;
import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Service
public class FiberSecMetricServiceImpl extends ServiceImpl<FiberSecMetricMapper, FiberSecMetric> implements FiberSecMetricService {
    private final FiberSecMetricMapper fiberSecMetricMapper;

    @Autowired
    public FiberSecMetricServiceImpl(FiberSecMetricMapper fiberSecMetricMapper) {
        this.fiberSecMetricMapper = fiberSecMetricMapper;
    }

    @Override
    public List<FiberSecMetric> getListByTimestamp(String date, long timestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<FiberSecMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FiberSecMetric::getTimestampStart, timestamp)
                .orderByAsc(FiberSecMetric::getXsecValue);
        return fiberSecMetricMapper.selectList(wrapper);
    }

    @Override
    public List<FiberSecMetric> getLatestList(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<FiberSecMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(FiberSecMetric::getTimestampStart);
        wrapper.last("LIMIT " + TABLE_SECTION_LIST_SIZE);
        return fiberSecMetricMapper.selectList(wrapper);
    }

    @Override
    public List<FiberSecMetric> getTargetListBySec(String date, long startTimestamp, long endTimestamp, double sec) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<FiberSecMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FiberSecMetric::getXsecValue, sec);
        if (startTimestamp != 0L) { wrapper.ge(FiberSecMetric::getTimestampStart, startTimestamp); }
        if (endTimestamp != 0L) { wrapper.le(FiberSecMetric::getTimestampStart, endTimestamp); }
        wrapper.orderByAsc(FiberSecMetric::getTimestampStart);
        return fiberSecMetricMapper.selectList(wrapper);
    }

    @Override
    public List<FiberSecMetric> getRadarRealTimeTargetList(String date, long startTimestamp, long endTimestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<FiberSecMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(FiberSecMetric::getTimestampStart)
                .ge(FiberSecMetric::getTimestampStart, startTimestamp)
                .le(FiberSecMetric::getTimestampStart, endTimestamp);
        wrapper.last("LIMIT " + TABLE_SECTION_LIST_SIZE);
        return fiberSecMetricMapper.selectList(wrapper);
    }
}
