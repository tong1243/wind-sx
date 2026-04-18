package com.wut.screencommonsx.Response.Operation;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class OperationMaintenanceResp {

    @Data
    public static class PageData<T> {
        private long pageNo;
        private long pageSize;
        private long total;
        private List<T> records;
    }

    @Data
    public static class RealtimeVehicleRow {
        private String id;
        private String currentLocation;
        private String licensePlate;
        private String vehicleType;
        private Integer speedKmh;
        private Integer direction;
        private LocalDateTime reportTime;
    }

    @Data
    public static class ReservationRow {
        private Long id;
        private String licensePlate;
        private String travelTimeSlot;
        private String vehicleType;
        private String deductionRecord;
        // 2: pending, 1: approved, 0: rejected
        private Integer isPassed;
        private String auditStatusText;
        private String rejectReason;
        private LocalDateTime createTime;
    }

    @Data
    public static class VehicleAuditRow {
        private Integer id;
        private String vehicleName;
        private String licensePlate;
        private String auditStatus;
        private String auditStatusText;
        private Integer currentPoints;
        private LocalDateTime createTime;
    }

    @Data
    public static class VehicleAuditTableData {
        private long pendingCount;
        private PageData<VehicleAuditRow> page;
    }

    @Data
    public static class VehicleAuditDetail {
        private Integer id;
        private String vehicleName;
        private String licensePlate;
        private String auditStatus;
        private String auditStatusText;
        private String vehicleType;
        private String owner;
        private String usageNature;
        private String brandModel;
        private String vin;
        private String engineNumber;
        private LocalDate registerDate;
        private String licensePhoto;
        private Integer currentPoints;
        private String rejectReason;
        private List<AuditCheckItem> checkItems;
        private ReservationStats reservationStats;
        private ReservationBrief latestReservation;
    }

    @Data
    public static class ReservationStats {
        private long totalCount;
        private long passedCount;
        private long rejectedCount;
    }

    @Data
    public static class ReservationBrief {
        private Long id;
        private String travelTimeSlot;
        private String startPoint;
        private String endPoint;
        private String vehicleType;
        private Integer isPassed;
        private String rejectReason;
        private LocalDateTime createTime;
    }

    @Data
    public static class AuditCheckItem {
        private String key;
        private String label;
        private boolean checked;
    }
}
