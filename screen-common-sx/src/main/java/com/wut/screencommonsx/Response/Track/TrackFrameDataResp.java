package com.wut.screencommonsx.Response.Track;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wut.screencommonsx.Response.Traj.TrajFrameData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrackFrameDataResp {
    @JsonProperty("frameList")
    private List<TrajFrameData> frameList;
}
