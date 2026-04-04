package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.RoadSegmentStaticMapper;
import com.wut.screendbmysqlsx.Model.RoadSegmentStatic;
import com.wut.screendbmysqlsx.Service.RoadSegmentStaticService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 路段静态信息服务实现。
 */
@Service
public class RoadSegmentStaticServiceImpl extends ServiceImpl<RoadSegmentStaticMapper, RoadSegmentStatic> implements RoadSegmentStaticService {
    /** 路段静态信息 Mapper。 */
    private final RoadSegmentStaticMapper roadSegmentStaticMapper;

    public RoadSegmentStaticServiceImpl(RoadSegmentStaticMapper roadSegmentStaticMapper) {
        this.roadSegmentStaticMapper = roadSegmentStaticMapper;
    }

    /**
     * 按方向与排序号返回启用路段。
     *
     * @return 启用路段列表
     */
    @Override
    public List<RoadSegmentStatic> getEnabledSegments() {
        LambdaQueryWrapper<RoadSegmentStatic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoadSegmentStatic::getIsEnabled, 1)
                .orderByAsc(RoadSegmentStatic::getDirection)
                .orderByAsc(RoadSegmentStatic::getSortNo)
                .orderByAsc(RoadSegmentStatic::getStartLocationM);
        return roadSegmentStaticMapper.selectList(wrapper);
    }
}
