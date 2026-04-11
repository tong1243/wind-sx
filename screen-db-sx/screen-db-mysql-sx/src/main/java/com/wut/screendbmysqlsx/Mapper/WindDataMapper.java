package com.wut.screendbmysqlsx.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wut.screendbmysqlsx.Model.WindData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大风数据 Mapper。
 */
@Mapper
public interface WindDataMapper extends BaseMapper<WindData> {
    /**
     * 查询某时刻之前（含）每个“方向+桩号区间”的最新一条风数据。
     *
     * @param ts 截止时间
     * @return 最新快照列表
     */
    @Select("""
            SELECT w.*
            FROM wind_data w
            INNER JOIN (
                SELECT direction, start_stake, end_stake, MAX(time_stamp) AS max_time
                FROM wind_data
                WHERE time_stamp <= #{ts}
                GROUP BY direction, start_stake, end_stake
            ) t
              ON w.direction = t.direction
             AND w.start_stake = t.start_stake
             AND w.end_stake = t.end_stake
             AND w.time_stamp = t.max_time
            """)
    List<WindData> selectLatestSnapshot(@Param("ts") LocalDateTime ts);
}

