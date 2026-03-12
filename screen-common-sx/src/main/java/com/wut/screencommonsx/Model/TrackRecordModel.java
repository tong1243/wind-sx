package com.wut.screencommonsx.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrackRecordModel {
    @JsonProperty("trajId")
    private long trajId;
    @JsonProperty("finalName")
    private String finalName;
    @JsonProperty("matchName")
    private String matchName;
}
