package com.wut.screencommonsx.Response.Posture;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostureStatisticData {
    @JsonProperty("flowToWH")
    private double flowToWH;                            // 鄂州至武汉小时平均流量
    @JsonProperty("flowToEZ")
    private double flowToEZ;                            // 武汉至鄂州小时平均流量
    @JsonProperty("speedToWH")
    private double speedToWH;                           // 鄂州至武汉平均速度
    @JsonProperty("speedToEZ")
    private double speedToEZ;                           // 武汉至鄂州平均速度
    @JsonProperty("congestionToWH")
    private double congestionToWH;                      // 鄂州至武汉拥堵指数
    @JsonProperty("congestionToEZ")
    private double congestionToEZ;                      // 武汉至鄂州拥堵指数
}
