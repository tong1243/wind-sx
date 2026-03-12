package com.wut.screencommonsx.Request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RadarTargetDataReq {
    private long timestamp;
    private int rid;
    @JsonProperty("dateTarget")
    private String dateTarget;
    @JsonProperty("timeStartTarget")
    private String timeStartTarget;
    @JsonProperty("timeEndTarget")
    private String timeEndTarget;
}
