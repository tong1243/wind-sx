package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.BottleneckAreaState;
import com.wut.screendbmysqlsx.Model.Posture;

import java.util.List;

public interface BottleneckAreaStateService  extends IService<BottleneckAreaState> {
    public List<BottleneckAreaState> getListByDate(String date);
    public List<BottleneckAreaState> getListByTarget(String date, long startTimestamp, long endTimestamp);
    public BottleneckAreaState getLatestOne(String date);
}
