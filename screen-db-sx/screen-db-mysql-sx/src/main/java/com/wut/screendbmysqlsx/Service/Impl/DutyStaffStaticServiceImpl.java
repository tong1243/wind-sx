package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.DutyStaffStaticMapper;
import com.wut.screendbmysqlsx.Model.DutyStaffStatic;
import com.wut.screendbmysqlsx.Service.DutyStaffStaticService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 执勤人员静态表服务实现。
 */
@Service
public class DutyStaffStaticServiceImpl extends ServiceImpl<DutyStaffStaticMapper, DutyStaffStatic>
        implements DutyStaffStaticService {
    private final DutyStaffStaticMapper dutyStaffStaticMapper;

    public DutyStaffStaticServiceImpl(DutyStaffStaticMapper dutyStaffStaticMapper) {
        this.dutyStaffStaticMapper = dutyStaffStaticMapper;
    }

    @Override
    public List<DutyStaffStatic> getEnabledStaff() {
        LambdaQueryWrapper<DutyStaffStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DutyStaffStatic::getIsEnabled, 1)
                .orderByAsc(DutyStaffStatic::getSortNo)
                .orderByAsc(DutyStaffStatic::getStaffId)
                .orderByAsc(DutyStaffStatic::getId);
        return dutyStaffStaticMapper.selectList(wrapper);
    }
}
