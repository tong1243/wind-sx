package com.wut.screencommonsx.Response.Posture;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoadNetworkResp {
    private long timestamp;         // 数据时间戳
    private String road;
    private double avgSpeed;
    private double avgStream;
    private double avgQueueLength;
    private double avgDelay;
    private double avgDensity;
    private String congestionRoad;
}
