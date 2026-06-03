package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可变信息发布内容模板静态表实体（4.4.3）。
 */
@Data
@TableName("vms_content_template_static")
public class VmsContentTemplateStatic {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("template_code")
    private String templateCode;

    @TableField("control_level")
    private String controlLevel;

    @TableField("publish_position")
    private String publishPosition;

    @TableField("vehicle_type")
    private String vehicleType;

    @TableField("template_text")
    private String templateText;

    @TableField("template_graphic_json")
    private String templateGraphicJson;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("is_enabled")
    private Integer isEnabled;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

