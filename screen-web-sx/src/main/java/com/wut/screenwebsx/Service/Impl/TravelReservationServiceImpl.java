package com.wut.screenwebsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wut.screencommonsx.Exception.BusinessException;
import com.wut.screencommonsx.Model.TravelReservation;

import com.wut.screencommonsx.Request.GreenCodeRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Service.TravelReservationService;
import com.wut.screenwebsx.Mapper.TravelReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 出行预约服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelReservationServiceImpl implements TravelReservationService {
    private final TravelReservationMapper reservationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> generateGreenCode(GreenCodeRequest request, String phone) {
        // 验证起点终点不能相同
        if (request.getStartPoint().equals(request.getEndPoint())) {
            throw BusinessException.badRequest("起点和终点不能相同");
        }

        // 保存预约记录
        TravelReservation reservation = buildReservation(request, phone);
        reservationMapper.insert(reservation);

        // 生成绿码（Mock Base64）
        GreenCodeResponse response = new GreenCodeResponse();
        response.setSuccess(true);
        response.setQrCode("data:image/png;base64,iVBORw0KGgoAAAANS...");
        response.setReservationData(request);

        return ApiResponse.success("绿码生成成功", response);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> createCertificate(GreenCodeRequest request, String phone) {
            // 验证预约是否存在（获取最新的预约记录）
            TravelReservation reservation = reservationMapper.selectList(
                    new LambdaQueryWrapper<TravelReservation>()
                            .eq(TravelReservation::getUserPhone, phone)
                            .eq(TravelReservation::getCarLicense, request.getPlateNumber())
                            .eq(TravelReservation::getTravelTimeSlot, request.getTravelTimeSlot())
                            .orderByDesc(TravelReservation::getCreateTime)
                            .last("LIMIT 1")
            ).stream().findFirst().orElse(null);

            if (reservation == null) {
                throw BusinessException.notFound("未找到对应预约记录");
            }
        // 构建通行凭证
        CertificateResponse response = new CertificateResponse();
        response.setId("cert-" + UUID.randomUUID().toString().substring(0, 9));
        response.setReservationData(request);
        response.setQrCode("data:image/png;base64,iVBORw0KGgoAAAANS...");
        response.setCreatedAt(LocalDateTime.now().toString());
        response.setStatus("allowed");

        return ApiResponse.success("通行凭证创建成功", response);
    }

    @Override
    public ApiResponse<?> getCertificate(String phone) {
        // 查询最新的通行凭证对应的预约
        TravelReservation latest = reservationMapper.selectLatestByPhone(phone);
        if (latest == null) {
            throw BusinessException.notFound("暂无通行凭证");
        }

        // 构建返回数据
        CertificateDTO dto = new CertificateDTO();
        dto.setId("cert-" + latest.getId());
        dto.setStartPoint(latest.getStartPoint());
        dto.setEndPoint(latest.getEndPoint());
        dto.setTravelTimeSlot(latest.getTravelTimeSlot());
        dto.setVehicleType(latest.getCarType());
        dto.setPlateNumber(latest.getCarLicense());
        dto.setQrCode("data:image/png;base64,iVBORw0KGgoAAAANS...");
        dto.setCreatedAt(latest.getCreateTime().toString());
        dto.setStatus(latest.getIsPassed() == 1 ? "allowed" : "rejected");

        return ApiResponse.success("获取成功", dto);
    }

    // 构建预约记录
    private TravelReservation buildReservation(GreenCodeRequest request, String phone) {
        TravelReservation reservation = new TravelReservation();
        reservation.setUserPhone(phone);
        reservation.setCarLicense(request.getPlateNumber());
        reservation.setStartPoint(request.getStartPoint());
        reservation.setEndPoint(request.getEndPoint());
        reservation.setTravelTimeSlot(request.getTravelTimeSlot());
        reservation.setCarType(request.getVehicleType());
        reservation.setCargoWeight(request.getCargoWeight() == null ? 
                java.math.BigDecimal.ZERO : new java.math.BigDecimal(request.getCargoWeight()));
        reservation.setIsPassed(1); // 默认通过
        reservation.setCreateTime(LocalDateTime.now());
        reservation.setUpdateTime(LocalDateTime.now());
        // 预约24小时后失效
        reservation.setExpireTime(LocalDateTime.now().plusHours(24));
        return reservation;
    }

    // 绿码响应内部类
    public static class GreenCodeResponse {
        private boolean success;
        private String qrCode;
        private GreenCodeRequest reservationData;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getQrCode() { return qrCode; }
        public void setQrCode(String qrCode) { this.qrCode = qrCode; }
        public GreenCodeRequest getReservationData() { return reservationData; }
        public void setReservationData(GreenCodeRequest reservationData) { this.reservationData = reservationData; }
    }

    // 通行凭证响应内部类
    public static class CertificateResponse {
        private String id;
        private GreenCodeRequest reservationData;
        private String qrCode;
        private String createdAt;
        private String status;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public GreenCodeRequest getReservationData() { return reservationData; }
        public void setReservationData(GreenCodeRequest reservationData) { this.reservationData = reservationData; }
        public String getQrCode() { return qrCode; }
        public void setQrCode(String qrCode) { this.qrCode = qrCode; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    // 通行凭证DTO
    public static class CertificateDTO {
        private String id;
        private String startPoint;
        private String endPoint;
        private String travelTimeSlot;
        private String vehicleType;
        private String plateNumber;
        private String qrCode;
        private String createdAt;
        private String status;

        // Getter & Setter
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getStartPoint() { return startPoint; }
        public void setStartPoint(String startPoint) { this.startPoint = startPoint; }
        public String getEndPoint() { return endPoint; }
        public void setEndPoint(String endPoint) { this.endPoint = endPoint; }
        public String getTravelTimeSlot() { return travelTimeSlot; }
        public void setTravelTimeSlot(String travelTimeSlot) { this.travelTimeSlot = travelTimeSlot; }
        public String getVehicleType() { return vehicleType; }
        public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
        public String getPlateNumber() { return plateNumber; }
        public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
        public String getQrCode() { return qrCode; }
        public void setQrCode(String qrCode) { this.qrCode = qrCode; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}