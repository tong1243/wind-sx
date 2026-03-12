package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("radarmetric")
public class RadarMetric {
    @TableField("timeStampStart")
    private Long timestampStart;
    @TableField("timeStampEnd")
    private Long timestampEnd;
    private Integer rid;
    private String ip;
    private Integer type;
    private Integer direction;
    @TableField("avgQez")
    private Double avgQez;
    @TableField("avgQwh")
    private Double avgQwh;
    @TableField("avgVez")
    private Double avgVez;
    @TableField("avgVwh")
    private Double avgVwh;
    @TableField("avgTimeout")
    private Long avgTimeout;
}
