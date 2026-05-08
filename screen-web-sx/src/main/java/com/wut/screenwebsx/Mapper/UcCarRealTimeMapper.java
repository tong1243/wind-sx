package com.wut.screenwebsx.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wut.screencommonsx.Model.UcCarRealTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * UC实时推送车辆数据Mapper
 */
@Mapper
public interface UcCarRealTimeMapper extends BaseMapper<UcCarRealTime> {
    // 查询用户最新的车辆实时数据
    @Select("SELECT * FROM uc_car_real_time WHERE user_phone = #{phone} ORDER BY report_time DESC LIMIT 1")
    UcCarRealTime selectLatestByPhone(@Param("phone") String phone);

    @Delete("DELETE FROM uc_car_real_time " +
            "WHERE user_phone = #{phone} " +
            "AND id NOT IN ( " +
            "  SELECT id FROM ( " +
            "    SELECT id FROM uc_car_real_time " +
            "    WHERE user_phone = #{phone} " +
            "    ORDER BY report_time DESC, id DESC " +
            "    LIMIT 1 " +
            "  ) latest " +
            ")")
    int clearHistoryByPhoneKeepLatest(@Param("phone") String phone);

    @Delete("DELETE FROM uc_car_real_time")
    int clearAll();

    @Select({
            "<script>",
            "SELECT COUNT(1) FROM uc_car_real_time_current",
            "WHERE 1 = 1",
            "<if test='vehicleId != null and vehicleId != \"\"'>",
            "  AND user_phone LIKE CONCAT('%', #{vehicleId}, '%')",
            "</if>",
            "<if test='licensePlate != null and licensePlate != \"\"'>",
            "  AND car_license LIKE CONCAT('%', #{licensePlate}, '%')",
            "</if>",
            "<if test='directionCode != null'>",
            "  AND direction = #{directionCode}",
            "</if>",
            "<if test='directionCode == null and directionText != null and directionText != \"\"'>",
            "  AND driving_direction = #{directionText}",
            "</if>",
            "</script>"
    })
    long countCurrentForOperation(@Param("vehicleId") String vehicleId,
                                  @Param("licensePlate") String licensePlate,
                                  @Param("directionCode") Integer directionCode,
                                  @Param("directionText") String directionText);

    @Select({
            "<script>",
            "SELECT",
            "  id,",
            "  user_phone AS userPhone,",
            "  car_license AS carLicense,",
            "  current_pile AS currentPile,",
            "  real_speed AS realSpeed,",
            "  direction,",
            "  driving_direction AS drivingDirection,",
            "  lane_number AS laneNumber,",
            "  road,",
            "  report_time AS reportTime",
            "FROM uc_car_real_time_current",
            "WHERE 1 = 1",
            "<if test='vehicleId != null and vehicleId != \"\"'>",
            "  AND user_phone LIKE CONCAT('%', #{vehicleId}, '%')",
            "</if>",
            "<if test='licensePlate != null and licensePlate != \"\"'>",
            "  AND car_license LIKE CONCAT('%', #{licensePlate}, '%')",
            "</if>",
            "<if test='directionCode != null'>",
            "  AND direction = #{directionCode}",
            "</if>",
            "<if test='directionCode == null and directionText != null and directionText != \"\"'>",
            "  AND driving_direction = #{directionText}",
            "</if>",
            "ORDER BY report_time DESC, id DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    java.util.List<UcCarRealTime> selectCurrentPageForOperation(@Param("offset") long offset,
                                                                 @Param("limit") long limit,
                                                                 @Param("vehicleId") String vehicleId,
                                                                 @Param("licensePlate") String licensePlate,
                                                                 @Param("directionCode") Integer directionCode,
                                                                 @Param("directionText") String directionText);

    @Select("SELECT id, user_phone AS userPhone, car_license AS carLicense, current_pile AS currentPile, " +
            "real_speed AS realSpeed, direction, driving_direction AS drivingDirection, lane_number AS laneNumber, " +
            "road, report_time AS reportTime " +
            "FROM uc_car_real_time_current WHERE user_phone = #{phone} ORDER BY report_time DESC, id DESC LIMIT 1")
    UcCarRealTime selectLatestByPhoneFromCurrent(@Param("phone") String phone);
}
