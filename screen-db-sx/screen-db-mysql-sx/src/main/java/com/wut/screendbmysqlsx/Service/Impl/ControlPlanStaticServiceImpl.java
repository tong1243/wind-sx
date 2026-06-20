package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.ControlPlanStaticMapper;
import com.wut.screendbmysqlsx.Model.ControlPlanStatic;
import com.wut.screendbmysqlsx.Service.ControlPlanStaticService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
                .orderByDesc(ControlPlanStatic::getSortNo)
                .orderByAsc(ControlPlanStatic::getControlLevelName)
                .orderByAsc(ControlPlanStatic::getId);
        return controlPlanStaticMapper.selectList(wrapper);
    }

    @Override
    public boolean updateEnabledByControlLevelName(ControlPlanStatic row) {
        if (row == null || row.getControlLevelName() == null || row.getControlLevelName().isBlank()) {
            return false;
        }
        LambdaUpdateWrapper<ControlPlanStatic> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ControlPlanStatic::getIsEnabled, 1)
                .in(ControlPlanStatic::getControlLevelName, levelNameCandidates(row.getControlLevelName()))
                .set(ControlPlanStatic::getWindLevelDesc, row.getWindLevelDesc())
                .set(ControlPlanStatic::getRiskSectionPlan, row.getRiskSectionPlan())
                .set(ControlPlanStatic::getUpstreamExitPlan, row.getUpstreamExitPlan())
                .set(ControlPlanStatic::getUpstreamEntryPlan, row.getUpstreamEntryPlan())
                .set(ControlPlanStatic::getUpstreamServiceAreaPlan, row.getUpstreamServiceAreaPlan())
                .set(ControlPlanStatic::getUpdateTime, LocalDateTime.now());
        return controlPlanStaticMapper.update(null, wrapper) > 0;
    }

    private List<String> levelNameCandidates(String controlLevelName) {
        String name = controlLevelName == null ? "" : controlLevelName.trim();
        if (name.contains("一") || name.contains("红")) {
            return List.of("一级管控", "一级", "红色警戒");
        }
        if (name.contains("二") || name.contains("橙")) {
            return List.of("二级管控", "二级", "橙色警戒");
        }
        if (name.contains("三") || name.contains("黄")) {
            return List.of("三级管控", "三级", "黄色警戒");
        }
        if (name.contains("四") || name.contains("蓝")) {
            return List.of("四级管控", "四级", "蓝色警戒");
        }
        if (name.contains("五") || name.contains("绿") || name.contains("正常")) {
            return List.of("五级管控", "五级", "绿色警戒", "正常通行");
        }
        return List.of(name);
    }
}
