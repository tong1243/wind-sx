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
@TableName("wind_detection_event")
/**
 * 4.1 事件检测持久化模型。
 *
 * eventFingerprint 用于幂等去重，payloadJson 保存扩展字段。
 */
public class WindDetectionEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("event_fingerprint")
    private String eventFingerprint;

    @TableField("event_id")
    private String eventId;

    @TableField("event_type")
    private String eventType;

    @TableField("segment")
    private String segment;

    @TableField("vehicle_plate")
    private String vehiclePlate;

    @TableField("threshold_speed_km_per_hour")
    private Integer thresholdSpeedKmPerHour;

    @TableField("status")
    private String status;

    @TableField("event_timestamp")
    private Long eventTimestamp;

    @TableField("payload_json")
    private String payloadJson;

    @TableField("updated_at")
    private Long updatedAt;
}
