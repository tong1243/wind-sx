package com.wut.screencommonsx.Response.Posture;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParametersResp {
    @JsonProperty("time")
    private int time;
    @JsonProperty("roadId")
    private int roadId;
    @JsonProperty("stream")
    private double stream;
    @JsonProperty("density")
    private double density;
    @JsonProperty("speed")
    private double speed;
    @JsonProperty("travelTime")
    private double travelTime;
    @JsonProperty("delay")
    private double delay;
    @JsonProperty("state")
    private int state;
    @JsonProperty("timeStamp")
    private long timeStamp;
    @JsonProperty("upSpeed")
    private double upSpeed;
    @JsonProperty("downSpeed")
    private double downSpeed;
    @JsonProperty("upDensity")
    private double upDensity;
    @JsonProperty("downDensity")
    private double downDensity;
    @JsonProperty("rampStream")
    private double rampStream;
    @JsonProperty("carCount")
    private int carCount;
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
