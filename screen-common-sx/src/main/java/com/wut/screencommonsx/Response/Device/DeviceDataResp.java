package com.wut.screencommonsx.Response.Device;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceDataResp {
    @JsonProperty("statisticData")
    private DeviceStatisticData statisticData;
    @JsonProperty("infoList")
    private List<DeviceInfoData> infoList;
}
