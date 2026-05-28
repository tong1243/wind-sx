package com.wut.screendbmysqlsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Mapper.WindDetectionEventMapper;
import com.wut.screendbmysqlsx.Model.WindDetectionEvent;
import com.wut.screendbmysqlsx.Service.WindDetectionEventService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/**
 * 4.1 事件检测持久化服务实现。
 */
public class WindDetectionEventServiceImpl extends ServiceImpl<WindDetectionEventMapper, WindDetectionEvent>
        implements WindDetectionEventService {
    private final WindDetectionEventMapper windDetectionEventMapper;

    public WindDetectionEventServiceImpl(WindDetectionEventMapper windDetectionEventMapper) {
        this.windDetectionEventMapper = windDetectionEventMapper;
    }

    @Override
    public void upsertByFingerprint(WindDetectionEvent event) {
        LambdaQueryWrapper<WindDetectionEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WindDetectionEvent::getEventFingerprint, event.getEventFingerprint());
        WindDetectionEvent existing = windDetectionEventMapper.selectOne(wrapper);
        if (existing == null) {
            windDetectionEventMapper.insert(event);
            return;
        }
        event.setId(existing.getId());
        windDetectionEventMapper.updateById(event);
    }

    @Override
    public List<WindDetectionEvent> getAllOrdered() {
        LambdaQueryWrapper<WindDetectionEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(WindDetectionEvent::getUpdatedAt);
        return windDetectionEventMapper.selectList(wrapper);
    }
}
