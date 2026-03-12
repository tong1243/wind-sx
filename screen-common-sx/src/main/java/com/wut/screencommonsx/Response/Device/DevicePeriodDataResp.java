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
public class DevicePeriodDataResp {
    @JsonProperty("radarTimeList")
    private List<TimeRecordData> radarTimeList;
}
