package com.wut.screencommonsx.Response.Section;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 断面参数检测记录。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SectionParameterDetectRecord {
    /** 方向。 */
    @JsonProperty("direction")
    private String direction;

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

    /** 当前车辆数。 */
    @JsonProperty("currentVehicleCount")
    private Integer currentVehicleCount;

    /** 拥堵状态。 */
    @JsonProperty("congestionStatus")
    private String congestionStatus;

    /** 平均速度（km/h）。 */
    @JsonProperty("averageSpeed")
    private Double averageSpeed;

    /** 路段类型。 */
    @JsonProperty("segmentType")
    private String segmentType;
}
