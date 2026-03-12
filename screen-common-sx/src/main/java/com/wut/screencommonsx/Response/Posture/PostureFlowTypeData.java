package com.wut.screencommonsx.Response.Posture;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostureFlowTypeData {
    private String name;            // 车型名
    private int type;               // 车型编号
    @JsonProperty("carNumToWH")
    private int carNumToWH;         // 鄂州至武汉车辆数量
    @JsonProperty("carNumToEZ")
    private int carNumToEZ;         // 武汉至鄂州车辆数量
    @JsonProperty("flowToWH")
    private double flowToWH;        // 鄂州至武汉贡献流量
    @JsonProperty("flowToEZ")
    private double flowToEZ;        // 武汉至鄂州贡献流量
}
