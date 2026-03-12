package com.wut.screencommonsx.Response.Traj;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrajCarStatisticData {
    @JsonProperty("carTotalToWH")
    private int carTotalToWH;        // 鄂州至武汉方向累计车辆数
    @JsonProperty("carTotalToEZ")
    private int carTotalToEZ;        // 武汉至鄂州方向累计车辆数
    @JsonProperty("carToWH")
    private int carToWH;             // 鄂州至武汉方向在途车辆数
    @JsonProperty("carToEZ")
    private int carToEZ;             // 武汉至鄂州方向在途车辆数
}
