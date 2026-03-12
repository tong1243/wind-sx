package com.wut.screencommonsx.Response.Traj;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrajInfoData {
    @JsonProperty("trajId")
    private long trajId;                        // 轨迹号
    @JsonProperty("license")
    private String license;                     // 车牌号
    @JsonProperty("type")
    private int type;                           // 车型
    @JsonProperty("direction")
    private int direction;                      // 道路幅向
    @JsonProperty("speed")
    private double speed;                       // 车辆速度(取最新数据)
    @JsonProperty("position")
    private String position;                    // 车辆位置(取最新数据)
    @JsonProperty("timestamp")
    private long timestamp;
    @JsonProperty("state")
    private int state;                          // 车辆轨迹状态
                                                // 0 -> 车辆轨迹首次出现(需要添加车辆实体)
                                                // 1 -> 车辆轨迹非首次出现(不需要添加车辆实体)
    @JsonProperty("frameList")
    private List<TrajFrameData> frameList;      // 车辆轨迹数据列表
}
