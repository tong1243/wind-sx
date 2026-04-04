package com.wut.screencommonsx.Response.Operation;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 运维后台接口响应模型集合。
 */
public class OperationMaintenanceResp {

    /**
     * 通用分页数据。
     */
    @Data
    public static class PageData<T> {
        /** 当前页码。 */
        private long pageNo;
        /** 每页条数。 */
        private long pageSize;
        /** 总记录数。 */
        private long total;
        /** 分页记录。 */
        private List<T> records;
    }

    /**
     * 在途车辆行数据。
     */
    @Data
    public static class RealtimeVehicleRow {
        /** 车辆ID。 */
        private String id;
        /** 当前里程位置。 */
        private String currentLocation;
        /** 车牌号。 */
        private String licensePlate;
        /** 车辆类型。 */
        private String vehicleType;
        /** 实时速度（km/h）。 */
        private Integer speedKmh;
        /** 行驶方向。 */
        private String direction;
        /** 上报时间。 */
        private LocalDateTime reportTime;
    }

    /**
     * 预约记录行数据。
     */
    @Data
    public static class ReservationRow {
        /** 记录ID。 */
        private Long id;
        /** 车牌号。 */
        private String licensePlate;
        /** 预约通行时段。 */
        private String travelTimeSlot;
        /** 车辆类型。 */
        private String vehicleType;
        /** 扣分记录文本。 */
        private String deductionRecord;
        /** 创建时间。 */
        private LocalDateTime createTime;
    }

    /**
     * 车辆审核列表行数据。
     */
    @Data
    public static class VehicleAuditRow {
        /** 车辆主键ID。 */
        private Integer id;
        /** 车辆名称。 */
        private String vehicleName;
        /** 车牌号。 */
        private String licensePlate;
        /** 审核状态编码。 */
        private String auditStatus;
        /** 审核状态文本。 */
        private String auditStatusText;
        /** 当前记分。 */
        private Integer currentPoints;
        /** 创建时间。 */
        private LocalDateTime createTime;
    }

    /**
     * 车辆审核列表响应体。
     */
    @Data
    public static class VehicleAuditTableData {
        /** 待审核数量。 */
        private long pendingCount;
        /** 分页数据。 */
        private PageData<VehicleAuditRow> page;
    }

    /**
     * 车辆审核明细。
     */
    @Data
    public static class VehicleAuditDetail {
        /** 车辆主键ID。 */
        private Integer id;
        /** 车辆名称。 */
        private String vehicleName;
        /** 车牌号。 */
        private String licensePlate;
        /** 审核状态编码。 */
        private String auditStatus;
        /** 审核状态文本。 */
        private String auditStatusText;
        /** 车辆类型。 */
        private String vehicleType;
        /** 所有人。 */
        private String owner;
        /** 使用性质。 */
        private String usageNature;
        /** 品牌型号。 */
        private String brandModel;
        /** 车架号。 */
        private String vin;
        /** 发动机号。 */
        private String engineNumber;
        /** 注册日期。 */
        private LocalDate registerDate;
        /** 行驶证照片地址。 */
        private String licensePhoto;
        /** 当前记分。 */
        private Integer currentPoints;
        /** 驳回原因。 */
        private String rejectReason;
        /** 勾选错误项。 */
        private List<AuditCheckItem> checkItems;
        /** 预约统计。 */
        private ReservationStats reservationStats;
        /** 最近预约情况。 */
        private ReservationBrief latestReservation;
    }

    /**
     * 预约统计信息。
     */
    @Data
    public static class ReservationStats {
        /** 总预约数。 */
        private long totalCount;
        /** 通过数。 */
        private long passedCount;
        /** 拒绝数。 */
        private long rejectedCount;
    }

    /**
     * 最近预约简要信息。
     */
    @Data
    public static class ReservationBrief {
        /** 预约ID。 */
        private Long id;
        /** 预约时段。 */
        private String travelTimeSlot;
        /** 起点。 */
        private String startPoint;
        /** 终点。 */
        private String endPoint;
        /** 车辆类型。 */
        private String vehicleType;
        /** 是否通过。 */
        private Integer isPassed;
        /** 拒绝原因。 */
        private String rejectReason;
        /** 创建时间。 */
        private LocalDateTime createTime;
    }

    /**
     * 审核勾选项。
     */
    @Data
    public static class AuditCheckItem {
        /** 勾选项编码。 */
        private String key;
        /** 勾选项文案。 */
        private String label;
        /** 是否勾选。 */
        private boolean checked;
    }
}
