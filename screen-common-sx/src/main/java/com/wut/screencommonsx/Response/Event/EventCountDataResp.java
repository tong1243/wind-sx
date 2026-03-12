package com.wut.screencommonsx.Response.Event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventCountDataResp {
    @JsonProperty("sId")
    private int sId;
    @JsonProperty("timestamp")
    private Long timestamp;
    @JsonProperty("count")
    private int count;
}
