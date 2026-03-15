package com.wut.screenwebsx.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wut.screencommonsx.Model.UcCarRealTime;
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
}