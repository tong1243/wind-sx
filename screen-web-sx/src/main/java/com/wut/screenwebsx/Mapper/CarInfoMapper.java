package com.wut.screenwebsx.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wut.screencommonsx.Model.CarInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CarInfoMapper extends BaseMapper<CarInfo> {
    List<CarInfo> selectByUserPhone(@Param("phone") String phone);

    @Select("SELECT * FROM car_info WHERE UPPER(license_plate) = UPPER(#{licensePlate}) LIMIT 1 FOR UPDATE")
    CarInfo selectByLicensePlateForUpdate(@Param("licensePlate") String licensePlate);
}
