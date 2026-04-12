package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 大风风速明细表实体。
 *
 * 对应表：wind_data
 * 用途：承载 4.2 模块的逐小时风速、分段限速与管控等级数据。
 */
@Data
@TableName("wind_data")
public class WindData {
    /** 自增主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 小时时间戳。 */
    @TableField("time_stamp")
    private LocalDateTime timeStamp;

    /** 方向编码：1=吐鲁番方向，2=哈密方向。 */
    @TableField("direction")
    private Integer direction;

    /** 路段起点桩号。 */
    @TableField("start_stake")
    private String startStake;

    /** 路段终点桩号。 */
    @TableField("end_stake")
    private String endStake;

    /** 命中的逐小时限速区段名称，如 k3178-k3183。 */
    @TableField("section_name")
    private String sectionName;

    /** 风速（m/s）。 */
    @TableField("wind_speed")
    private BigDecimal windSpeed;

    /** 风向。 */
    @TableField("wind_direction")
    private String windDirection;

    /** 大车限速（km/h）。 */
    @TableField("heavy_vehicle_speed_limit")
    private Integer heavyVehicleSpeedLimit;

    /** 小车限速（km/h）。 */
    @TableField("light_vehicle_speed_limit")
    private Integer lightVehicleSpeedLimit;

    /** 管控等级（1-5）。 */
    @TableField("control_level")
    private Integer controlLevel;

    /** 数据来源备注。 */
    @TableField("data_source")
    private String dataSource;

    /** 记录创建时间。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 记录更新时间。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
