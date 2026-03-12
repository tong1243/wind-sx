package com.wut.screencommonsx.Response.Risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskEventResp {
    @JsonProperty("riskLevelData")
    private List<RiskLevelData> riskLevelData;
    @JsonProperty("riskData")
    private List<RiskData> riskData;
    @JsonProperty("riskEvent")
    private List<RiskEvent> riskEvent;
    @JsonProperty("tunnelRisk")
    private List<TunnelRisk> tunnelRisk;
}
