package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.RoadSegmentStatic;

import java.util.List;

/**
 * 路段静态信息服务接口。
 */
public interface RoadSegmentStaticService extends IService<RoadSegmentStatic> {
    /**
     * 查询启用的路段列表。
     *
     * @return 启用路段
     */
    List<RoadSegmentStatic> getEnabledSegments();
}
