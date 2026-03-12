package com.wut.screencommonsx.Response.Event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LanelineResp {
    @JsonProperty("frenetX")
    private Double frenetX;
    private Double longitude;
    private Double latitude;
    private Integer lane;
    @JsonProperty("height")
    private Double height;
}
