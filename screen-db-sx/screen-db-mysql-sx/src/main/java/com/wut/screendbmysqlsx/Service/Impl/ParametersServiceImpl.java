package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.ParametersMapper;
import com.wut.screendbmysqlsx.Mapper.PostureMapper;
import com.wut.screendbmysqlsx.Model.Parameters;
import com.wut.screendbmysqlsx.Model.Posture;
import com.wut.screendbmysqlsx.Service.ParametersService;
import com.wut.screendbmysqlsx.Service.PostureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_POSTURE_LIMIT;
import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Service
public class ParametersServiceImpl extends ServiceImpl<ParametersMapper, Parameters> implements ParametersService {
    private final ParametersMapper parametersMapperMapper;

    @Autowired
    public ParametersServiceImpl(ParametersMapper parametersMapperMapper) {
        this.parametersMapperMapper = parametersMapperMapper;
    }

    @Override
    public List<Parameters> getListByDate(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Parameters> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Parameters::getTime)
                .last("LIMIT " + 7);
        return parametersMapperMapper.selectList(wrapper);
    }

    @Override
    public List<Parameters> getAllListByDate(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Parameters> wrapper = new LambdaQueryWrapper<>();
        return parametersMapperMapper.selectList(wrapper);
    }

    @Override
    public Parameters getSecStream(String date, int sId) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Parameters> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Parameters::getRoadId, sId)
                .orderByDesc(Parameters::getTime)
                .last("LIMIT 1");
        return parametersMapperMapper.selectOne(wrapper);
    }

//    @Override
//    public List<Parameters> getListByTarget(String date, long startTimestamp, long endTimestamp) {
//        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
//        LambdaQueryWrapper<Parameters> wrapper = new LambdaQueryWrapper<>();
//        if (startTimestamp != 0L) { wrapper.ge(Parameters::getTimestampStart, startTimestamp); }
//        if (endTimestamp != 0L) { wrapper.le(Parameters::getTimestampStart, endTimestamp); }
//        wrapper.orderByAsc(Posture::getTimestampStart);
//        return parametersMapperMapper.selectList(wrapper);
//    }

//    @Override
//    public Posture getLatestOne(String date) {
//        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
//        LambdaQueryWrapper<Posture> wrapper = new LambdaQueryWrapper<>();
//        wrapper.last("LIMIT 1");
//        return parametersMapperMapper.selectOne(wrapper);
//    }

}
