package com.wut.screendbmysqlsx.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wut.screendbmysqlsx.Model.WindData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WindDataMapper extends BaseMapper<WindData> {
    @Insert("""
            INSERT INTO wind_data (
                time_stamp,
                direction,
                start_stake,
                end_stake,
                section_name,
                wind_speed,
                wind_direction,
                heavy_vehicle_speed_limit,
                light_vehicle_speed_limit,
                control_level,
                data_source,
                create_time,
                update_time
            ) VALUES (
                #{row.timeStamp},
                #{row.direction},
                #{row.startStake},
                #{row.endStake},
                #{row.sectionName},
                #{row.windSpeed},
                #{row.windDirection},
                #{row.heavyVehicleSpeedLimit},
                #{row.lightVehicleSpeedLimit},
                #{row.controlLevel},
                #{row.dataSource},
                #{row.createTime},
                #{row.updateTime}
            )
            ON DUPLICATE KEY UPDATE
                section_name = VALUES(section_name),
                wind_speed = VALUES(wind_speed),
                wind_direction = VALUES(wind_direction),
                heavy_vehicle_speed_limit = VALUES(heavy_vehicle_speed_limit),
                light_vehicle_speed_limit = VALUES(light_vehicle_speed_limit),
                control_level = VALUES(control_level),
                update_time = VALUES(update_time)
            """)
    int upsert(@Param("row") WindData row);

    @Select("""
            SELECT id
            FROM wind_data
            WHERE time_stamp = #{timeStamp}
              AND direction = #{direction}
              AND start_stake = #{startStake}
              AND end_stake = #{endStake}
            ORDER BY id ASC
            LIMIT 1
            """)
    Long selectFirstIdByNaturalKey(@Param("timeStamp") LocalDateTime timeStamp,
                                   @Param("direction") Integer direction,
                                   @Param("startStake") String startStake,
                                   @Param("endStake") String endStake);

    @Update("""
            UPDATE wind_data
            SET section_name = #{row.sectionName},
                wind_speed = #{row.windSpeed},
                wind_direction = #{row.windDirection},
                heavy_vehicle_speed_limit = #{row.heavyVehicleSpeedLimit},
                light_vehicle_speed_limit = #{row.lightVehicleSpeedLimit},
                control_level = #{row.controlLevel},
                data_source = #{row.dataSource},
                update_time = #{row.updateTime}
            WHERE id = #{id}
            """)
    int updateDataById(@Param("id") Long id, @Param("row") WindData row);

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
