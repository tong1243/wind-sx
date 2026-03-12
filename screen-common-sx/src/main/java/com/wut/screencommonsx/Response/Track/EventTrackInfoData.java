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
public class EventTrackInfoData {
    @JsonProperty("trajId")
    private long trajId;                        // 轨迹号
    private String license;                     // 车牌号
    private int type;                           // 车型
    private int direction;                      // 道路幅向
    private String time;                        // 最新时间
    private long timestamp;                     // 最新时间戳
    private double speed;                       // 最新速度
    private String position;                    // 最新位置
    @JsonProperty("frameList")
    private List<TrajFrameData> frameList;      // 车辆轨迹数据列表
}
