package com.wut.screencommonsx.Response.Risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskDataResp {
    @JsonProperty("sId")
    private int sId;
    @JsonProperty("riskType")
    private int riskType;
    @JsonProperty("timestamp")
    private long timestamp;
    @JsonProperty("riskLevel")
    private int riskLevel;
    @JsonProperty("infoDistribute")
    private int infoDistribute;
}
