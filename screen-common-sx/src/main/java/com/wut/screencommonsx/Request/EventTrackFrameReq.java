package com.wut.screencommonsx.Request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventTrackFrameReq {
    @JsonProperty("timestamp")
    private long timestamp;
    @JsonProperty("trajId")
    private long trajId;
    @JsonProperty("type")
    private int type;
    @JsonProperty("eventId")
    private String eventId;
}
