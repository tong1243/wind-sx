package com.wut.screenwebsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wind_risk_section_hourly")
public class WindRiskSectionHourly {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("time_stamp")
    private LocalDateTime timeStamp;

    @TableField("wind_threshold")
    private BigDecimal windThreshold;

    @TableField("merge_distance_km")
    private BigDecimal mergeDistanceKm;

    @TableField("risk_section_count")
    private Integer riskSectionCount;

    @TableField("risk_sections")
    private String riskSections;

    @TableField("data_source")
    private String dataSource;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
