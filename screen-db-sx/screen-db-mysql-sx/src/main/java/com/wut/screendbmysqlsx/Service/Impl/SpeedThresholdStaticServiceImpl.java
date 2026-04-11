package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.SpeedThresholdStaticMapper;
import com.wut.screendbmysqlsx.Model.SpeedThresholdStatic;
import com.wut.screendbmysqlsx.Service.SpeedThresholdStaticService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 限速阈值静态表服务实现。
 */
@Service
public class SpeedThresholdStaticServiceImpl extends ServiceImpl<SpeedThresholdStaticMapper, SpeedThresholdStatic>
        implements SpeedThresholdStaticService {
    /**
     * 限速阈值静态表 Mapper。
     */
    private final SpeedThresholdStaticMapper speedThresholdStaticMapper;

    public SpeedThresholdStaticServiceImpl(SpeedThresholdStaticMapper speedThresholdStaticMapper) {
        this.speedThresholdStaticMapper = speedThresholdStaticMapper;
    }

    /**
     * 查询启用阈值，返回顺序与静态配置一致。
     *
     * @return 启用阈值列表
     */
    @Override
    public List<SpeedThresholdStatic> getEnabledThresholds() {
        LambdaQueryWrapper<SpeedThresholdStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpeedThresholdStatic::getIsEnabled, 1)
                .orderByAsc(SpeedThresholdStatic::getSortNo)
                .orderByAsc(SpeedThresholdStatic::getControlLevelName)
                .orderByAsc(SpeedThresholdStatic::getId);
        return speedThresholdStaticMapper.selectList(wrapper);
    }
}
