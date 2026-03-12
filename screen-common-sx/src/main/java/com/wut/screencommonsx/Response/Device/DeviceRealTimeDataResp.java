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
public class DeviceRealTimeDataResp {
    @JsonProperty("fiberRecordList")
    private List<PositionRecordData> fiberRecordList;
    @JsonProperty("radarRecordList")
    private List<PositionRecordData> radarRecordList;
}
