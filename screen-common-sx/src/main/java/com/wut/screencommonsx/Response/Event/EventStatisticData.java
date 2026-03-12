package com.wut.screencommonsx.Response.Event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventStatisticData {
    private int total;      // 事件总数
    private int pending;    // 待处理事件数
    private int parking;    // 违停事件数
    private int against;    // 逆行事件数
    private int fast;       // 超速事件数
    private int slow;       // 低速事件数
}
