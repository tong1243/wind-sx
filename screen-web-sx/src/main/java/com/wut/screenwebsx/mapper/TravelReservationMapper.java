package com.wut.screenwebsx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wut.screencommonsx.Model.TravelReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 预约信息Mapper
 */
@Mapper
public interface TravelReservationMapper extends BaseMapper<TravelReservation> {
    // 查询用户最新的预约记录
    @Select("SELECT * FROM travel_reservation WHERE user_phone = #{phone} ORDER BY create_time DESC LIMIT 1")
    TravelReservation selectLatestByPhone(@Param("phone") String phone);
}