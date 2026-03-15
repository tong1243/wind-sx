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
}
