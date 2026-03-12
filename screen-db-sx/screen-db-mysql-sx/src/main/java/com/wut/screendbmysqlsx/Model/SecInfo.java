package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("secinfo")
// 断面选择支
public class SecInfo {
    @TableId(type = IdType.INPUT)
    private Integer sid;
    @TableField("xsecName")
    private String xsecName;
    @TableField("xsecValue")
    private Double xsecValue;
}
