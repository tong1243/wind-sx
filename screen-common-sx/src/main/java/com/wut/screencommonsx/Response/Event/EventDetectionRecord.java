package com.wut.screencommonsx.Response.Event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 事件检测记录。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventDetectionRecord {
    /** 事件ID。 */
    @JsonProperty("eventId")
    private String eventId;

    /** 事件发生时间。 */
    @JsonProperty("time")
    private String time;

    /** 行驶方向。 */
    @JsonProperty("direction")
    private String direction;

    /** 事件位置。 */
    @JsonProperty("eventLocation")
    private String eventLocation;

    /** 事件类型。 */
    @JsonProperty("eventType")
    private String eventType;

    /** 车牌号。 */
    @JsonProperty("vehiclePlateNumber")
    private String vehiclePlateNumber;

    /** 预警状态。 */
    @JsonProperty("warningStatus")
    private String warningStatus;
}
