package com.wut.screencommonsx.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventProcessReq {
    private long timestamp;
    private String uuid;
    private int status;     // 目标事件处理状态
    private int process;    // 目标事件处理方式
}
