package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.VehicleAuditRejectRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Service.OperationMaintenanceService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 运维后台接口控制器（第三章）。
 * 提供在途车辆、预约管理、车辆审核与导出能力。
 */
@RestController
@RequestMapping("/api/v1/operation-maintenance")
@RequiredArgsConstructor
public class OperationMaintenanceController {

    /** 运维后台业务服务。 */
    private final OperationMaintenanceService operationMaintenanceService;

    /**
     * 查询在途车辆表格。
     *
     * @param pageNo 分页页码
     * @param pageSize 分页大小
     * @param vehicleId 车辆ID（精准）
     * @param licensePlate 车牌号（精准）
     * @param direction 行驶方向
     * @return 在途车辆分页结果
     */
    @GetMapping("/realtime-vehicles")
    public ApiResponse<?> getRealtimeVehicleTable(@RequestParam(defaultValue = "1") long pageNo,
                                                  @RequestParam(defaultValue = "10") long pageSize,
                                                  @RequestParam(required = false) String vehicleId,
                                                  @RequestParam(required = false) String licensePlate,
                                                  @RequestParam(required = false) String direction) {
        return operationMaintenanceService.getRealtimeVehicleTable(pageNo, pageSize, vehicleId, licensePlate, direction);
    }

    /**
     * 查询预约记录表格。
     *
     * @param pageNo 分页页码
     * @param pageSize 分页大小
     * @param licensePlate 车牌号筛选
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param deductedOnly 是否仅扣分车辆
     * @return 预约分页结果
     */
    @GetMapping("/reservations")
    public ApiResponse<?> getReservationTable(@RequestParam(defaultValue = "1") long pageNo,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam(required = false) String licensePlate,
                                              @RequestParam(required = false) String startTime,
                                              @RequestParam(required = false) String endTime,
                                              @RequestParam(required = false) Boolean deductedOnly) {
        return operationMaintenanceService.getReservationTable(pageNo, pageSize, licensePlate, startTime, endTime, deductedOnly);
    }

    /**
     * 查询车辆审核列表。
     *
     * @param pageNo 分页页码
     * @param pageSize 分页大小
     * @param keyword 关键字（车辆名称/车牌）
     * @param auditStatus 审核状态
     * @return 审核分页结果
     */
    @GetMapping("/vehicle-audit")
    public ApiResponse<?> getVehicleAuditTable(@RequestParam(defaultValue = "1") long pageNo,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String auditStatus) {
        return operationMaintenanceService.getVehicleAuditTable(pageNo, pageSize, keyword, auditStatus);
    }

    /**
     * 查询车辆审核明细。
     *
     * @param licensePlate 车牌号
     * @return 审核明细
     */
    @GetMapping("/vehicle-audit/{licensePlate}")
    public ApiResponse<?> getVehicleAuditDetail(@PathVariable String licensePlate) {
        return operationMaintenanceService.getVehicleAuditDetail(licensePlate);
    }

    /**
     * 审核通过。
     *
     * @param licensePlate 车牌号
     * @return 操作结果
     */
    @PostMapping("/vehicle-audit/{licensePlate}/approve")
    public ApiResponse<?> approveVehicleAudit(@PathVariable String licensePlate) {
        return operationMaintenanceService.approveVehicleAudit(licensePlate);
    }

    /**
     * 审核拒绝。
     *
     * @param licensePlate 车牌号
     * @param request 拒绝项与备注
     * @return 操作结果
     */
    @PostMapping("/vehicle-audit/{licensePlate}/reject")
    public ApiResponse<?> rejectVehicleAudit(@PathVariable String licensePlate,
                                             @Valid @RequestBody VehicleAuditRejectRequest request) {
        return operationMaintenanceService.rejectVehicleAudit(licensePlate, request.getRejectItems(), request.getRejectRemark());
    }

    /**
     * 导出车辆审核数据（CSV）。
     *
     * @param keyword 关键字筛选
     * @param auditStatus 审核状态筛选
     * @param response HTTP 响应对象
     * @throws IOException 输出异常
     */
    @GetMapping("/vehicle-audit/export")
    public void exportVehicleAudit(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String auditStatus,
                                   HttpServletResponse response) throws IOException {
        byte[] content = operationMaintenanceService.exportVehicleAudit(keyword, auditStatus);
        String fileName = URLEncoder.encode("vehicle-audit-export.csv", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.getOutputStream().write(content);
        response.getOutputStream().flush();
    }
}
