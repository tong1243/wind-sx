package com.wut.screencommonsx.Response.Device;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceStatisticData {
    private int total;
    private int online;
    private int offline;
    @JsonProperty("highTimeout")
    private int highTimeout;
    @JsonProperty("faulty")
    private int faulty;
    @JsonProperty("avgTimeout")
    private long avgTimeout;
}
