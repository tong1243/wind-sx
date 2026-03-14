package com.wut.screencommonsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_app_select")
public class SysAppSelect {
    @TableId(type = IdType.AUTO)
    private Integer id;         // 配置ID
    private String type;        // 选择类型：start_point/end_point/car_type/driving_type
    private String code;        // 选项编码
    private String name;        // 选项名称
    private Integer sort;       // 排序号
    private Integer isEnable;   // 是否启用：1-是 0-否
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}