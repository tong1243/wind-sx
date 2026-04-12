package com.wut.screenwebsx.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wut.screencommonsx.Model.NavigationSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NavigationSettlementMapper extends BaseMapper<NavigationSettlement> {

    @Select("SELECT * FROM navigation_settlement WHERE user_phone = #{phone} AND UPPER(car_license) = UPPER(#{carLicense}) ORDER BY navigation_end_time DESC, create_time DESC LIMIT 1")
    NavigationSettlement selectLatestByPhoneAndCar(@Param("phone") String phone,
                                                   @Param("carLicense") String carLicense);
}
