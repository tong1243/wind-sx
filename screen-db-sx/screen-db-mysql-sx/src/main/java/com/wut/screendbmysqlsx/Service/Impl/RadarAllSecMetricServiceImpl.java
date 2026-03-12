package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.RadarAllSecMetricMapper;
import com.wut.screendbmysqlsx.Model.RadarAllSecMetric;
import com.wut.screendbmysqlsx.Service.RadarAllSecMetricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SECTION_LIST_SIZE;
import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Service
public class RadarAllSecMetricServiceImpl extends ServiceImpl<RadarAllSecMetricMapper, RadarAllSecMetric> implements RadarAllSecMetricService {
    private final RadarAllSecMetricMapper radarAllSecMetricMapper;

    @Autowired
    public RadarAllSecMetricServiceImpl(RadarAllSecMetricMapper radarAllSecMetricMapper) {
        this.radarAllSecMetricMapper = radarAllSecMetricMapper;
    }

    @Override
    public List<RadarAllSecMetric> getListByTimestamp(String date, long timestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<RadarAllSecMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RadarAllSecMetric::getTimestampStart, timestamp)
                .orderByAsc(RadarAllSecMetric::getXsecValue);
        return radarAllSecMetricMapper.selectList(wrapper);
    }

    @Override
    public List<RadarAllSecMetric> getLatestList(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<RadarAllSecMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(RadarAllSecMetric::getTimestampStart);
        wrapper.last("LIMIT " + TABLE_SECTION_LIST_SIZE);
        return radarAllSecMetricMapper.selectList(wrapper);
    }

    @Override
    public List<RadarAllSecMetric> getRadarRealTimeTargetList(String date, long startTimestamp, long endTimestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<RadarAllSecMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(RadarAllSecMetric::getTimestampStart)
                .ge(RadarAllSecMetric::getTimestampStart, startTimestamp)
                .le(RadarAllSecMetric::getTimestampStart, endTimestamp);
        wrapper.last("LIMIT " + TABLE_SECTION_LIST_SIZE);
        return radarAllSecMetricMapper.selectList(wrapper);
    }
}
