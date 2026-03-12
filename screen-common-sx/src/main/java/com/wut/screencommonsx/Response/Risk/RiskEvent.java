package com.wut.screencommonsx.Response.Risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskEvent {
    @JsonProperty("timestamp")
    private Long timestamp;
    @JsonProperty("carId")
    private String carId;
    @JsonProperty("position")
    private String position;
    @JsonProperty("speed")
    private Double speed;
    @JsonProperty("inteSpeed")
    private Double inteSpeed;
    @JsonProperty("distanceFrontCar")
    private Double distanceFrontCar;
    @JsonProperty("riskType")
    private int riskType;
}
