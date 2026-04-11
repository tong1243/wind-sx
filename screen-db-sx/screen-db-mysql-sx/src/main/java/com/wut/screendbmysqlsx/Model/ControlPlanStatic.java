package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管控预案静态表实体。
 */
@Data
@TableName("control_plan_static")
public class ControlPlanStatic {
    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 管控等级名称（如：一级管控、二级管控）。
     */
    @TableField("control_level_name")
    private String controlLevelName;

    /**
     * 风力等级描述。
     */
    @TableField("wind_level_desc")
    private String windLevelDesc;

    /**
     * 风险区段内方案。
     */
    @TableField("risk_section_plan")
    private String riskSectionPlan;

    /**
     * 上游出口方案。
     */
    @TableField("upstream_exit_plan")
    private String upstreamExitPlan;

    /**
     * 上游入口方案。
     */
    @TableField("upstream_entry_plan")
    private String upstreamEntryPlan;

    /**
     * 上游服务区方案。
     */
    @TableField("upstream_service_area_plan")
    private String upstreamServiceAreaPlan;

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
