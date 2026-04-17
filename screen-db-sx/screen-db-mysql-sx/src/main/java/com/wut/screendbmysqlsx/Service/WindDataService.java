package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.WindData;

import java.time.LocalDateTime;
import java.util.List;

public interface WindDataService extends IService<WindData> {
    boolean upsert(WindData row);

    int upsertBatch(List<WindData> rows);

    List<WindData> listLatestSnapshot(LocalDateTime timestamp);

    List<WindData> listByTimeRange(LocalDateTime start, LocalDateTime end);
}
