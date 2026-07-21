package com.wut.screencommonsx.Request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NavigationSettlementRequest {
    private String navigationSessionId;
    private String reservationId;
    private String endReason;
    private Long endedAt;
    private CarSnapshot carSnapshot;
    private List<DeductionItem> items = new ArrayList<>();
    private Integer totalDeduction;
    private Double travelMileageKm;
    private Long travelDurationSeconds;
    private Double drivingMileageKm;
    private Long drivingDurationSeconds;
    private String startPile;
    private String endPile;

    @Data
    public static class CarSnapshot {
        private Double speed;
        private Integer direction;
        private String pile;
        private Double unifiedPositionKm;
        private Integer road;
        private Long sampledAt;
    }

    @Data
    public static class DeductionItem {
        private String code;
        private String label;
        private Integer count;
        private Integer pointsPerOccurrence;
        private Integer deductionPoints;
    }
}
