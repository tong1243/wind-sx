package com.wut.screendbmysqlsx.Model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("wind_control_plan")
/**
 * 管控方案模型。
 * payloadJson 保存完整方案，便于接口原样恢复。
 */
public class WindControlPlan {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("plan_id")
    private String planId;

    @TableField("segment")
    private String segment;

    @TableField("status")
    private String status;

    @TableField("plan_timestamp")
    private Long planTimestamp;

    @TableField("payload_json")
    private String payloadJson;

    @TableField("updated_at")
    private Long updatedAt;
}
