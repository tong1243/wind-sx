package com.wut.screencommonsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("uc_car_real_time")
public class UcCarRealTime {
    @TableId(type = IdType.AUTO)
    private Long id;             // 记录ID
    private String userPhone;    // 用户手机号
    private String carLicense;   // 车辆号牌
    private String currentPile;  // 当前位置桩号
    private Integer realSpeed;   // 实时车速（km/h）
    private String drivingDirection; // 行驶方向：hamimi_to_tuyugou/tuyugou_to_hamimi
    private Integer laneNumber;  // 车道编号（1-4）
    private LocalDateTime reportTime; // 上报时间
}