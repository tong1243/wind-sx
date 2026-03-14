package com.wut.screencommonsx.Model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("car_info")
public class CarInfo {
    @TableId
    private String licensePlate;        // 车牌号（主键）
    private String carName;             // 车辆名称（1-6位）
    private String carType;             // 车辆类型
    private String owner;               // 所有人
    private String usageNature;         // 使用性质：non_operating/operating
    private String brandModel;          // 品牌型号
    private String vin;                 // 车架号（17位）
    private String engineNumber;        // 发动机号
    private LocalDate registerDate;     // 注册日期
    private String licensePhotoUrl;     // 行驶证照片URL
    private String auditStatus;         // 审核状态：unaudited/passed/rejected
    private String rejectReason;        // 驳回原因
    private Integer currentPoints;      // 当前积分（0-12）
    private Integer pointsIncreaseRemainTime; // 积分上涨剩余时间（秒）
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}