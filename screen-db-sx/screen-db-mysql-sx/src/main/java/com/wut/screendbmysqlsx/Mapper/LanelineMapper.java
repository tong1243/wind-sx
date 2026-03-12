package com.wut.screendbmysqlsx.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wut.screendbmysqlsx.Model.Laneline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LanelineMapper extends BaseMapper<Laneline> {
    @Select("""
    SELECT *
    FROM (
        SELECT *,
               FLOOR((frenetX - #{startFrenetX}) / 5) AS interval_index
        FROM laneline
        WHERE frenetX BETWEEN #{startFrenetX} AND #{endFrenetX}
          AND lane = #{lane}
    ) AS subquery
    GROUP BY interval_index
""")
    List<Laneline> getLanelineByInterval(@Param("startFrenetX") double startFrenetX, @Param("endFrenetX") double endFrenetX, @Param("lane") int lane);
}
