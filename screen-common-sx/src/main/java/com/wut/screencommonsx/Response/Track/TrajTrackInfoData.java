package com.wut.screencommonsx.Response.Track;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wut.screencommonsx.Response.Traj.TrajFrameData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrajTrackInfoData {
    @JsonProperty("trajId")
    private long trajId;                        // 轨迹号
    private String license;                     // 车牌号
    private String match;                       // 匹配上的车牌号
    private int type;                           // 车型
    private int direction;                      // 道路幅向
    @JsonProperty("startTime")
    private String startTime;                   // 起始时间
    @JsonProperty("startTimestamp")
    private long startTimestamp;                // 起始时间戳
    @JsonProperty("startPosition")
    private String startPosition;               // 起始位置
    @JsonProperty("endTime")
    private String endTime;                     // 终止时间
    @JsonProperty("endTimestamp")
    private long endTimestamp;                  // 终止时间戳
    @JsonProperty("endPosition")
    private String endPosition;                 // 终止位置
    @JsonProperty("frameList")
    private List<TrajFrameData> frameList;      // 车辆轨迹数据列表
}
