package com.wut.screencommonsx.Response.Section;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wut.screencommonsx.Response.TimeRecordData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SectionTimeData {
    @JsonProperty("xsecName")
    private String xsecName;
    @JsonProperty("xsecValue")
    private double xsecValue;
    @JsonProperty("flowRecordList")
    private List<TimeRecordData> flowRecordList;
    @JsonProperty("speedRecordList")
    private List<TimeRecordData> speedRecordList;
    @JsonProperty("congestionRecordList")
    private List<TimeRecordData> congestionRecordList;
}
