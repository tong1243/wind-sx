package com.wut.screencommonsx.Response.Event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventInfoDataResp {
    private String license;         // 车牌号
    @JsonProperty("trajId")
    private long trajId;            // 轨迹号
    private int type;               // 事件类型
    private String time;            // 事件发生时间
    private int frenetX;            // 事件桩号
    private String position;        // 事件发生位置
    private int status;             // 事件状态
    private int process;            // 事件处理方式
    private int lane;               // 车道号
    private int during;             // 事件持续时间
    @JsonProperty("distanceOutTunnel")
    private int distanceOutTunnel;  // 隧道出口距离
    @JsonProperty("distanceInTunnel")
    private int distanceInTunnel;   // 隧道入口距离
    private String accidentLevel;   // 事故等级
    @JsonProperty("queueLength")
    private double queueLength;     // 排队长度
    @JsonProperty("road")
    private String road;            // 事件路段
}
