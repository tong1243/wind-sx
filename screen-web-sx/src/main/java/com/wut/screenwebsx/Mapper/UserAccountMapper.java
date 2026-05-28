package com.wut.screenwebsx.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wut.screencommonsx.Model.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
    @Select("""
            SELECT *
            FROM user_account
            WHERE UPPER(car1_license) = UPPER(#{licensePlate})
               OR UPPER(car2_license) = UPPER(#{licensePlate})
               OR UPPER(car3_license) = UPPER(#{licensePlate})
            LIMIT 1
            """)
    UserAccount selectByAnyLicense(@Param("licensePlate") String licensePlate);
}
