package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.PublishFacilityStaticMapper;
import com.wut.screendbmysqlsx.Model.PublishFacilityStatic;
import com.wut.screendbmysqlsx.Service.PublishFacilityStaticService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 信息发布设施静态表服务实现。
 */
@Service
public class PublishFacilityStaticServiceImpl extends ServiceImpl<PublishFacilityStaticMapper, PublishFacilityStatic>
        implements PublishFacilityStaticService {
    private final PublishFacilityStaticMapper publishFacilityStaticMapper;

    public PublishFacilityStaticServiceImpl(PublishFacilityStaticMapper publishFacilityStaticMapper) {
        this.publishFacilityStaticMapper = publishFacilityStaticMapper;
    }

    @Override
    public List<PublishFacilityStatic> getEnabledFacilities() {
        LambdaQueryWrapper<PublishFacilityStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PublishFacilityStatic::getIsEnabled, 1)
                .orderByAsc(PublishFacilityStatic::getSortNo)
                .orderByAsc(PublishFacilityStatic::getFacilityId)
                .orderByAsc(PublishFacilityStatic::getId);
        return publishFacilityStaticMapper.selectList(wrapper);
    }
}
