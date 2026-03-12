package com.wut.screencommonsx.Response.Section;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wut.screencommonsx.Response.PositionRecordData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SectionRealTimeDataResp {
    @JsonProperty("flowRecordList")
    private List<PositionRecordData> flowRecordList;
    @JsonProperty("speedRecordList")
    private List<PositionRecordData> speedRecordList;
    @JsonProperty("congestionRecordList")
    private List<PositionRecordData> congestionRecordList;
}
