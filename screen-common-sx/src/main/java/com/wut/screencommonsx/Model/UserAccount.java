package com.wut.screencommonsx.Model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_account")
public class UserAccount {
    @TableId
    private String phone;               // 手机号（主键，11位）
    private String emergencyContact;    // 紧急联系人手机号
    private String password;            // 加密密码（varchar64）
    private String car1License;         // 车辆1号牌
    private String car2License;         // 车辆2号牌
    private String car3License;         // 车辆3号牌
    private String nickname;            // 用户昵称（默认手机号）
    private Integer status;             // 账号状态：1-正常 0-注销
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime deleteTime;   // 注销时间
}