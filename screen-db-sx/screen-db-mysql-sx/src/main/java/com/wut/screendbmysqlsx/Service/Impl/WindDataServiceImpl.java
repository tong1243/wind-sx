package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.WindDataMapper;
import com.wut.screendbmysqlsx.Model.WindData;
import com.wut.screendbmysqlsx.Service.WindDataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class WindDataServiceImpl extends ServiceImpl<WindDataMapper, WindData> implements WindDataService {
    private final WindDataMapper windDataMapper;

    public WindDataServiceImpl(WindDataMapper windDataMapper) {
        this.windDataMapper = windDataMapper;
    }

    @Override
    public boolean upsert(WindData row) {
        if (row == null) {
            return false;
        }
        return windDataMapper.upsert(row) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int upsertBatch(List<WindData> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (WindData row : rows) {
            if (row == null) {
                continue;
            }
            count += windDataMapper.upsert(row);
        }
        return count;
    }

    @Override
    public List<WindData> listLatestSnapshot(LocalDateTime timestamp) {
        if (timestamp == null) {
            return Collections.emptyList();
        }
        List<WindData> rows = windDataMapper.selectLatestSnapshot(timestamp);
        return rows == null ? Collections.emptyList() : rows;
    }

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
