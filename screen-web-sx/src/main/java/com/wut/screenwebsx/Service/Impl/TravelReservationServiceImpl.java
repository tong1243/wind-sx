package com.wut.screenwebsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wut.screencommonsx.Exception.BusinessException;
import com.wut.screencommonsx.Model.TravelReservation;
import com.wut.screencommonsx.Request.GreenCodeRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Mapper.TravelReservationMapper;
import com.wut.screenwebsx.Service.TravelReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TravelReservationServiceImpl implements TravelReservationService {
    private final TravelReservationMapper reservationMapper;
    private static final int RESERVATION_PENDING = 2;
    private static final int RESERVATION_APPROVED = 1;
    private static final int RESERVATION_REJECTED = 0;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> generateGreenCode(GreenCodeRequest request, String phone) {
        if (request.getStartPoint().equals(request.getEndPoint())) {
            throw BusinessException.badRequest("startPoint and endPoint cannot be the same");
        }

        TravelReservation reservation = buildReservation(request, phone);
        reservationMapper.insert(reservation);

        GreenCodeResponse response = new GreenCodeResponse();
        response.setSuccess(true);
        response.setQrCode("data:image/png;base64,iVBORw0KGgoAAAANS...");
        response.setReservationData(request);
        return ApiResponse.success("reservation submitted, pending audit", response);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> createCertificate(GreenCodeRequest request, String phone) {
        TravelReservation reservation = reservationMapper.selectList(
                new LambdaQueryWrapper<TravelReservation>()
                        .eq(TravelReservation::getUserPhone, phone)
                        .eq(TravelReservation::getCarLicense, request.getPlateNumber())
                        .eq(TravelReservation::getTravelTimeSlot, request.getTravelTimeSlot())
                        .orderByDesc(TravelReservation::getCreateTime)
                        .last("LIMIT 1")
        ).stream().findFirst().orElse(null);

        if (reservation == null) {
            throw BusinessException.notFound("No matching reservation record found");
        }

        CertificateResponse response = new CertificateResponse();
        response.setId("cert-" + UUID.randomUUID().toString().substring(0, 9));
        response.setReservationData(toReservationData(request));
        response.setQrCode("data:image/png;base64,iVBORw0KGgoAAAANS...");
        response.setCreatedAt(LocalDateTime.now().toString());
        response.setStatus(toCertificateStatus(reservation.getIsPassed()));
        return ApiResponse.success("certificate status loaded", response);
    }

    @Override
    public ApiResponse<?> getCertificate(String phone) {
        TravelReservation latest = reservationMapper.selectLatestByPhone(phone);
        if (latest == null) {
            throw BusinessException.notFound("No certificate found");
        }

        CertificateResponse response = new CertificateResponse();
        response.setId("cert-" + latest.getId());
        response.setReservationData(toReservationData(latest));
        response.setQrCode("data:image/png;base64,iVBORw0KGgoAAAANS...");
        response.setCreatedAt(latest.getCreateTime().toString());
        response.setStatus(toCertificateStatus(latest.getIsPassed()));
        return ApiResponse.success("\u83b7\u53d6\u6210\u529f", response);
    }

    private TravelReservation buildReservation(GreenCodeRequest request, String phone) {
        TravelReservation reservation = new TravelReservation();
        reservation.setUserPhone(phone);
        reservation.setCarLicense(request.getPlateNumber());
        reservation.setStartPoint(request.getStartPoint());
        reservation.setEndPoint(request.getEndPoint());
        reservation.setTravelTimeSlot(request.getTravelTimeSlot());
        reservation.setCarType(request.getVehicleType());
        reservation.setCargoWeight(request.getCargoWeight() == null
                ? BigDecimal.ZERO
                : new BigDecimal(request.getCargoWeight()));
        reservation.setIsPassed(RESERVATION_PENDING);
        reservation.setCreateTime(LocalDateTime.now());
        reservation.setUpdateTime(LocalDateTime.now());
        reservation.setExpireTime(LocalDateTime.now().plusHours(24));
        return reservation;
    }

    private String toCertificateStatus(Integer isPassed) {
        if (isPassed == null || isPassed == RESERVATION_PENDING) {
            return "pending";
        }
        if (isPassed == RESERVATION_APPROVED) {
            return "allowed";
        }
        if (isPassed == RESERVATION_REJECTED) {
            return "rejected";
        }
        return "unknown";
    }

    private ReservationData toReservationData(GreenCodeRequest request) {
        ReservationData data = new ReservationData();
        data.setStartPoint(request.getStartPoint());
        data.setEndPoint(request.getEndPoint());
        data.setTravelTimeSlot(request.getTravelTimeSlot());
        data.setVehicleType(request.getVehicleType());
        data.setPlateNumber(request.getPlateNumber());
        return data;
    }

    private ReservationData toReservationData(TravelReservation reservation) {
        ReservationData data = new ReservationData();
        data.setStartPoint(reservation.getStartPoint());
        data.setEndPoint(reservation.getEndPoint());
        data.setTravelTimeSlot(reservation.getTravelTimeSlot());
        data.setVehicleType(reservation.getCarType());
        data.setPlateNumber(reservation.getCarLicense());
        return data;
    }

    public static class GreenCodeResponse {
        private boolean success;
        private String qrCode;
        private GreenCodeRequest reservationData;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getQrCode() {
            return qrCode;
        }

        public void setQrCode(String qrCode) {
            this.qrCode = qrCode;
        }

        public GreenCodeRequest getReservationData() {
            return reservationData;
        }

        public void setReservationData(GreenCodeRequest reservationData) {
            this.reservationData = reservationData;
        }
    }

    public static class CertificateResponse {
        private String id;
        private ReservationData reservationData;
        private String qrCode;
        private String createdAt;
        private String status;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public ReservationData getReservationData() {
            return reservationData;
        }

        public void setReservationData(ReservationData reservationData) {
            this.reservationData = reservationData;
        }

        public String getQrCode() {
            return qrCode;
        }

        public void setQrCode(String qrCode) {
            this.qrCode = qrCode;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class ReservationData {
        private String startPoint;
        private String endPoint;
        private String travelTimeSlot;
        private String vehicleType;
        private String plateNumber;

        public String getStartPoint() {
            return startPoint;
        }

        public void setStartPoint(String startPoint) {
            this.startPoint = startPoint;
        }

        public String getEndPoint() {
            return endPoint;
        }

        public void setEndPoint(String endPoint) {
            this.endPoint = endPoint;
        }

        public String getTravelTimeSlot() {
            return travelTimeSlot;
        }

        public void setTravelTimeSlot(String travelTimeSlot) {
            this.travelTimeSlot = travelTimeSlot;
        }

        public String getVehicleType() {
            return vehicleType;
        }

        public void setVehicleType(String vehicleType) {
            this.vehicleType = vehicleType;
        }

        public String getPlateNumber() {
            return plateNumber;
        }

        public void setPlateNumber(String plateNumber) {
            this.plateNumber = plateNumber;
        }
    }
}
