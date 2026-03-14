package com.wut.screencommonsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_app_storage")
public class SysAppStorage {
    @TableId(type = IdType.AUTO)
    private Integer id;         // 配置ID
    private String type;        // 存储类型：node_pile/lane_rule/speed_limit_rule
    private String configKey;   // 配置键
    private String configValue; // 配置值（文本）
    private Integer sort;       // 排序号
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}