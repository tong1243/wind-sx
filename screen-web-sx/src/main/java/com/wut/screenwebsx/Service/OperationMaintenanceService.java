package com.wut.screenwebsx.Service;

import com.wut.screencommonsx.Response.ApiResponse;

import java.util.List;

/**
 * 运维后台业务服务接口。
 */
public interface OperationMaintenanceService {
    /**
     * 查询在途车辆表格数据。
     */
    ApiResponse<?> getRealtimeVehicleTable(long pageNo, long pageSize, String vehicleId, String licensePlate, String direction);

    /**
     * 查询预约表格数据。
     */
    ApiResponse<?> getReservationTable(long pageNo, long pageSize, String licensePlate, String startTime, String endTime, Boolean deductedOnly);

    /**
     * 查询车辆审核列表。
     */
    ApiResponse<?> getVehicleAuditTable(long pageNo, long pageSize, String keyword, String auditStatus);

    /**
     * 查询车辆审核明细。
     */
    ApiResponse<?> getVehicleAuditDetail(String licensePlate);

    /**
     * 审核通过。
     */
    ApiResponse<?> approveVehicleAudit(String licensePlate);

    /**
     * 审核拒绝。
     */
    ApiResponse<?> rejectVehicleAudit(String licensePlate, List<String> rejectItems, String rejectRemark);

    /**
     * 导出车辆审核列表。
     */
    byte[] exportVehicleAudit(String keyword, String auditStatus);
}
