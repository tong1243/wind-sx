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
            return ApiResponse.badRequest("暂无车辆实时数据");
        }
        if (isRealtimeDataExpired(carRealTime.getReportTime())) {
            return ApiResponse.badRequest("导航已结束或暂无车辆实时数据");
        }
        if (isNavigationFinished(phone, carRealTime.getCarLicense(), carRealTime.getReportTime())) {
            return ApiResponse.badRequest("导航已结束或暂无车辆实时数据");
        }

        CarInfoResponse response = new CarInfoResponse();
        response.setSpeed(carRealTime.getRealSpeed());
        response.setLane(carRealTime.getLaneNumber());
        response.setPile(carRealTime.getCurrentPile());
        response.setDirection(resolveDirectionCode(carRealTime));
        response.setVehicleType(getCarType(carRealTime.getCarLicense()));

        return ApiResponse.success("获取成功", response);
    }

    @Override
    public ApiResponse<List<NavigationController.WindZoneInfo>> getWindZoneInfo() {
        List<NavigationController.WindZoneInfo> windZones = List.of(
                new NavigationController.WindZoneInfo(1000, 1500),
                new NavigationController.WindZoneInfo(2000, 2500),
                new NavigationController.WindZoneInfo(3000, 3500)
        );
        return ApiResponse.success("获取成功", windZones);
    }

    private String getCarType(String licensePlate) {
        if (licensePlate == null || licensePlate.isBlank()) {
            return "小型客车";
        }
        return licensePlate.endsWith("挂") ? "货车" : "小型客车";
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
                || "吐鲁番".equals(s)
                || "上行".equals(s)
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
                || "哈密".equals(s)
                || "下行".equals(s)
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
