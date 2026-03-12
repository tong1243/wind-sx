package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.RadarSecMetricMapper;
import com.wut.screendbmysqlsx.Model.FiberSecMetric;
import com.wut.screendbmysqlsx.Model.RadarSecMetric;
import com.wut.screendbmysqlsx.Service.RadarSecMetricService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SECTION_LIST_SIZE;
import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Service
public class RadarSecMetricServiceImpl extends ServiceImpl<RadarSecMetricMapper, RadarSecMetric> implements RadarSecMetricService {
    private final RadarSecMetricMapper radarSecMetricMapper;

    @Autowired
    public RadarSecMetricServiceImpl(RadarSecMetricMapper radarSecMetricMapper) {
        this.radarSecMetricMapper = radarSecMetricMapper;
    }

    @Override
    public List<RadarSecMetric> getListByTimestampAndRid(String date, long timestamp, int rid) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<RadarSecMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RadarSecMetric::getTimestampStart, timestamp)
                .eq(RadarSecMetric::getRid, rid)
                .orderByAsc(RadarSecMetric::getXsecValue);
        return radarSecMetricMapper.selectList(wrapper);
    }

    @Override
    public List<RadarSecMetric> getLatestListByRid(String date, int rid) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<RadarSecMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RadarSecMetric::getRid, rid)
                .orderByDesc(RadarSecMetric::getTimestampStart);
        wrapper.last("LIMIT " + TABLE_SECTION_LIST_SIZE);
        return radarSecMetricMapper.selectList(wrapper);
    }

    @Override
    public List<RadarSecMetric> getTargetListByRid(String date, long startTimestamp, long endTimestamp, int rid) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<RadarSecMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RadarSecMetric::getRid, rid);
        if (startTimestamp != 0L) { wrapper.ge(RadarSecMetric::getTimestampStart, startTimestamp); }
        if (endTimestamp != 0L) { wrapper.le(RadarSecMetric::getTimestampStart, endTimestamp); }
        wrapper.orderByAsc(RadarSecMetric::getTimestampStart);
        return radarSecMetricMapper.selectList(wrapper);
    }
}
