package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.RadarMetricMapper;
import com.wut.screendbmysqlsx.Model.RadarMetric;
import com.wut.screendbmysqlsx.Service.RadarMetricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommonsx.Static.DbModuleStatic.*;

@Service
public class RadarMetricServiceImpl extends ServiceImpl<RadarMetricMapper, RadarMetric> implements RadarMetricService {
    private final RadarMetricMapper radarMetricMapper;

    @Autowired
    public RadarMetricServiceImpl(RadarMetricMapper radarMetricMapper) {
        this.radarMetricMapper = radarMetricMapper;
    }

    @Override
    public List<RadarMetric> getLatestList(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<RadarMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(RadarMetric::getTimestampStart)
                .last("LIMIT " + TABLE_DEVICE_LIMIT);
        return radarMetricMapper.selectList(wrapper);
    }

    @Override
    public List<RadarMetric> getTargetListByRid(String date, long startTimestamp, long endTimestamp, int rid) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<RadarMetric> wrapper = new LambdaQueryWrapper<>();
        if (startTimestamp != 0L) { wrapper.ge(RadarMetric::getTimestampStart, startTimestamp); }
        if (endTimestamp != 0L) { wrapper.le(RadarMetric::getTimestampStart, endTimestamp); }
        wrapper.eq(RadarMetric::getRid, rid);
        wrapper.orderByAsc(RadarMetric::getTimestampStart);
        return radarMetricMapper.selectList(wrapper);
    }

    @Override
    public List<RadarMetric> getLatestListByRid(String date, int rid) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<RadarMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RadarMetric::getRid, rid)
                .orderByDesc(RadarMetric::getTimestampStart);
        wrapper.last("LIMIT " + TABLE_POSTURE_LIMIT);
        return radarMetricMapper.selectList(wrapper);
    }
}
