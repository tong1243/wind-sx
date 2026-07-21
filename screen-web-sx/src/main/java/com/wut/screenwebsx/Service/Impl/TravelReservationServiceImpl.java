package com.wut.screenwebsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wut.screencommonsx.Exception.BusinessException;
import com.wut.screencommonsx.Model.TravelReservation;
import com.wut.screencommonsx.Request.GreenCodeRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Controller.NavigationController;
import com.wut.screenwebsx.Mapper.TravelReservationMapper;
import com.wut.screenwebsx.Service.NavigationService;
import com.wut.screenwebsx.Service.TravelReservationService;
import com.wut.screenwebsx.Service.UserNoticePublishService;
import com.wut.screenwebsx.Service.WindControlWindImpactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TravelReservationServiceImpl implements TravelReservationService {
    private final TravelReservationMapper reservationMapper;
    private final UserNoticePublishService userNoticePublishService;
    private final WindControlWindImpactService windControlWindImpactService;
    private final NavigationService navigationService;
    private static final int RESERVATION_PENDING = 2;
    private static final int RESERVATION_APPROVED = 1;
    private static final int RESERVATION_REJECTED = 0;
    private static final int RESERVATION_FINISHED = 3;
    private static final int CONTROL_LEVEL_RED = 1;
    private static final int CONTROL_LEVEL_ORANGE = 2;
    private static final int CONTROL_LEVEL_YELLOW = 3;
    private static final int HONGSHANKOU_SERVICE_AREA_ORDER = 30;
    private static final int HONGSHANKOU_INTERCHANGE_ORDER = 40;
    private static final Map<String, Integer> ROUTE_POINT_ORDER = Map.ofEntries(
            Map.entry("哈密北", 10),
            Map.entry("哈密北出口", 10),
            Map.entry("哈密北收费站", 10),
            Map.entry("哈密北互通", 10),
            Map.entry("一碗泉", 20),
            Map.entry("一碗泉服务区", 20),
            Map.entry("红山口服务区", HONGSHANKOU_SERVICE_AREA_ORDER),
            Map.entry("红山口", HONGSHANKOU_SERVICE_AREA_ORDER),
            Map.entry("红山口互通", HONGSHANKOU_INTERCHANGE_ORDER),
            Map.entry("沙尔湖", 50),
            Map.entry("沙尔湖服务区", 50),
            Map.entry("七克台", 60),
            Map.entry("七可台", 60),
            Map.entry("七克台东互通", 60),
            Map.entry("七可台互通", 60),
            Map.entry("吐峪沟", 70),
            Map.entry("吐峪沟互通", 70)
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> generateGreenCode(GreenCodeRequest request, String phone) {
        String licensePlate = request == null ? null : request.getPlateNumber();
        String travelTimeSlot = request == null ? null : request.getTravelTimeSlot();
        String startPoint = request == null ? null : request.getStartPoint();
        String endPoint = request == null ? null : request.getEndPoint();

        try {
            if (request.getStartPoint().equals(request.getEndPoint())) {
                throw BusinessException.badRequest("起点和终点不能相同");
            }

            TravelReservation reservation = buildReservation(request, phone);
            reservationMapper.insert(reservation);

            userNoticePublishService.publishReservationSubmitSuccess(
                    phone,
                    reservation.getCarLicense(),
                    reservation.getTravelTimeSlot(),
                    reservation.getStartPoint(),
                    reservation.getEndPoint()
            );

            GreenCodeResponse response = new GreenCodeResponse();
            response.setSuccess(true);
            response.setQrCode("data:image/png;base64,iVBORw0KGgoAAAANS...");
            response.setReservationData(request);
            response.setOverview(resolveOverview());
            return ApiResponse.success("预约提交成功", response);
        } catch (BusinessException ex) {
            userNoticePublishService.publishReservationSubmitFailed(
                    phone, licensePlate, travelTimeSlot, startPoint, endPoint, ex.getMessage()
            );
            throw ex;
        } catch (Exception ex) {
            log.error("预约提交失败：phone={}, plate={}", phone, licensePlate, ex);
            userNoticePublishService.publishReservationSubmitFailed(
                    phone, licensePlate, travelTimeSlot, startPoint, endPoint, "系统异常，请稍后重试"
            );
            throw ex;
        }
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
            throw BusinessException.notFound("未找到匹配的预约记录");
        }

        CertificateResponse response = new CertificateResponse();
        response.setId("cert-" + UUID.randomUUID().toString().substring(0, 9));
        response.setReservationData(toReservationData(request));
        response.setQrCode("data:image/png;base64,iVBORw0KGgoAAAANS...");
        response.setCreatedAt(LocalDateTime.now().toString());
        response.setStatus(toCertificateStatus(reservation.getIsPassed()));
        return ApiResponse.success("通行凭证状态获取成功", response);
    }

    @Override
    public ApiResponse<?> getCertificate(String phone) {
        TravelReservation latest = reservationMapper.selectLatestByPhone(phone);
        if (latest == null) {
            throw BusinessException.notFound("未找到通行凭证");
        }

        CertificateResponse response = new CertificateResponse();
        response.setId("cert-" + latest.getId());
        response.setReservationData(toReservationData(latest));
        response.setQrCode("data:image/png;base64,iVBORw0KGgoAAAANS...");
        response.setCreatedAt(latest.getCreateTime().toString());
        response.setStatus(toCertificateStatus(latest.getIsPassed()));
        return ApiResponse.success("\u83b7\u53d6\u6210\u529f", response);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<?> finishReservation(Long reservationId, String phone) {
        TravelReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null || !sameText(reservation.getUserPhone(), phone)) {
            throw BusinessException.notFound("未找到预约记录");
        }
        Integer status = reservation.getIsPassed();
        if (status != null && status != RESERVATION_PENDING && status != RESERVATION_APPROVED && status != RESERVATION_FINISHED) {
            throw BusinessException.badRequest("当前预约状态不允许结束");
        }
        if (status == null || status != RESERVATION_FINISHED) {
            reservation.setIsPassed(RESERVATION_FINISHED);
            reservation.setUpdateTime(LocalDateTime.now());
            reservationMapper.updateById(reservation);
        }
        return ApiResponse.success("预约状态已结束", null);
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
        ReservationDecision decision = decideReservation(request);
        reservation.setIsPassed(decision.isPassed());
        reservation.setRejectReason(decision.rejectReason());
        reservation.setCreateTime(LocalDateTime.now());
        reservation.setUpdateTime(LocalDateTime.now());
        reservation.setExpireTime(LocalDateTime.now().plusHours(24));
        return reservation;
    }

    private ReservationDecision decideReservation(GreenCodeRequest request) {
        if (!includesHongshankou(request)) {
            return ReservationDecision.approved();
        }

        Integer currentControlLevel = resolveCurrentControlLevel();
        if (currentControlLevel == null) {
            return ReservationDecision.pending();
        }
        if (currentControlLevel == CONTROL_LEVEL_RED) {
            return ReservationDecision.rejected("当前红色警戒，所有车辆禁止预约通行");
        }
        if (currentControlLevel == CONTROL_LEVEL_ORANGE) {
            return isSmallVehicle(request.getVehicleType())
                    ? ReservationDecision.approved()
                    : ReservationDecision.rejected("当前橙色警戒，大型车辆禁止预约通行");
        }
        if (currentControlLevel >= CONTROL_LEVEL_YELLOW) {
            return ReservationDecision.approved();
        }
        return ReservationDecision.pending();
    }

    private NavigationController.OverviewInfo resolveOverview() {
        try {
            ApiResponse<NavigationController.OverviewInfo> overviewResponse = navigationService.getOverview();
            return overviewResponse == null ? null : overviewResponse.getData();
        } catch (Exception ex) {
            log.warn("resolve reservation overview failed", ex);
            return null;
        }
    }

    private boolean includesHongshankou(GreenCodeRequest request) {
        String startPoint = request.getStartPoint();
        String endPoint = request.getEndPoint();
        if (containsHongshankou(startPoint) || containsHongshankou(endPoint)) {
            return true;
        }

        Integer startOrder = resolveRoutePointOrder(startPoint);
        Integer endOrder = resolveRoutePointOrder(endPoint);
        if (startOrder == null || endOrder == null) {
            return false;
        }

        int minOrder = Math.min(startOrder, endOrder);
        int maxOrder = Math.max(startOrder, endOrder);
        return minOrder <= HONGSHANKOU_INTERCHANGE_ORDER
                && maxOrder >= HONGSHANKOU_SERVICE_AREA_ORDER;
    }

    private boolean containsHongshankou(String text) {
        return text != null && text.contains("红山口");
    }

    private Integer resolveRoutePointOrder(String point) {
        String normalized = normalizeRoutePoint(point);
        if (normalized.isBlank()) {
            return null;
        }
        Integer exactOrder = ROUTE_POINT_ORDER.get(normalized);
        if (exactOrder != null) {
            return exactOrder;
        }
        for (Map.Entry<String, Integer> entry : ROUTE_POINT_ORDER.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String normalizeRoutePoint(String point) {
        if (point == null) {
            return "";
        }
        return point.trim()
                .replace(" ", "")
                .replace("　", "")
                .replace("（", "(")
                .replace("）", ")")
                .replace("出口收费站", "出口")
                .replace("入口收费站", "入口");
    }

    private Integer resolveCurrentControlLevel() {
        try {
            Map<String, Object> data = windControlWindImpactService.evaluateSpatiotemporalImpact(
                    System.currentTimeMillis(), "real", null);
            Object rawRecords = data == null ? null : data.get("records");
            if (!(rawRecords instanceof List<?> records)) {
                return null;
            }

            Integer level = null;
            for (Object item : records) {
                if (!(item instanceof Map<?, ?> record)) {
                    continue;
                }
                Integer rowLevel = toInteger(record.get("currentControlLevel"));
                if (rowLevel == null) {
                    rowLevel = toInteger(record.get("recommendedControlLevel"));
                }
                if (rowLevel == null || rowLevel <= 0) {
                    continue;
                }
                level = level == null ? rowLevel : Math.min(level, rowLevel);
            }
            return level;
        } catch (Exception ex) {
            log.warn("resolve reservation current control level failed", ex);
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isSmallVehicle(String vehicleType) {
        if (vehicleType == null) {
            return false;
        }
        String value = vehicleType.trim().toLowerCase(Locale.ROOT);
        return "1".equals(value)
                || value.contains("small")
                || value.contains("小")
                || value.contains("客");
    }

    private boolean sameText(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
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
        if (isPassed == RESERVATION_FINISHED) {
            return "finished";
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
        private NavigationController.OverviewInfo overview;

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

        public NavigationController.OverviewInfo getOverview() {
            return overview;
        }

        public void setOverview(NavigationController.OverviewInfo overview) {
            this.overview = overview;
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

    private record ReservationDecision(Integer isPassed, String rejectReason) {
        static ReservationDecision approved() {
            return new ReservationDecision(RESERVATION_APPROVED, null);
        }

        static ReservationDecision pending() {
            return new ReservationDecision(RESERVATION_PENDING, null);
        }

        static ReservationDecision rejected(String rejectReason) {
            return new ReservationDecision(RESERVATION_REJECTED, rejectReason);
        }
    }
}
