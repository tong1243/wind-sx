package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.ControlPlanStatic;

import java.util.List;

/**
 * 管控预案静态表服务接口。
 */
public interface ControlPlanStaticService extends IService<ControlPlanStatic> {
    /**
     * 查询所有启用的管控预案，按排序号和等级名称升序返回。
     *
     * @return 启用的预案列表
     */
    List<ControlPlanStatic> getEnabledPlans();
}
