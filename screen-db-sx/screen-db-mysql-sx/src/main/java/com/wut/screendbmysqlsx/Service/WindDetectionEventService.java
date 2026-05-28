package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.WindDetectionEvent;

import java.util.List;

public interface WindDetectionEventService extends IService<WindDetectionEvent> {
    /**
     * 按事件指纹写入或更新检测事件。
     */
    void upsertByFingerprint(WindDetectionEvent event);

    /**
     * 按更新时间倒序查询检测事件。
     */
    List<WindDetectionEvent> getAllOrdered();
}
