package com.wut.screencommonsx.Response.Posture;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LimitSpeedResp {
    @JsonProperty("rampGreenLightTiming")
    private int rampGreenLightTiming;
    @JsonProperty("rampSignalCycle")
    private int rampSignalCycle;
    private List<LimitSpeedData> data;

}
