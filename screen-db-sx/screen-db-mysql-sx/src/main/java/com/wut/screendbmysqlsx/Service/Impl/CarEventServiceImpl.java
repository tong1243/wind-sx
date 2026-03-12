package com.wut.screendbmysqlsx.Service.Impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.CarEventMapper;
import com.wut.screendbmysqlsx.Model.CarEvent;
import com.wut.screendbmysqlsx.Service.CarEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;

@Service
public class CarEventServiceImpl extends ServiceImpl<CarEventMapper, CarEvent> implements CarEventService {
    private final CarEventMapper carEventMapper;

    @Autowired
    public CarEventServiceImpl(CarEventMapper carEventMapper) {
        this.carEventMapper = carEventMapper;
    }

    @Override
    public List<CarEvent> getListByDate(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<CarEvent> wrapper = new LambdaQueryWrapper<>();
        return carEventMapper.selectList(wrapper);
    }

    @Override
    public List<CarEvent> getListByTarget(String date, long startTimestamp, long endTimestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<CarEvent> wrapper = new LambdaQueryWrapper<>();
        if (startTimestamp != 0L) { wrapper.ge(CarEvent::getStartTimestamp, startTimestamp); }
        if (endTimestamp != 0L) { wrapper.le(CarEvent::getStartTimestamp, endTimestamp); }
        wrapper.orderByAsc(CarEvent::getStartTimestamp);
        return carEventMapper.selectList(wrapper);
    }

    @Override
    public CarEvent getOneByUuid(String date, long uuid) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<CarEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CarEvent::getUuid, uuid);
        return carEventMapper.selectOne(wrapper);
    }

    @Override
    public void updateOneByUuid(String date, CarEvent event) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaUpdateWrapper<CarEvent> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CarEvent::getUuid, event.getUuid());
        carEventMapper.update(event, wrapper);
    }

}
