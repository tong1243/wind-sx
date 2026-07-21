package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.SpeedThresholdStaticMapper;
import com.wut.screendbmysqlsx.Model.SpeedThresholdStatic;
import com.wut.screendbmysqlsx.Service.SpeedThresholdStaticService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

    @Override
    public boolean updateEnabledByControlLevelName(SpeedThresholdStatic row) {
        if (row == null || row.getControlLevelName() == null || row.getControlLevelName().isBlank()) {
            return false;
        }
        LambdaUpdateWrapper<SpeedThresholdStatic> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SpeedThresholdStatic::getIsEnabled, 1)
                .in(SpeedThresholdStatic::getControlLevelName, levelNameCandidates(row.getControlLevelName()))
                .set(SpeedThresholdStatic::getWindLevelDesc, row.getWindLevelDesc())
                .set(SpeedThresholdStatic::getMinWindLevel, row.getMinWindLevel())
                .set(SpeedThresholdStatic::getMaxWindLevel, row.getMaxWindLevel())
                .set(SpeedThresholdStatic::getLightVehicleSpeedLimit, row.getLightVehicleSpeedLimit())
                .set(SpeedThresholdStatic::getHeavyVehicleSpeedLimit, row.getHeavyVehicleSpeedLimit())
                .set(SpeedThresholdStatic::getUpdateTime, LocalDateTime.now());
        return speedThresholdStaticMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean updateEnabledByWindLevel(int windLevel, Integer lightVehicleSpeedLimit, Integer heavyVehicleSpeedLimit) {
        if (windLevel < 1 || windLevel > 12) {
            return false;
        }
        LambdaUpdateWrapper<SpeedThresholdStatic> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SpeedThresholdStatic::getIsEnabled, 1)
                .and(w -> w.isNull(SpeedThresholdStatic::getMinWindLevel)
                        .or()
                        .le(SpeedThresholdStatic::getMinWindLevel, windLevel))
                .and(w -> w.isNull(SpeedThresholdStatic::getMaxWindLevel)
                        .or()
                        .ge(SpeedThresholdStatic::getMaxWindLevel, windLevel))
                .set(lightVehicleSpeedLimit != null, SpeedThresholdStatic::getLightVehicleSpeedLimit, lightVehicleSpeedLimit)
                .set(heavyVehicleSpeedLimit != null, SpeedThresholdStatic::getHeavyVehicleSpeedLimit, heavyVehicleSpeedLimit)
                .set(SpeedThresholdStatic::getUpdateTime, LocalDateTime.now());
        return speedThresholdStaticMapper.update(null, wrapper) > 0;
    }

    private List<String> levelNameCandidates(String controlLevelName) {
        String name = controlLevelName == null ? "" : controlLevelName.trim();
        if (name.contains("一") || name.contains("红")) {
            return List.of("一级", "一级管控", "红色警戒");
        }
        if (name.contains("二") || name.contains("橙")) {
            return List.of("二级", "二级管控", "橙色警戒");
        }
        if (name.contains("三") || name.contains("黄")) {
            return List.of("三级", "三级管控", "黄色警戒");
        }
        if (name.contains("四") || name.contains("蓝")) {
            return List.of("四级", "四级管控", "蓝色警戒");
        }
        if (name.contains("五") || name.contains("绿") || name.contains("正常")) {
            return List.of("五级", "五级管控", "绿色警戒", "正常通行");
        }
        return List.of(name);
    }
}
