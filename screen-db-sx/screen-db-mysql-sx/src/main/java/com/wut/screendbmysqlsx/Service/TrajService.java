package com.wut.screendbmysqlsx.Service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.Traj;

import java.util.List;

public interface TrajService extends IService<Traj> {
    public List<Traj> getListByEventInterval(String date, long trajId, long timeStart, long timeEnd);

    public List<Traj> getDistinctCarIdList(String date);
    public List<Traj> getDistinctCarIdList(String date, long startTimestamp, long endTimestamp);

    public List<Traj> getDistinctTrajIdList(String date);

    public List<Traj> getListByTrajId(String date, long trajId);
    public List<Traj> getListByTrajId(String date, long trajId, long startTimestamp, long endTimestamp);

    public List<Traj> getListByTrajIdAndTime(String date, long trajId, long timeStart, long timeEnd);

}
