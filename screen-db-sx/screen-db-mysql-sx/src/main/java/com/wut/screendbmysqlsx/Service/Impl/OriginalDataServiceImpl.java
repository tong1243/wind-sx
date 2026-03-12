package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.OriginalDataMapper;
import com.wut.screendbmysqlsx.Model.OriginalData;
import com.wut.screendbmysqlsx.Service.OriginalDataService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OriginalDataServiceImpl extends ServiceImpl<OriginalDataMapper, OriginalData>implements OriginalDataService {

    private final OriginalDataMapper originalDataMapper;

    public OriginalDataServiceImpl(OriginalDataMapper originalDataMapper) {
        this.originalDataMapper = originalDataMapper;
    }

    @Override
    public OriginalData getOneByTime(long timestamp) {
        LambdaQueryWrapper<OriginalData> wrapper = new LambdaQueryWrapper<>();
        List<OriginalData> results = originalDataMapper.selectList(wrapper);
        if (results.isEmpty()) {
            return null;
        }
        long closestTime = results.get(0).getTimestamp();
        long minDiff = Math.abs(closestTime - timestamp);

        for (OriginalData dt : results) {
            long diff = Math.abs(dt.getTimestamp() - timestamp);
            if (diff < minDiff) {
                minDiff = diff;
                closestTime = dt.getTimestamp();
            }
        }
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OriginalData::getTimestamp, closestTime);
        return originalDataMapper.selectOne(wrapper);
    }

    @Override
    public OriginalData getOneById(int id) {
        LambdaQueryWrapper<OriginalData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OriginalData::getId, id);
        return originalDataMapper.selectOne(wrapper);
    }
}
