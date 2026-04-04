package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.WindControlPlanMapper;
import com.wut.screendbmysqlsx.Model.WindControlPlan;
import com.wut.screendbmysqlsx.Service.WindControlPlanService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/**
 * 管控方案服务实现。
 */
public class WindControlPlanServiceImpl extends ServiceImpl<WindControlPlanMapper, WindControlPlan> implements WindControlPlanService {
    private final WindControlPlanMapper windControlPlanMapper;

    public WindControlPlanServiceImpl(WindControlPlanMapper windControlPlanMapper) {
        this.windControlPlanMapper = windControlPlanMapper;
    }

    @Override
    public WindControlPlan getByPlanId(String planId) {
        LambdaQueryWrapper<WindControlPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WindControlPlan::getPlanId, planId);
        return windControlPlanMapper.selectOne(wrapper);
    }

    @Override
    public void upsertByPlanId(String planId, String segment, String status, long planTimestamp, String payloadJson, long updatedAt) {
        // 按 plan_id 唯一键进行幂等写入，避免重复插入。
        WindControlPlan existing = getByPlanId(planId);
        if (existing == null) {
            WindControlPlan insert = new WindControlPlan();
            insert.setPlanId(planId);
            insert.setSegment(segment);
            insert.setStatus(status);
            insert.setPlanTimestamp(planTimestamp);
            insert.setPayloadJson(payloadJson);
            insert.setUpdatedAt(updatedAt);
            windControlPlanMapper.insert(insert);
            return;
        }
        existing.setSegment(segment);
        existing.setStatus(status);
        existing.setPlanTimestamp(planTimestamp);
        existing.setPayloadJson(payloadJson);
        existing.setUpdatedAt(updatedAt);
        windControlPlanMapper.updateById(existing);
    }

    @Override
    public List<WindControlPlan> getLatestPlans(int limit) {
        LambdaQueryWrapper<WindControlPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(WindControlPlan::getUpdatedAt).last("limit " + limit);
        return windControlPlanMapper.selectList(wrapper);
    }
}
