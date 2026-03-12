package com.wut.screencommonsx.Response.Traj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrajFrameData {
    private long timestamp;     // 轨迹数据帧时间戳
    private double longitude;   // 轨迹纬度坐标
    private double latitude;    // 轨迹经度坐标
    private double angle;       // 轨迹航向角
    private String position;    // 轨迹位置
    private double speed;       // 轨迹速度
    private double height;      // 轨迹高度
}
