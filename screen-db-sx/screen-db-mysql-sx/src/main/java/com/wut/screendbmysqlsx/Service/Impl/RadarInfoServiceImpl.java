package com.wut.screendbmysqlsx.Service.Impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.RadarInfoMapper;
import com.wut.screendbmysqlsx.Model.RadarInfo;
import com.wut.screendbmysqlsx.Service.RadarInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RadarInfoServiceImpl extends ServiceImpl<RadarInfoMapper, RadarInfo> implements RadarInfoService {
    private final RadarInfoMapper radarInfoMapper;

    @Autowired
    public RadarInfoServiceImpl(RadarInfoMapper radarInfoMapper) {
        this.radarInfoMapper = radarInfoMapper;
    }

    @Override
    public List<RadarInfo> getAllRadarInfo() {
        LambdaQueryWrapper<RadarInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(RadarInfo::getRid);
        return radarInfoMapper.selectList(wrapper);
    }

    @Override
    public List<RadarInfo> getEnabledRadarInfo() {
        LambdaQueryWrapper<RadarInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(RadarInfo::getState, -1)
                .orderByAsc(RadarInfo::getRid);
        return radarInfoMapper.selectList(wrapper);
    }
}
