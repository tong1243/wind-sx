package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.WindData;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大风数据服务接口。
 */
public interface WindDataService extends IService<WindData> {
    /**
     * 查询某时刻之前（含）的最新风场快照（按方向+桩号区间去重）。
     *
     * @param timestamp 截止时间
     * @return 快照记录列表
     */
    List<WindData> listLatestSnapshot(LocalDateTime timestamp);

    /**
     * 查询时间范围内的风数据明细。
     *
     * @param start 起始时间（含）
     * @param end 结束时间（含）
     * @return 风数据列表
     */
    List<WindData> listByTimeRange(LocalDateTime start, LocalDateTime end);
}

