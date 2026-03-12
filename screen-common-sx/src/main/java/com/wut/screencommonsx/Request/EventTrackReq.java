package com.wut.screencommonsx.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventTrackReq {
    private long timestamp;
    private String uuid;
}
