package com.wut.screencommonsx.Response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimeRecordData {
    private String time;            // 数据时间
    private long timestamp;         // 数据时间戳
    @JsonProperty("valueToEZ")
    private double valueToEZ;       // 武汉至鄂州方向数值
    @JsonProperty("valueToWH")
    private double valueToWH;       // 鄂州至武汉方向数值
}
