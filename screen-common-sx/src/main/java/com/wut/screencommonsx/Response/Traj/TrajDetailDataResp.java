package com.wut.screencommonsx.Response.Traj;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrajDetailDataResp {
    @JsonProperty("trajId")
    private long trajId;
    @JsonProperty("license")
    private String license;
    @JsonProperty("direction")
    private int direction;
    @JsonProperty("type")
    private int type;
    @JsonProperty("startTime")
    private String startTime;
    @JsonProperty("endTime")
    private String endTime;
    @JsonProperty("startPosition")
    private String startPosition;
    @JsonProperty("endPosition")
    private String endPosition;
    @JsonProperty("startTimestamp")
    private long startTimestamp;
    @JsonProperty("endTimestamp")
    private long endTimestamp;
    @JsonProperty("startMileage")
    private double startMileage;
    @JsonProperty("endMileage")
    private double endMileage;
    @JsonProperty("frameList")
    private List<TrajFrameData> frameList;
}
