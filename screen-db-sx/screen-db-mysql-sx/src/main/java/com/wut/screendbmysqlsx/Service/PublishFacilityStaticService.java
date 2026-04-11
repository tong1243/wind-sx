package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.PublishFacilityStatic;

import java.util.List;

/**
 * 信息发布设施静态表服务接口。
 */
public interface PublishFacilityStaticService extends IService<PublishFacilityStatic> {
    /**
     * 查询启用的设施数据，按排序号升序返回。
     *
     * @return 启用设施列表
     */
    List<PublishFacilityStatic> getEnabledFacilities();
}
