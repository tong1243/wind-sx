package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 封路设备静态表实体（4.3.2）。
 */
@Data
@TableName("closure_device_static")
public class ClosureDeviceStatic {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("device_id")
    private String deviceId;

    @TableField("warehouse")
    private String warehouse;

    @TableField("device_type")
    private String deviceType;

    @TableField("quantity")
    private Integer quantity;

    @TableField("available")
    private Integer available;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("is_enabled")
    private Integer isEnabled;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
