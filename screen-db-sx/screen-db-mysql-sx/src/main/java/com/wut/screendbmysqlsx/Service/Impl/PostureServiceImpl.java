package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.PostureMapper;
import com.wut.screendbmysqlsx.Model.Posture;
import com.wut.screendbmysqlsx.Service.PostureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_POSTURE_LIMIT;
import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Service
public class PostureServiceImpl extends ServiceImpl<PostureMapper, Posture> implements PostureService {
    private final PostureMapper postureMapper;

    @Autowired
    public PostureServiceImpl(PostureMapper postureMapper) {
        this.postureMapper = postureMapper;
    }

    @Override
    public List<Posture> getListByDate(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Posture> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Posture::getTimestampStart)
                .last("LIMIT " + TABLE_POSTURE_LIMIT);
        return postureMapper.selectList(wrapper);
    }

    @Override
    public List<Posture> getListByTarget(String date, long startTimestamp, long endTimestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Posture> wrapper = new LambdaQueryWrapper<>();
        if (startTimestamp != 0L) { wrapper.ge(Posture::getTimestampStart, startTimestamp); }
        if (endTimestamp != 0L) { wrapper.le(Posture::getTimestampStart, endTimestamp); }
        wrapper.orderByAsc(Posture::getTimestampStart);
        return postureMapper.selectList(wrapper);
    }

    @Override
    public Posture getLatestOne(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Posture> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Posture::getTimestampStart)
                .last("LIMIT 1");
        return postureMapper.selectOne(wrapper);
    }

}
