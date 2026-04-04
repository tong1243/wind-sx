package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.WindControlPlan;

import java.util.List;

public interface WindControlPlanService extends IService<WindControlPlan> {
    /**
     * 按 planId 查询方案。
     */
    WindControlPlan getByPlanId(String planId);

    /**
     * 按 planId 写入或更新方案。
     */
    void upsertByPlanId(String planId, String segment, String status, long planTimestamp, String payloadJson, long updatedAt);

    /**
     * 查询最近更新的方案列表。
     */
    List<WindControlPlan> getLatestPlans(int limit);
}
