package com.wut.screencommonsx.Response.Track;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wut.screencommonsx.Response.Event.EventInfoData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventTrackDataResp {
    @JsonProperty("eventInfo")
    private EventInfoData eventInfo;                    // 事件详细信息
    @JsonProperty("trackInfoList")
    private List<EventTrackInfoData> trackInfoList;     // 事件相关车辆轨迹信息
}
