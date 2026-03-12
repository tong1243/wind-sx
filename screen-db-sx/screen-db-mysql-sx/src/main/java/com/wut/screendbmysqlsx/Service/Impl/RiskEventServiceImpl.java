package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screencommonsx.Response.Risk.RiskLevelData;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.RiskEventMapper;
import com.wut.screendbmysqlsx.Model.RiskEvent;
import com.wut.screendbmysqlsx.Service.RiskEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Service
public class RiskEventServiceImpl extends ServiceImpl<RiskEventMapper, RiskEvent> implements RiskEventService {
    private final RiskEventMapper riskEventMapper;
    @Autowired
    public RiskEventServiceImpl(RiskEventMapper riskEventMapper) {
        this.riskEventMapper = riskEventMapper;
    }

    @Override
    public List<RiskEvent> getAllRisk(String date, long time) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<RiskEvent> wrapper = new LambdaQueryWrapper<>();
        return riskEventMapper.selectList(wrapper);
    }

    @Override
    public List<RiskLevelData> getRiskLevelData(String date, long timestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        QueryWrapper<RiskEvent> wrapper = new QueryWrapper<>();
        // 构建查询语句
        wrapper.select("riskType", "COUNT(carId) AS carCount")
                .groupBy("riskType");
        // 查询结果转换为需要的格式
        return riskEventMapper.selectMaps(wrapper).stream()
                .map(map -> {
                    RiskLevelData data = new RiskLevelData();
                    data.setLevel(((Number) map.get("riskType")).intValue());
                    data.setCarCount(((Number) map.get("carCount")).intValue());
                    return data;
                }).collect(Collectors.toList());
    }
    @Override
    public List<RiskEvent> getTargetRisk(String date, long timestampStart, long timestampEnd) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<RiskEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(RiskEvent::getTimestamp, timestampStart, timestampEnd);
        return riskEventMapper.selectList(wrapper);
    }

}
