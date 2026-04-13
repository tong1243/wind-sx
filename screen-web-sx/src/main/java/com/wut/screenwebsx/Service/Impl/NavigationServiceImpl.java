package com.wut.screenwebsx.Service.Impl;

import com.wut.screencommonsx.Model.NavigationSettlement;
import com.wut.screencommonsx.Model.UcCarRealTime;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Controller.NavigationController;
import com.wut.screenwebsx.Mapper.NavigationSettlementMapper;
import com.wut.screenwebsx.Mapper.UcCarRealTimeMapper;
import com.wut.screenwebsx.Service.NavigationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationServiceImpl implements NavigationService {
    private final UcCarRealTimeMapper ucCarRealTimeMapper;
    private final NavigationSettlementMapper navigationSettlementMapper;

    @Value("${app.realtime-navigation.data-timeout-seconds:120}")
    private long dataTimeoutSeconds;

    @Override
    public ApiResponse<?> getCarRealInfo(String phone) {
        UcCarRealTime carRealTime = ucCarRealTimeMapper.selectLatestByPhone(phone);
        if (carRealTime == null) {
            return ApiResponse.badRequest("\u6682\u65e0\u8f66\u8f86\u5b9e\u65f6\u6570\u636e");
        }
        if (isRealtimeDataExpired(carRealTime.getReportTime())) {
            return ApiResponse.badRequest("\u5bfc\u822a\u5df2\u7ed3\u675f\u6216\u6682\u65e0\u8f66\u8f86\u5b9e\u65f6\u6570\u636e");
        }
        if (isNavigationFinished(phone, carRealTime.getCarLicense(), carRealTime.getReportTime())) {
            return ApiResponse.badRequest("\u5bfc\u822a\u5df2\u7ed3\u675f\u6216\u6682\u65e0\u8f66\u8f86\u5b9e\u65f6\u6570\u636e");
        }

        CarInfoResponse response = new CarInfoResponse();
        response.setSpeed(carRealTime.getRealSpeed());
        response.setLane(carRealTime.getLaneNumber());
        response.setPile(carRealTime.getCurrentPile());
        response.setDirection(resolveDirectionCode(carRealTime));
        response.setVehicleType(getCarType(carRealTime.getCarLicense()));

        return ApiResponse.success("\u83b7\u53d6\u6210\u529f", response);
    }

    @Override
    public ApiResponse<List<NavigationController.WindZoneInfo>> getWindZoneInfo() {
        List<NavigationController.WindZoneInfo> windZones = List.of(
                new NavigationController.WindZoneInfo(1000, 1500),
                new NavigationController.WindZoneInfo(2000, 2500),
                new NavigationController.WindZoneInfo(3000, 3500)
        );
        return ApiResponse.success("\u83b7\u53d6\u6210\u529f", windZones);
    }

    private String getCarType(String licensePlate) {
        if (licensePlate == null || licensePlate.isBlank()) {
            return "\u5c0f\u578b\u5ba2\u8f66";
        }
        return licensePlate.endsWith("\u6302") ? "\u8d27\u8f66" : "\u5c0f\u578b\u5ba2\u8f66";
    }

    private boolean isRealtimeDataExpired(LocalDateTime reportTime) {
        if (reportTime == null) {
            return true;
        }
        long safeTimeout = Math.max(dataTimeoutSeconds, 30L);
        return reportTime.isBefore(LocalDateTime.now().minusSeconds(safeTimeout));
    }

    private boolean isNavigationFinished(String phone, String carLicense, LocalDateTime reportTime) {
        if (phone == null || phone.isBlank() || carLicense == null || carLicense.isBlank()) {
            return false;
        }
        NavigationSettlement latestSettlement = navigationSettlementMapper.selectLatestByPhoneAndCar(phone, carLicense);
        if (latestSettlement == null || latestSettlement.getNavigationEndTime() == null) {
            return false;
        }
        if (reportTime == null) {
            return true;
        }
        return !reportTime.isAfter(latestSettlement.getNavigationEndTime());
    }

    private Integer resolveDirectionCode(UcCarRealTime carRealTime) {
        if (carRealTime == null) {
            return null;
        }
        Integer code = carRealTime.getDirection();
        if (code != null && (code == 1 || code == 2)) {
            return code;
        }
        return toDirectionCode(carRealTime.getDrivingDirection());
    }

    private Integer toDirectionCode(String rawDirection) {
        if (rawDirection == null || rawDirection.isBlank()) {
            return null;
        }
        String s = rawDirection.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(s)
                || "\u5410\u9c81\u756a".equals(s)
                || "\u4e0a\u884c".equals(s)
                || "turpan".equals(s)
                || "tulufan".equals(s)
                || "toez".equals(s)
                || "to_ez".equals(s)
                || "hamimi_to_tuyugou".equals(s)
                || "hami_to_turpan".equals(s)
                || "to_turpan".equals(s)) {
            return 1;
        }
        if ("2".equals(s)
                || "\u54c8\u5bc6".equals(s)
                || "\u4e0b\u884c".equals(s)
                || "hami".equals(s)
                || "towh".equals(s)
                || "to_wh".equals(s)
                || "tuyugou_to_hamimi".equals(s)
                || "turpan_to_hami".equals(s)
                || "to_hami".equals(s)) {
            return 2;
        }
        return null;
    }

    public static class CarInfoResponse {
        private Integer speed;
        private Integer lane;
        private String pile;
        private Integer direction;
        private String vehicleType;

        public Integer getSpeed() {
            return speed;
        }

        public void setSpeed(Integer speed) {
            this.speed = speed;
        }

        public Integer getLane() {
            return lane;
        }

        public void setLane(Integer lane) {
            this.lane = lane;
        }

        public String getPile() {
            return pile;
        }

        public void setPile(String pile) {
            this.pile = pile;
        }

        public Integer getDirection() {
            return direction;
        }

        public void setDirection(Integer direction) {
            this.direction = direction;
        }

        public String getVehicleType() {
            return vehicleType;
        }

        public void setVehicleType(String vehicleType) {
            this.vehicleType = vehicleType;
        }
    }
}

