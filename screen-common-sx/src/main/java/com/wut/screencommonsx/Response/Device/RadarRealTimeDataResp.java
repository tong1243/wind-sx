package com.wut.screencommonsx.Response.Device;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wut.screencommonsx.Response.PositionRecordData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RadarRealTimeDataResp {
    @JsonProperty("sectionFlowRecordList")
    private List<PositionRecordData> sectionFlowRecordList;
    @JsonProperty("sectionSpeedRecordList")
    private List<PositionRecordData> sectionSpeedRecordList;
    @JsonProperty("fiberFlowRecordList")
    private List<PositionRecordData> fiberFlowRecordList;
    @JsonProperty("fiberSpeedRecordList")
    private List<PositionRecordData> fiberSpeedRecordList;
    @JsonProperty("radarFlowRecordList")
    private List<PositionRecordData> radarFlowRecordList;
    @JsonProperty("radarSpeedRecordList")
    private List<PositionRecordData> radarSpeedRecordList;
}
