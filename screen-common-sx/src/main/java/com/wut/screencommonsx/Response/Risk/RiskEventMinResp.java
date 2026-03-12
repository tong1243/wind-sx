package com.wut.screencommonsx.Response.Risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskEventMinResp {
    @JsonProperty("timestamp")
    private Long timestamp;
    @JsonProperty("carId")
    private String carId;
    @JsonProperty("position")
    private String position;
    @JsonProperty("riskType")
    private int riskType;
}
