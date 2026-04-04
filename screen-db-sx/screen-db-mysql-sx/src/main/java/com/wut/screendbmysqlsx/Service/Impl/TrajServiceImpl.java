package com.wut.screendbmysqlsx.Service.Impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screendbmysqlsx.Context.TableTimeContext;
import com.wut.screendbmysqlsx.Mapper.TrajMapper;
import com.wut.screendbmysqlsx.Model.Traj;
import com.wut.screendbmysqlsx.Service.TrajService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.wut.screencommonsx.Static.DbModuleStatic.TABLE_SUFFIX_KEY;
import static com.wut.screencommonsx.Static.WebModuleStatic.EVENT_TRAJ_TIME_OFFSET;

/**
 * 轨迹数据服务实现。
 */
@Service
public class TrajServiceImpl extends ServiceImpl<TrajMapper, Traj> implements TrajService {
    /** 轨迹 Mapper。 */
    private final TrajMapper trajMapper;

    @Autowired
    public TrajServiceImpl(TrajMapper trajMapper) {
        this.trajMapper = trajMapper;
    }

    /**
     * 查询事件窗口前后偏移范围轨迹。
     */
    @Override
    public List<Traj> getListByEventInterval(String date, long trajId, long timeStart, long timeEnd) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Traj> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Traj::getTrajId, trajId)
                .ge(Traj::getTimestamp, timeStart - EVENT_TRAJ_TIME_OFFSET)
                .le(Traj::getTimestamp, timeEnd + EVENT_TRAJ_TIME_OFFSET)
                .orderByAsc(Traj::getTimestamp);
        return trajMapper.selectList(wrapper);
    }

    /**
     * 查询当日去重车辆。
     */
    @Override
    public List<Traj> getDistinctCarIdList(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        QueryWrapper<Traj> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT CarId, trajId");
        return trajMapper.selectList(wrapper);
    }

    /**
     * 查询时间范围去重车辆。
     */
    @Override
    public List<Traj> getDistinctCarIdList(String date, long startTimestamp, long endTimestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        QueryWrapper<Traj> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT CarId, trajId");
        if (startTimestamp != 0L) { wrapper.ge("timestamp", startTimestamp); }
        if (endTimestamp != 0L) { wrapper.le("timestamp", endTimestamp); }
        return trajMapper.selectList(wrapper);
    }

    /**
     * 查询当日去重轨迹ID。
     */
    @Override
    public List<Traj> getDistinctTrajIdList(String date) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        QueryWrapper<Traj> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT trajId");
        return trajMapper.selectList(wrapper);
    }

    /**
     * 查询指定轨迹全部点位。
     */
    @Override
    public List<Traj> getListByTrajId(String date, long trajId) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Traj> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Traj::getTrajId, trajId)
                .orderByAsc(Traj::getTimestamp);
        return trajMapper.selectList(wrapper);
    }

    /**
     * 查询指定轨迹在时间范围内点位。
     */
    @Override
    public List<Traj> getListByTrajId(String date, long trajId, long startTimestamp, long endTimestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Traj> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Traj::getTrajId, trajId);
        if (startTimestamp != 0L) { wrapper.ge(Traj::getTimestamp, startTimestamp); }
        if (endTimestamp != 0L) { wrapper.le(Traj::getTimestamp, endTimestamp); }
        wrapper.orderByAsc(Traj::getTimestamp);
        return trajMapper.selectList(wrapper);
    }

    /**
     * 按轨迹ID与时间范围查询点位。
     */
    @Override
    public List<Traj> getListByTrajIdAndTime(String date, long trajId, long timeStart, long timeEnd) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Traj> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Traj::getTrajId, trajId)
                .ge(Traj::getTimestamp, timeStart)
                .le(Traj::getTimestamp, timeEnd)
                .orderByAsc(Traj::getTimestamp);
        return trajMapper.selectList(wrapper);
    }

    /**
     * 按时间范围查询轨迹点。
     */
    @Override
    public List<Traj> getListByTimestampRange(String date, long startTimestamp, long endTimestamp) {
        TableTimeContext.setTime(TABLE_SUFFIX_KEY, date);
        LambdaQueryWrapper<Traj> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Traj::getTimestamp, startTimestamp)
                .le(Traj::getTimestamp, endTimestamp)
                .orderByAsc(Traj::getTrajId)
                .orderByAsc(Traj::getTimestamp);
        return trajMapper.selectList(wrapper);
    }

}
