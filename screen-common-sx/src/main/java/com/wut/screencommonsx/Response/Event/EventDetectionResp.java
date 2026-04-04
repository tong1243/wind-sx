package com.wut.screencommonsx.Response.Event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 事件检测接口响应。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventDetectionResp {
    /** 查询时间戳。 */
    @JsonProperty("timestamp")
    private long timestamp;

    /** 事件检测记录列表。 */
    @JsonProperty("recordList")
    private List<EventDetectionRecord> recordList;
}
