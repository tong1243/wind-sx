package com.wut.screencommonsx.Response.Device;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wut.screencommonsx.Response.TimeRecordData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RadarPeriodDataResp {
    @JsonProperty("sectionFlowTimeList")
    private List<TimeRecordData> sectionFlowTimeList;
    @JsonProperty("sectionSpeedTimeList")
    private List<TimeRecordData> sectionSpeedTimeList;
    @JsonProperty("fiberFlowTimeList")
    private List<TimeRecordData> fiberFlowTimeList;
    @JsonProperty("fiberSpeedTimeList")
    private List<TimeRecordData> fiberSpeedTimeList;
    @JsonProperty("radarFlowTimeList")
    private List<TimeRecordData> radarFlowTimeList;
    @JsonProperty("radarSpeedTimeList")
    private List<TimeRecordData> radarSpeedTimeList;
}
