package com.wut.screencommonsx.Response.Track;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrajTrackDataResp {
    @JsonProperty("trackInfoList")
    private List<TrajTrackInfoData> trackInfoList;
}
