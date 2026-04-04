package com.wut.screencommonsx.Response.Section;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 断面参数检测响应。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SectionParameterDetectResp {
    /** 查询时间戳。 */
    @JsonProperty("timestamp")
    private long timestamp;

    /** 检测记录列表。 */
    @JsonProperty("recordList")
    private List<SectionParameterDetectRecord> recordList;
}
