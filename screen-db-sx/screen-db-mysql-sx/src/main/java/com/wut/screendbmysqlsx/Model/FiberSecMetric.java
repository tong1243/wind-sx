package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("fibersecmetric")
public class FiberSecMetric {
    @TableField("xsecName")
    private String xsecName;
    @TableField("xsecValue")
    private Double xsecValue;
    @TableField("timeStampStart")
    private Long timestampStart;
    @TableField("timeStampEnd")
    private Long timestampEnd;
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
