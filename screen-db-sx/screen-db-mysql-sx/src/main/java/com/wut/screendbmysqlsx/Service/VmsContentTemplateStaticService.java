package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.VmsContentTemplateStatic;

import java.util.List;

/**
 * 可变信息发布内容模板静态表服务接口。
 */
public interface VmsContentTemplateStaticService extends IService<VmsContentTemplateStatic> {
    /**
     * 按管控等级查询模板，支持按启用状态筛选。
     */
    List<VmsContentTemplateStatic> listByControlLevel(String controlLevel, Integer isEnabled);

    /**
     * 按模板编码查询单条。
     */
    VmsContentTemplateStatic getByTemplateCode(String templateCode);

    /**
     * 按条件匹配模板（优先车型精确命中，未命中时可由上层做 ALL 回退）。
     */
    VmsContentTemplateStatic matchTemplate(String controlLevel, String publishPosition, String vehicleType);

    /**
     * 按模板编码删除。
     */
    boolean removeByTemplateCode(String templateCode);
}

