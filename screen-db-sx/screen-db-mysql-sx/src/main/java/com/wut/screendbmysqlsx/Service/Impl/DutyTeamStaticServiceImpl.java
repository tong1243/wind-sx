package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.DutyTeamStaticMapper;
import com.wut.screendbmysqlsx.Model.DutyTeamStatic;
import com.wut.screendbmysqlsx.Service.DutyTeamStaticService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 执勤班组静态表服务实现。
 */
@Service
public class DutyTeamStaticServiceImpl extends ServiceImpl<DutyTeamStaticMapper, DutyTeamStatic>
        implements DutyTeamStaticService {
    private final DutyTeamStaticMapper dutyTeamStaticMapper;

    public DutyTeamStaticServiceImpl(DutyTeamStaticMapper dutyTeamStaticMapper) {
        this.dutyTeamStaticMapper = dutyTeamStaticMapper;
    }

    @Override
    public List<DutyTeamStatic> getEnabledTeams() {
        LambdaQueryWrapper<DutyTeamStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DutyTeamStatic::getIsEnabled, 1)
                .orderByAsc(DutyTeamStatic::getSortNo)
                .orderByAsc(DutyTeamStatic::getTeamId)
                .orderByAsc(DutyTeamStatic::getId);
        return dutyTeamStaticMapper.selectList(wrapper);
    }
}
