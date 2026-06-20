package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.SpeedThresholdStatic;

import java.util.List;

/**
 * 限速阈值静态表服务接口。
 */
public interface SpeedThresholdStaticService extends IService<SpeedThresholdStatic> {
    /**
     * 查询所有启用的限速阈值配置，按排序号和等级名称升序返回。
     *
     * @return 启用的限速阈值列表
     */
    List<SpeedThresholdStatic> getEnabledThresholds();

    /**
     * 按管控等级名称更新启用的限速阈值配置。
     *
     * @param row 限速阈值配置
     * @return 是否更新成功
     */
    boolean updateEnabledByControlLevelName(SpeedThresholdStatic row);
}
