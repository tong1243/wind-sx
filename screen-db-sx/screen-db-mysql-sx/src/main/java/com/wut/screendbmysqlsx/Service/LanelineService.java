package com.wut.screendbmysqlsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.Laneline;

import java.util.List;

public interface LanelineService extends IService<Laneline> {
    public List<Laneline> getListByFrenetXAndLane(double frenetX, int lane);
}
