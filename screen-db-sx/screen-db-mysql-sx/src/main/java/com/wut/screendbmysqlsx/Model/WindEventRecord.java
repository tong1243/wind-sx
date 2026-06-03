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
@TableName("wind_event_record")
/**
 * 大风事件记录模型。
 * payloadJson 保存事件扩展字段，结构变化时无需改表。
 */
public class WindEventRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("event_id")
    private String eventId;

    @TableField("start_time")
    private String startTime;

    @TableField("segment")
    private String segment;

    @TableField("incident_location")
    private String incidentLocation;

    @TableField("wind_speed_scale")
    private String windSpeedScale;

    @TableField("management_plan")
    private String managementPlan;

    @TableField("occurrence_time")
    private String occurrenceTime;

    @TableField("conclusion_time")
    private String conclusionTime;

    @TableField("control_perimeter")
    private String controlPerimeter;

    @TableField("on_duty_personnel")
    private String onDutyPersonnel;

    @TableField("direction")
    private String direction;

    @TableField("max_wind_level")
    private Integer maxWindLevel;

    @TableField("control_level")
    private Integer controlLevel;

    @TableField("duration_min")
    private Integer durationMin;

    @TableField("status")
    private String status;

    @TableField("payload_json")
    private String payloadJson;

    @TableField("updated_at")
    private Long updatedAt;
}
