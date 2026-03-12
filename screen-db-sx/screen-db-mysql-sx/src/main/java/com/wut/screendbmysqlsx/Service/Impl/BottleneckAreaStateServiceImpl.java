package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.BottleneckAreaStateMapper;
import com.wut.screendbmysqlsx.Mapper.PostureMapper;
import com.wut.screendbmysqlsx.Model.BottleneckAreaState;
import com.wut.screendbmysqlsx.Model.Posture;
import com.wut.screendbmysqlsx.Service.BottleneckAreaStateService;
import com.wut.screendbmysqlsx.Service.PostureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_POSTURE_LIMIT;
import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Service
public class BottleneckAreaStateServiceImpl extends ServiceImpl<BottleneckAreaStateMapper, BottleneckAreaState> implements BottleneckAreaStateService {
    private final BottleneckAreaStateMapper bottleneckAreaStateMapper;

    @Autowired
    public BottleneckAreaStateServiceImpl(BottleneckAreaStateMapper bottleneckAreaStateMapper) {
        this.bottleneckAreaStateMapper = bottleneckAreaStateMapper;
    }
    @Override
    public List<BottleneckAreaState> getListByDate(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<BottleneckAreaState> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BottleneckAreaState::getId)
                .last("LIMIT " + 5);
        return bottleneckAreaStateMapper.selectList(wrapper);
    }

    @Override
    public List<BottleneckAreaState> getListByTarget(String date, long startTimestamp, long endTimestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<BottleneckAreaState> wrapper = new LambdaQueryWrapper<>();
        return bottleneckAreaStateMapper.selectList(wrapper);
    }

    @Override
    public BottleneckAreaState getLatestOne(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<BottleneckAreaState> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BottleneckAreaState::getId).last("LIMIT 1");
        return bottleneckAreaStateMapper.selectOne(wrapper);
    }
}
