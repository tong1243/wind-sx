package com.wut.screencommonsx.Response.Posture;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OriginalDataResp {
    @JsonProperty("timestamp")
    private long timestamp;
    @JsonProperty("botAreaTravelTime")
    private double botAreaTravelTime;
    @JsonProperty("accAreaTravelTime")
    private double accAreaTravelTime;
    @JsonProperty("botAvgSpeed")
    private double botAvgSpeed;
    @JsonProperty("accAvgSpeed")
    private double accAvgSpeed;
    @JsonProperty("botAvgDelay")
    private double botAvgDelay;
    @JsonProperty("accAvgDelay")
    private double accAvgDelay;
}
