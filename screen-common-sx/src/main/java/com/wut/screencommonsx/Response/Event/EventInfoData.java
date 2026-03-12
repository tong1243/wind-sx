package com.wut.screencommonsx.Response.Event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventInfoData {
    private String uuid;            // UUID
    private String license;         // 车牌号
    @JsonProperty("trajId")
    private long trajId;            // 轨迹号
    private int type;               // 事件类型
    private String time;            // 事件发生时间
    private String position;        // 事件发生位置
    private int status;             // 事件状态
    private int process;            // 事件处理方式
}
