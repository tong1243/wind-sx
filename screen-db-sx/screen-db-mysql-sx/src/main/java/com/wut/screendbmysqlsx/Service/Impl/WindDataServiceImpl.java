package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.WindDataMapper;
import com.wut.screendbmysqlsx.Model.WindData;
import com.wut.screendbmysqlsx.Service.WindDataService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 大风数据服务实现。
 */
@Service
public class WindDataServiceImpl extends ServiceImpl<WindDataMapper, WindData> implements WindDataService {
    /** 大风数据 Mapper。 */
    private final WindDataMapper windDataMapper;

    public WindDataServiceImpl(WindDataMapper windDataMapper) {
        this.windDataMapper = windDataMapper;
    }

    /**
     * 查询最新快照。
     *
     * @param timestamp 截止时间
     * @return 快照列表
     */
    @Override
    public List<WindData> listLatestSnapshot(LocalDateTime timestamp) {
        if (timestamp == null) {
            return Collections.emptyList();
        }
        List<WindData> rows = windDataMapper.selectLatestSnapshot(timestamp);
        return rows == null ? Collections.emptyList() : rows;
    }

    /**
     * 查询时间范围明细。
     *
     * @param start 起始时间（含）
     * @param end 结束时间（含）
     * @return 风数据明细
     */
    @Override
    public List<WindData> listByTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<WindData> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(WindData::getTimeStamp, start)
                .le(WindData::getTimeStamp, end)
                .orderByAsc(WindData::getTimeStamp)
                .orderByAsc(WindData::getDirection)
                .orderByAsc(WindData::getStartStake)
                .orderByAsc(WindData::getEndStake)
                .orderByAsc(WindData::getId);
        List<WindData> rows = windDataMapper.selectList(wrapper);
        return rows == null ? Collections.emptyList() : rows;
    }
}

