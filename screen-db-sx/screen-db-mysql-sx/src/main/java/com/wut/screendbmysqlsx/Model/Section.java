package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 断面信息
// 对应表名:section_{time}
@TableName("section")
public class Section {
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
    @TableField("avgVez")
    private Double avgVez;
    @TableField("avgKez")
    private Double avgKez;
    @TableField("avgQwh")
    private Double avgQwh;
    @TableField("avgVwh")
    private Double avgVwh;
    @TableField("avgKwh")
    private Double avgKwh;
}
