package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 信息发布设施静态表实体（4.3.1）。
 */
@Data
@TableName("publish_facility_static")
public class PublishFacilityStatic {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("facility_id")
    private String facilityId;

    @TableField("pile_no")
    private String pileNo;

    @TableField("direction")
    private Integer direction;

    @TableField("facility_type")
    private String facilityType;

    @TableField("segment")
    private String segment;

    @TableField("interchange_name")
    private String interchangeName;

    @TableField("interchange_stake")
    private String interchangeStake;

    @TableField("red_alert_message")
    private String redAlertMessage;

    @TableField("color_alert_message")
    private String colorAlertMessage;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("is_enabled")
    private Integer isEnabled;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
