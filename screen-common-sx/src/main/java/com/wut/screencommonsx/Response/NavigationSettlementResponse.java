package com.wut.screencommonsx.Response;

import lombok.Data;

@Data
public class NavigationSettlementResponse {
    private String settlementId;
    private String navigationSessionId;
    private Integer deductedPoints;
    private Integer remainingPoints;
}
