package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管控区间静态表实体。
 */
@Data
@TableName("control_interval_static")
public class ControlIntervalStatic {
    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 区间名称。
     */
    @TableField("interval_name")
    private String intervalName;

    /**
     * 区间起点桩号。
     */
    @TableField("start_stake")
    private String startStake;

    /**
     * 区间终点桩号。
     */
    @TableField("end_stake")
    private String endStake;

    /**
     * 区间起点位置（米）。
     */
    @TableField("segment_start_location_m")
    private Integer segmentStartLocationM;

    /**
     * 区间终点位置（米）。
     */
    @TableField("segment_end_location_m")
    private Integer segmentEndLocationM;

    /**
     * 行驶方向。
     */
    @TableField("direction")
    private String direction;

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
