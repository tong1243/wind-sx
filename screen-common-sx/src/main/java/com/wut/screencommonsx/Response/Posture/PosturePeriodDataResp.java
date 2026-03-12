package com.wut.screencommonsx.Response.Posture;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wut.screencommonsx.Response.TimeRecordData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PosturePeriodDataResp {
    @JsonProperty("flowRecordList")
    private List<TimeRecordData> flowRecordList;         // 流量趋势表格数据
    @JsonProperty("speedRecordList")
    private List<TimeRecordData> speedRecordList;        // 速度趋势表格数据
    @JsonProperty("congestionRecordList")
    private List<TimeRecordData> congestionRecordList;   // 拥堵趋势表格数据
}
