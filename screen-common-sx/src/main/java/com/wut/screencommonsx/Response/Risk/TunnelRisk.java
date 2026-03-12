package com.wut.screencommonsx.Response.Risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TunnelRisk {
    @JsonProperty("sId")
    private int sId;
    @JsonProperty("riskLevel")
    private int riskLevel;
    @JsonProperty("maxRiskLevel")
    private int maxRiskLevel;
    @JsonProperty("timestamp")
    private Long timestamp;
    @JsonProperty("stream")
    private Double stream;
    @JsonProperty("density")
    private Double density;
    @JsonProperty("speed")
    private Double speed;
    @JsonProperty("TSC")
    private Double TSC;
    @JsonProperty("riskCount")
    private int riskCount;
}