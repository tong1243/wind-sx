package com.wut.screencommonsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_province")
public class SysProvince {
    @TableId(type = IdType.AUTO)
    private Integer id;         // 省份ID（tinyint unsigned）
    private String provinceCode;// 省份简称（如粤、琼）
    private String provinceName;// 省份全称
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}