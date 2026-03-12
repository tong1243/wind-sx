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
public class CongestionDataResp {
    @JsonProperty("upCongestion")
    private double upCongestion;
    @JsonProperty("downCongestion")
    private double downCongestion;
    @JsonProperty("congestionRecordList")
    private List<TimeRecordData> congestionRecordList;   // 拥堵趋势表格数据
}
