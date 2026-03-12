package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.TunnelRiskMapper;
import com.wut.screendbmysqlsx.Model.TunnelRisk;
import com.wut.screendbmysqlsx.Service.TunnelRiskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Service
public class TunnelRiskServiceImpl extends ServiceImpl<TunnelRiskMapper, TunnelRisk> implements TunnelRiskService {
    private final TunnelRiskMapper tunnelRiskMapper;
    @Autowired
    public TunnelRiskServiceImpl(TunnelRiskMapper tunnelRiskMapper) {
        this.tunnelRiskMapper = tunnelRiskMapper;
    }

    @Override
    public List<TunnelRisk> getThreeRisk(String date, long time) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<TunnelRisk> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(TunnelRisk::getTimestamp);
        wrapper.last("LIMIT 3");
        return tunnelRiskMapper.selectList(wrapper);
    }
    @Override
    public List<TunnelRisk> getAllRisk(String date, long time) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<TunnelRisk> wrapper = new LambdaQueryWrapper<>();
        return tunnelRiskMapper.selectList(wrapper);
    }
}
