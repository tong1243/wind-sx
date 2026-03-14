package com.wut.screencommonsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("travel_reservation")
public class TravelReservation {
    @TableId(type = IdType.AUTO)
    private Long id;             // 预约ID（自增主键）
    private String userPhone;    // 用户手机号（关联用户表）
    private String carLicense;   // 车辆号牌（关联车辆表）
    private String startPoint;   // 起点
    private String endPoint;     // 终点（不可与起点相同）
    private String travelTimeSlot; // 出行时段：YYYY-MM-DD HH:MM~HH:MM
    private String carType;      // 车型
    private BigDecimal cargoWeight; // 货物重量（吨）
    private Integer isPassed;    // 是否通过预约：1-是 0-否
    private String rejectReason; // 驳回原因
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime expireTime; // 预约失效时间
}