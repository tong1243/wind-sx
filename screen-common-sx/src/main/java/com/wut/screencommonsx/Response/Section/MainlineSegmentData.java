package com.wut.screencommonsx.Response.Section;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全线状态可视化中的单路段数据。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MainlineSegmentData {
    /** 方向。 */
    @JsonProperty("direction")
    private String direction;

    /** 参考桩号。 */
    @JsonProperty("stake")
    private String stake;

    /** 起点里程（米）。 */
    @JsonProperty("startLocation")
    private Integer startLocation;

    /** 终点里程（米）。 */
    @JsonProperty("endLocation")
    private Integer endLocation;

    /** 起点桩号。 */
    @JsonProperty("startStake")
    private String startStake;

    /** 终点桩号。 */
    @JsonProperty("endStake")
    private String endStake;

    /** 拥堵状态。 */
    @JsonProperty("congestionStatus")
    private String congestionStatus;

    /** 路段类型。 */
    @JsonProperty("segmentType")
    private String segmentType;

    /** 渲染颜色。 */
    @JsonProperty("color")
    private String color;

    /** 平均速度（km/h）。 */
    @JsonProperty("averageSpeed")
    private Double averageSpeed;
}
