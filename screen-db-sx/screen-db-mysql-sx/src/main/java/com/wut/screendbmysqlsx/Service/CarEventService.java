package com.wut.screendbmysqlsx.Service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.CarEvent;

import java.util.List;

public interface CarEventService extends IService<CarEvent> {
    public List<CarEvent> getListByDate(String date);
    public List<CarEvent> getListByTarget(String date, long startTimestamp, long endTimestamp);

    public CarEvent getOneByUuid(String date, long uuid);

    public void updateOneByUuid(String date, CarEvent event);

}
