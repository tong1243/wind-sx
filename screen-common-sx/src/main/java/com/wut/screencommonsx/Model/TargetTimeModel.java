package com.wut.screencommonsx.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TargetTimeModel {
    @JsonProperty("tableDateStr")
    private String tableDateStr;
    @JsonProperty("startTimestamp")
    private long startTimestamp;
    @JsonProperty("endTimestamp")
    private long endTimestamp;
}
