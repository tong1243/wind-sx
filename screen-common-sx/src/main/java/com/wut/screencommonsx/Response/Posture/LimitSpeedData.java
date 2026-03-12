package com.wut.screencommonsx.Response.Posture;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LimitSpeedData {
    @JsonProperty("sid")
    private int sid;
    @JsonProperty("speed")
    private double speed;
}