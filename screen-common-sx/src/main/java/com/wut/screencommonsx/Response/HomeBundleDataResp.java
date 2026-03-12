package com.wut.screencommonsx.Response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wut.screencommonsx.Response.Device.DeviceStatisticData;
import com.wut.screencommonsx.Response.Event.EventRoadRecordData;
import com.wut.screencommonsx.Response.Event.EventStatisticData;
import com.wut.screencommonsx.Response.Posture.PostureFlowTypeData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HomeBundleDataResp {
    @JsonProperty("deviceStatisticData")
    private DeviceStatisticData deviceStatisticData;
    @JsonProperty("eventStatisticData")
    private EventStatisticData eventStatisticData;
    @JsonProperty("eventRoadRecordList")
    private List<EventRoadRecordData> eventRoadRecordList;
    @JsonProperty("postureFlowTypeList")
    private List<PostureFlowTypeData> postureFlowTypeList;
    @JsonProperty("postureSpeedRecordList")
    private List<TimeRecordData> postureSpeedRecordList;
    @JsonProperty("sectionFlowRecordList")
    private List<PositionRecordData> sectionFlowRecordList;
    @JsonProperty("sectionSpeedRecordList")
    private List<PositionRecordData> sectionSpeedRecordList;
}
