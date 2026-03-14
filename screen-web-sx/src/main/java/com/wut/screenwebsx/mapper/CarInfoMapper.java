package com.wut.screenwebsx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wut.screencommonsx.Model.CarInfo;
import com.wut.screencommonsx.Model.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CarInfoMapper extends BaseMapper<CarInfo> {
    // 根据用户手机号查询绑定车辆
    List<CarInfo> selectByUserPhone(@Param("phone") String phone);
}