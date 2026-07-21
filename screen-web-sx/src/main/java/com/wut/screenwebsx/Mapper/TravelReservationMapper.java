package com.wut.screenwebsx.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wut.screencommonsx.Model.TravelReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 预约信息Mapper
 */
@Mapper
public interface TravelReservationMapper extends BaseMapper<TravelReservation> {
    // 查询用户最新的预约记录
    @Select("SELECT * FROM travel_reservation WHERE user_phone = #{phone} ORDER BY create_time DESC LIMIT 1")
    TravelReservation selectLatestByPhone(@Param("phone") String phone);

    @Select("SELECT * FROM travel_reservation WHERE UPPER(car_license) = UPPER(#{carLicense}) ORDER BY create_time DESC LIMIT 1")
    TravelReservation selectLatestByCarLicense(@Param("carLicense") String carLicense);

    @Select("""
            SELECT COUNT(DISTINCT car_license)
            FROM travel_reservation
            WHERE car_license IS NOT NULL
              AND TRIM(car_license) <> ''
              AND is_passed IN (1, 2)
              AND (expire_time IS NULL OR expire_time >= #{now})
            """)
    long countActiveReservedVehicles(@Param("now") LocalDateTime now);
}
