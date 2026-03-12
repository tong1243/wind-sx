package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.SectionMapper;
import com.wut.screendbmysqlsx.Model.Section;
import com.wut.screendbmysqlsx.Service.SectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommonsx.Static.DbModuleStatic.*;

@Service
public class SectionServiceImpl extends ServiceImpl<SectionMapper, Section> implements SectionService {
    private final SectionMapper sectionMapper;

    @Autowired
    public SectionServiceImpl(SectionMapper sectionMapper) {
        this.sectionMapper = sectionMapper;
    }

    @Override
    public List<Section> getListByDate(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Section> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Section::getTimestampStart)
                .last("LIMIT " + TABLE_SECTION_LIMIT * TABLE_SECTION_LIST_SIZE);
        return sectionMapper.selectList(wrapper);
    }

    @Override
    public List<Section> getListByDateAndSec(String date, double sec) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Section> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Section::getXsecValue, sec)
                .orderByDesc(Section::getTimestampStart)
                .last("LIMIT " + TABLE_SECTION_LIMIT);
        return sectionMapper.selectList(wrapper);
    }

    @Override
    public List<Section> getListByTarget(String date, long startTimestamp, long endTimestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Section> wrapper = new LambdaQueryWrapper<>();
        if (startTimestamp != 0L) { wrapper.ge(Section::getTimestampStart, startTimestamp); }
        if (endTimestamp != 0L) { wrapper.le(Section::getTimestampStart, endTimestamp); }
        wrapper.orderByAsc(Section::getTimestampStart);
        return sectionMapper.selectList(wrapper);
    }

    @Override
    public List<Section> getListByTargetAndSec(String date, long startTimestamp, long endTimestamp, double sec) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Section> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Section::getXsecValue, sec);
        if (startTimestamp != 0L) { wrapper.ge(Section::getTimestampStart, startTimestamp); }
        if (endTimestamp != 0L) { wrapper.le(Section::getTimestampStart, endTimestamp); }
        wrapper.orderByAsc(Section::getTimestampStart);
        return sectionMapper.selectList(wrapper);
    }

    @Override
    public List<Section> getLatestList(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Section> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Section::getTimestampStart)
                .orderByAsc(Section::getXsecValue)
                .last("LIMIT " + TABLE_SECTION_LIST_SIZE);
        return sectionMapper.selectList(wrapper);
    }

    @Override
    public List<Section> getRadarRealTimeTargetList(String date, long startTimestamp, long endTimestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Section> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Section::getTimestampStart)
                .ge(Section::getTimestampStart, startTimestamp)
                .le(Section::getTimestampStart, endTimestamp);
        wrapper.last("LIMIT " + TABLE_SECTION_LIST_SIZE);
        return sectionMapper.selectList(wrapper);
    }
}
