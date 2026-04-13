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
    private Long id;
    private String userPhone;
    private String carLicense;
    private String currentPile;
    private Integer realSpeed;
    /**
     * Direction code: 1=Turpan, 2=Hami.
     */
    private Integer direction;
    /**
     * Legacy text direction, kept for compatibility.
     */
    private String drivingDirection;
    private Integer laneNumber;
    private LocalDateTime reportTime;
}

