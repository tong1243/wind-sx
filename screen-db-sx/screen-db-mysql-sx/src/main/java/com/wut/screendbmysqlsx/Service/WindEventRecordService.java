package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.WindEventRecord;

import java.util.List;

public interface WindEventRecordService extends IService<WindEventRecord> {
    /**
     * 按 eventId 写入或更新事件。
     */
    void upsertByEventId(WindEventRecord record);

    /**
     * 按更新时间倒序查询全部事件。
     */
    List<WindEventRecord> getAllOrdered();
}
