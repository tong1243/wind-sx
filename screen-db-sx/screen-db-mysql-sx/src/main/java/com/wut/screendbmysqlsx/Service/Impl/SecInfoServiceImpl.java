package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.SecInfoMapper;
import com.wut.screendbmysqlsx.Mapper.TunnelSecInfoMapper;
import com.wut.screendbmysqlsx.Model.SecInfo;
import com.wut.screendbmysqlsx.Model.TunnelSecInfo;
import com.wut.screendbmysqlsx.Service.SecInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SecInfoServiceImpl extends ServiceImpl<SecInfoMapper, SecInfo> implements SecInfoService {
    private final SecInfoMapper secInfoMapper;
    private final TunnelSecInfoMapper tunnelSecInfoMapper;

    @Autowired
    public SecInfoServiceImpl(SecInfoMapper secInfoMapper, TunnelSecInfoMapper tunnelSecInfoMapper) {
        this.secInfoMapper = secInfoMapper;
        this.tunnelSecInfoMapper = tunnelSecInfoMapper;
    }

    @Override
    public List<SecInfo> getAllSecInfo() {
        LambdaQueryWrapper<SecInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SecInfo::getXsecValue);
        return secInfoMapper.selectList(wrapper);
    }

    @Override
    public List<TunnelSecInfo> getAllTunnelSecInfo() {
        LambdaQueryWrapper<TunnelSecInfo> wrapper = new LambdaQueryWrapper<>();
        return tunnelSecInfoMapper.selectList(wrapper);
    }

}
