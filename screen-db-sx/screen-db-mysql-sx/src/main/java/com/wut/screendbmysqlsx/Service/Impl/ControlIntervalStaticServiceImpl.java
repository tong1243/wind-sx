package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.ControlIntervalStaticMapper;
import com.wut.screendbmysqlsx.Model.ControlIntervalStatic;
import com.wut.screendbmysqlsx.Service.ControlIntervalStaticService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管控区间静态表服务实现。
 */
@Service
public class ControlIntervalStaticServiceImpl extends ServiceImpl<ControlIntervalStaticMapper, ControlIntervalStatic>
        implements ControlIntervalStaticService {
    /**
     * 管控区间静态表 Mapper。
     */
    private final ControlIntervalStaticMapper controlIntervalStaticMapper;

    public ControlIntervalStaticServiceImpl(ControlIntervalStaticMapper controlIntervalStaticMapper) {
        this.controlIntervalStaticMapper = controlIntervalStaticMapper;
    }

    /**
     * 查询启用区间，返回顺序与静态配置一致。
     *
     * @return 启用区间列表
     */
    @Override
    public List<ControlIntervalStatic> getEnabledIntervals() {
        LambdaQueryWrapper<ControlIntervalStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ControlIntervalStatic::getIsEnabled, 1)
                .orderByAsc(ControlIntervalStatic::getSortNo)
                .orderByAsc(ControlIntervalStatic::getSegmentStartLocationM)
                .orderByAsc(ControlIntervalStatic::getId);
        return controlIntervalStaticMapper.selectList(wrapper);
    }
}
