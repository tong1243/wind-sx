package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 限速阈值静态表实体。
 */
@Data
@TableName("speed_threshold_static")
public class SpeedThresholdStatic {
    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 管控等级名称（如：一级、二级、五级）。
     */
    @TableField("control_level_name")
    private String controlLevelName;

    /**
     * 风力等级描述。
     */
    @TableField("wind_level_desc")
    private String windLevelDesc;

    /**
     * 最小风级（可为空）。
     */
    @TableField("min_wind_level")
    private Integer minWindLevel;

    /**
     * 最大风级（可为空）。
     */
    @TableField("max_wind_level")
    private Integer maxWindLevel;

    /**
     * 小客车限速（km/h）。
     */
    @TableField("light_vehicle_speed_limit")
    private Integer lightVehicleSpeedLimit;

    /**
     * 客货车限速（km/h）。
     */
    @TableField("heavy_vehicle_speed_limit")
    private Integer heavyVehicleSpeedLimit;

    /**
     * 排序号。
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 是否启用（1 启用，0 停用）。
     */
    @TableField("is_enabled")
    private Integer isEnabled;

    /**
     * 创建时间。
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
