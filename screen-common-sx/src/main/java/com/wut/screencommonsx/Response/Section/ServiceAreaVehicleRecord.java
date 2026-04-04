package com.wut.screencommonsx.Response.Section;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务区车辆统计记录。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceAreaVehicleRecord {
    /** 服务区位置名称。 */
    @JsonProperty("locationName")
    private String locationName;

    /** 进入车辆数。 */
    @JsonProperty("inCount")
    private Integer inCount;

    /** 离开车辆数。 */
    @JsonProperty("outCount")
    private Integer outCount;

    /** 当前在区车辆数。 */
    @JsonProperty("vehicleCount")
    private Integer vehicleCount;
}
