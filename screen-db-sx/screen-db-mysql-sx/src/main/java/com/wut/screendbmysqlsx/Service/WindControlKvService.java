package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.WindControlKv;

import java.util.List;

public interface WindControlKvService extends IService<WindControlKv> {
    /**
     * 按业务类别查询全部快照。
     */
    List<WindControlKv> getByCategory(String category);

    /**
     * 按类别 + 业务主键查询单条快照。
     */
    WindControlKv getByCategoryAndKey(String category, String itemKey);

    /**
     * 按唯一键写入或更新快照。
     */
    void upsert(String category, String itemKey, String contentJson, long updatedAt);

    /**
     * 删除指定快照。
     */
    boolean deleteByCategoryAndKey(String category, String itemKey);
}
