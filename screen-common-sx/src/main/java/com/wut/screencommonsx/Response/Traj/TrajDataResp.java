package com.wut.screencommonsx.Response.Traj;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrajDataResp {
    @JsonProperty("timestamp")
    private long timestamp;                         // 数据最新时间戳
    @JsonProperty("datetime")
    private String datetime;                        // 数据最新时间戳对应时间字符串
    @JsonProperty("carStatisticData")
    private TrajCarStatisticData carStatisticData;  // 车辆数量统计数据
    @JsonProperty("infoListToWH")
    private List<TrajInfoData> infoListToWH;        // 鄂州机场至武汉方向数据
    @JsonProperty("infoListToEZ")
    private List<TrajInfoData> infoListToEZ;        // 武汉至鄂州机场方向数据
    @JsonProperty("expireTrajIdListToWH")
    private List<Long> expireTrajIdListToWH;        // 鄂州机场至武汉方向失效轨迹ID列表
    @JsonProperty("expireTrajIdListToEZ")
    private List<Long> expireTrajIdListToEZ;        // 武汉至鄂州机场方向失效轨迹ID列表
    @JsonProperty("offlineTrajIdListToWH")
    private List<Long> offlineTrajIdListToWH;       // 鄂州机场至武汉方向离线轨迹ID列表
    @JsonProperty("offlineTrajIdListToEZ")
    private List<Long> offlineTrajIdListToEZ;       // 武汉至鄂州机场方向离线轨迹ID列表
}
