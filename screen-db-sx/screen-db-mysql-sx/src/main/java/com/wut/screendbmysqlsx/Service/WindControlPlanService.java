package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.WindControlPlan;

import java.util.List;

/**
 * 管控方案持久化服务接口。
 */
public interface WindControlPlanService extends IService<WindControlPlan> {
    /**
     * 按 planId 查询方案。
     */
    WindControlPlan getByPlanId(String planId);

    /**
     * 按 planId 执行新增或更新。
     */
    void upsertByPlanId(String planId, String segment, String status, long planTimestamp, String payloadJson, long updatedAt);

    /**
     * 查询最近更新的方案列表。
     */
    List<WindControlPlan> getLatestPlans(int limit);

    /**
     * 按 planId 删除方案。
     */
    boolean removeByPlanId(String planId);
}
