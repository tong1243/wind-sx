package com.wut.screencommonsx.Response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PositionRecordData {
    @JsonProperty("xsecName")
    private String xsecName;
    @JsonProperty("xsecValue")
    private double xsecValue;
    @JsonProperty("valueToWH")
    private double valueToWH;
    @JsonProperty("valueToEZ")
    private double valueToEZ;
}
