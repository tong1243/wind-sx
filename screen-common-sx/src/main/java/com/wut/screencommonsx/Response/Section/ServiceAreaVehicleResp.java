package com.wut.screencommonsx.Response.Section;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 服务区进出车辆响应。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceAreaVehicleResp {
    /** 查询时间戳。 */
    @JsonProperty("timestamp")
    private long timestamp;

    /** 服务区车辆统计列表。 */
    @JsonProperty("recordList")
    private List<ServiceAreaVehicleRecord> recordList;
}
