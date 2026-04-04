package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 路段静态信息实体。
 */
@Data
@TableName("road_segment_static")
public class RoadSegmentStatic {
    /** 主键ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 起点里程（米）。 */
    @TableField("start_location_m")
    private Integer startLocationM;

    /** 起点桩号。 */
    @TableField("start_stake")
    private String startStake;

    /** 终点桩号。 */
    @TableField("end_stake")
    private String endStake;

    /** 行驶方向。 */
    private String direction;

    /** 路段类型。 */
    @TableField("segment_type")
    private String segmentType;

    /** 排序号。 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 是否启用。 */
    @TableField("is_enabled")
    private Integer isEnabled;

    /** 创建时间。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间。 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
