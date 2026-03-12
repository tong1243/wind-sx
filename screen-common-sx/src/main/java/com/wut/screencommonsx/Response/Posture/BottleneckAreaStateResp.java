package com.wut.screencommonsx.Response.Posture;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BottleneckAreaStateResp {
    @JsonProperty("id")
    private int id;
    @JsonProperty("state")
    private int state;
    @JsonProperty("speed")
    private double speed;
    @JsonProperty("stream")
    private double stream;
    @JsonProperty("mainStream")
    private int mainStream;
    @JsonProperty("rampStream")
    private int rampStream;
    @JsonProperty("queueLength")
    private double queueLength;
    @JsonProperty("queueDelayTime")
    private double queueDelayTime;
}
