package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 执勤人员静态表实体（4.3.3）。
 */
@Data
@TableName("duty_staff_static")
public class DutyStaffStatic {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("staff_id")
    private String staffId;

    @TableField("name")
    private String name;

    @TableField("on_duty")
    private Integer onDuty;

    @TableField("team_id")
    private String teamId;

    @TableField("phone")
    private String phone;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("is_enabled")
    private Integer isEnabled;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
