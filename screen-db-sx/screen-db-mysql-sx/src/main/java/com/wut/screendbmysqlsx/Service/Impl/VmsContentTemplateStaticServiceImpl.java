package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.VmsContentTemplateStaticMapper;
import com.wut.screendbmysqlsx.Model.VmsContentTemplateStatic;
import com.wut.screendbmysqlsx.Service.VmsContentTemplateStaticService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 可变信息发布内容模板静态表服务实现。
 */
@Service
public class VmsContentTemplateStaticServiceImpl extends ServiceImpl<VmsContentTemplateStaticMapper, VmsContentTemplateStatic>
        implements VmsContentTemplateStaticService {

    private final VmsContentTemplateStaticMapper mapper;

    public VmsContentTemplateStaticServiceImpl(VmsContentTemplateStaticMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<VmsContentTemplateStatic> listByControlLevel(String controlLevel, Integer isEnabled) {
        LambdaQueryWrapper<VmsContentTemplateStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VmsContentTemplateStatic::getControlLevel, controlLevel);
        if (isEnabled != null) {
            wrapper.eq(VmsContentTemplateStatic::getIsEnabled, isEnabled);
        }
        wrapper.orderByAsc(VmsContentTemplateStatic::getSortNo)
                .orderByAsc(VmsContentTemplateStatic::getTemplateCode)
                .orderByAsc(VmsContentTemplateStatic::getId);
        return mapper.selectList(wrapper);
    }

    @Override
    public VmsContentTemplateStatic getByTemplateCode(String templateCode) {
        LambdaQueryWrapper<VmsContentTemplateStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VmsContentTemplateStatic::getTemplateCode, templateCode).last("LIMIT 1");
        return mapper.selectOne(wrapper);
    }

    @Override
    public VmsContentTemplateStatic matchTemplate(String controlLevel, String publishPosition, String vehicleType) {
        LambdaQueryWrapper<VmsContentTemplateStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VmsContentTemplateStatic::getControlLevel, controlLevel)
                .eq(VmsContentTemplateStatic::getPublishPosition, publishPosition)
                .eq(VmsContentTemplateStatic::getVehicleType, vehicleType)
                .eq(VmsContentTemplateStatic::getIsEnabled, 1)
                .orderByAsc(VmsContentTemplateStatic::getSortNo)
                .orderByAsc(VmsContentTemplateStatic::getId)
                .last("LIMIT 1");
        return mapper.selectOne(wrapper);
    }

    @Override
    public boolean removeByTemplateCode(String templateCode) {
        LambdaQueryWrapper<VmsContentTemplateStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VmsContentTemplateStatic::getTemplateCode, templateCode);
        return mapper.delete(wrapper) > 0;
    }
}

