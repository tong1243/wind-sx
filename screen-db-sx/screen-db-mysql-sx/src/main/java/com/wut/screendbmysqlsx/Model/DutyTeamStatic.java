package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 执勤班组静态表实体（4.3.4）。
 */
@Data
@TableName("duty_team_static")
public class DutyTeamStatic {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("team_id")
    private String teamId;

    @TableField("name")
    private String name;

    @TableField("leader_id")
    private String leaderId;

    @TableField("node")
    private String node;

    @TableField("dispatch_state")
    private String dispatchState;

    @TableField("member_ids")
    private String memberIds;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("is_enabled")
    private Integer isEnabled;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
