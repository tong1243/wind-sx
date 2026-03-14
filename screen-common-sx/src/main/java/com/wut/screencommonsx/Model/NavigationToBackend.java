package com.wut.screencommonsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("navigation_to_backend")
public class NavigationToBackend {
    @TableId(type = IdType.AUTO)
    private Long id;             // 记录ID
    private String userPhone;    // 用户手机号
    private String carLicense;   // 车辆号牌
    private String currentPile;  // 当前位置桩号
    private Integer realSpeed;   // 实时车速
    private LocalDateTime reportTime; // 上报时间
}