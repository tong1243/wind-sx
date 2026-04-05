package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.ClosureDeviceStaticMapper;
import com.wut.screendbmysqlsx.Model.ClosureDeviceStatic;
import com.wut.screendbmysqlsx.Service.ClosureDeviceStaticService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 封路设备静态表服务实现。
 */
@Service
public class ClosureDeviceStaticServiceImpl extends ServiceImpl<ClosureDeviceStaticMapper, ClosureDeviceStatic>
        implements ClosureDeviceStaticService {
    private final ClosureDeviceStaticMapper closureDeviceStaticMapper;

    public ClosureDeviceStaticServiceImpl(ClosureDeviceStaticMapper closureDeviceStaticMapper) {
        this.closureDeviceStaticMapper = closureDeviceStaticMapper;
    }

    @Override
    public List<ClosureDeviceStatic> getEnabledDevices() {
        LambdaQueryWrapper<ClosureDeviceStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClosureDeviceStatic::getIsEnabled, 1)
                .orderByAsc(ClosureDeviceStatic::getSortNo)
                .orderByAsc(ClosureDeviceStatic::getDeviceId)
                .orderByAsc(ClosureDeviceStatic::getId);
        return closureDeviceStaticMapper.selectList(wrapper);
    }
}
