package com.wut.screencommonsx.Response.Section;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 全线状态可视化响应。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MainlineVisualizationResp {
    /** 查询时间戳。 */
    @JsonProperty("timestamp")
    private long timestamp;

    /** 路段列表。 */
    @JsonProperty("segmentList")
    private List<MainlineSegmentData> segmentList;
}
