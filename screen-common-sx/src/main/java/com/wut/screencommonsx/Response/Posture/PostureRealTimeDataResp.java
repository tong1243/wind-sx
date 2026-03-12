package com.wut.screencommonsx.Response.Posture;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostureRealTimeDataResp {
    @JsonProperty("statisticData")
    private PostureStatisticData statisticData;         // 流量/速度/拥堵指数统计数据
    @JsonProperty("flowTypeList")
    private List<PostureFlowTypeData> flowTypeList;     // 车辆数量和流量车型分类列表
}
