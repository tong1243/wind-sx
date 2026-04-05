package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.ControlPlanStaticMapper;
import com.wut.screendbmysqlsx.Model.ControlPlanStatic;
import com.wut.screendbmysqlsx.Service.ControlPlanStaticService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管控预案静态表服务实现。
 */
@Service
public class ControlPlanStaticServiceImpl extends ServiceImpl<ControlPlanStaticMapper, ControlPlanStatic>
        implements ControlPlanStaticService {
    /**
     * 管控预案静态表 Mapper。
     */
    private final ControlPlanStaticMapper controlPlanStaticMapper;

    public ControlPlanStaticServiceImpl(ControlPlanStaticMapper controlPlanStaticMapper) {
        this.controlPlanStaticMapper = controlPlanStaticMapper;
    }

    /**
     * 查询启用预案，返回顺序与静态配置一致。
     *
     * @return 启用预案列表
     */
    @Override
    public List<ControlPlanStatic> getEnabledPlans() {
        LambdaQueryWrapper<ControlPlanStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ControlPlanStatic::getIsEnabled, 1)
                .orderByAsc(ControlPlanStatic::getSortNo)
                .orderByAsc(ControlPlanStatic::getControlLevelName)
                .orderByAsc(ControlPlanStatic::getId);
        return controlPlanStaticMapper.selectList(wrapper);
    }
}
