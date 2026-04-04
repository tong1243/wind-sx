package com.wut.screendbmysqlsx.Service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screendbmysqlsx.Model.Traj;

import java.util.List;

/**
 * 轨迹数据服务接口。
 */
public interface TrajService extends IService<Traj> {
    /**
     * 查询事件时间窗口附近的轨迹点。
     */
    public List<Traj> getListByEventInterval(String date, long trajId, long timeStart, long timeEnd);

    /**
     * 查询当日去重车辆列表。
     */
    public List<Traj> getDistinctCarIdList(String date);
    /**
     * 查询时间范围内去重车辆列表。
     */
    public List<Traj> getDistinctCarIdList(String date, long startTimestamp, long endTimestamp);

    /**
     * 查询当日去重轨迹ID列表。
     */
    public List<Traj> getDistinctTrajIdList(String date);

    /**
     * 按轨迹ID查询全部轨迹点。
     */
    public List<Traj> getListByTrajId(String date, long trajId);
    /**
     * 按轨迹ID和时间范围查询轨迹点。
     */
    public List<Traj> getListByTrajId(String date, long trajId, long startTimestamp, long endTimestamp);

    /**
     * 按轨迹ID和起止时间查询轨迹点。
     */
    public List<Traj> getListByTrajIdAndTime(String date, long trajId, long timeStart, long timeEnd);

    /**
     * 按时间范围查询轨迹点。
     */
    public List<Traj> getListByTimestampRange(String date, long startTimestamp, long endTimestamp);

}
