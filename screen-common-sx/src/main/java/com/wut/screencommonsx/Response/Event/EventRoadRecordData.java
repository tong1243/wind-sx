package com.wut.screencommonsx.Response.Event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventRoadRecordData {
    @JsonProperty("roadName")
    public String roadName;
    @JsonProperty("roadStart")
    public double roadStart;
    @JsonProperty("roadEnd")
    public double roadEnd;
    @JsonProperty("valueToEZ")
    public int valueToEZ;
    @JsonProperty("valueToWH")
    public int valueToWH;
}
