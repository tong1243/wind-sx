package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.ControlIntervalStatic;

import java.util.List;

/**
 * 管控区间静态表服务接口。
 */
public interface ControlIntervalStaticService extends IService<ControlIntervalStatic> {
    /**
     * 查询所有启用的管控区间，按排序号和起点位置升序返回。
     *
     * @return 启用的管控区间列表
     */
    List<ControlIntervalStatic> getEnabledIntervals();
}
