package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.WindEventRecordMapper;
import com.wut.screendbmysqlsx.Model.WindEventRecord;
import com.wut.screendbmysqlsx.Service.WindEventRecordService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/**
 * 风事件记录服务实现。
 */
public class WindEventRecordServiceImpl extends ServiceImpl<WindEventRecordMapper, WindEventRecord> implements WindEventRecordService {
    private final WindEventRecordMapper windEventRecordMapper;

    public WindEventRecordServiceImpl(WindEventRecordMapper windEventRecordMapper) {
        this.windEventRecordMapper = windEventRecordMapper;
    }

    @Override
    public void upsertByEventId(WindEventRecord record) {
        // 事件按 event_id 幂等更新，重复发布不会产生脏重复数据。
        LambdaQueryWrapper<WindEventRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WindEventRecord::getEventId, record.getEventId());
        WindEventRecord existing = windEventRecordMapper.selectOne(wrapper);
        if (existing == null) {
            windEventRecordMapper.insert(record);
            return;
        }
        record.setId(existing.getId());
        windEventRecordMapper.updateById(record);
    }

    @Override
    public List<WindEventRecord> getAllOrdered() {
        LambdaQueryWrapper<WindEventRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(WindEventRecord::getUpdatedAt);
        return windEventRecordMapper.selectList(wrapper);
    }
}
