package com.wut.screenwebsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wind_speed_limit_hourly")
public class WindSpeedLimitHourly {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("time_stamp")
    private LocalDateTime timeStamp;

    @TableField("direction")
    private Integer direction;

    @TableField("section_order")
    private Integer sectionOrder;

    @TableField("section_name")
    private String sectionName;

    @TableField("section_start_km")
    private BigDecimal sectionStartKm;

    @TableField("section_end_km")
    private BigDecimal sectionEndKm;

    @TableField("max_wind_speed")
    private BigDecimal maxWindSpeed;

    @TableField("control_level")
    private Integer controlLevel;

    @TableField("level_desc")
    private String levelDesc;

    @TableField("car_speed_limit")
    private Integer carSpeedLimit;

    @TableField("truck_speed_limit")
    private Integer truckSpeedLimit;

    @TableField("note")
    private String note;

    @TableField("data_source")
    private String dataSource;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
