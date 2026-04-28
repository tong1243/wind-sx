package com.wut.screenwebsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wut.screencommonsx.Model.CarInfo;
import com.wut.screencommonsx.Model.NavigationSettlement;
import com.wut.screencommonsx.Model.UcCarRealTime;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Controller.NavigationController;
import com.wut.screenwebsx.Mapper.CarInfoMapper;
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
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationServiceImpl implements NavigationService {
    private final UcCarRealTimeMapper ucCarRealTimeMapper;
    private final NavigationSettlementMapper navigationSettlementMapper;
    private final CarInfoMapper carInfoMapper;
    private final ConcurrentHashMap<String, Long> realtimeNavLastVisitMs = new ConcurrentHashMap<>();
    private final Object realtimeResetLock = new Object();

    @Value("${app.realtime-navigation.data-timeout-seconds:120}")
    private long dataTimeoutSeconds;

    @Value("${app.realtime-navigation.auto-reset-entry-gap-seconds:30}")
    private long autoResetEntryGapSeconds;

    @Override
    public ApiResponse<?> resetRealTimeNavigationData() {
        int deleted = ucCarRealTimeMapper.clearAll();
        log.info("Cleared uc_car_real_time rows: {}", deleted);
        return ApiResponse.success("重置成功", deleted);
    }

    @Override
    public ApiResponse<?> getCarRealInfo(String phone) {
        if (phone == null || phone.isBlank()) {
            return ApiResponse.badRequest("用户信息不存在");
        }
        maybeResetRealtimeDataOnEntry(phone);

        int cleaned = ucCarRealTimeMapper.clearHistoryByPhoneKeepLatest(phone);
        if (cleaned > 0) {
            log.info("Cleaned historical uc_car_real_time rows for phone={}, deleted={}", phone, cleaned);
        }

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
        response.setRoad(resolveRoadCode(carRealTime));
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
        String fromCarInfo = resolveVehicleTypeFromCarInfo(licensePlate);
        return "2".equals(fromCarInfo) ? "2" : "1";
    }

    private String resolveVehicleTypeFromCarInfo(String licensePlate) {
        if (licensePlate == null || licensePlate.isBlank()) {
            return null;
        }
        CarInfo carInfo = carInfoMapper.selectOne(new LambdaQueryWrapper<CarInfo>()
                .eq(CarInfo::getLicensePlate, licensePlate.trim())
                .last("LIMIT 1"));
        if (carInfo == null || carInfo.getVehicleType() == null || carInfo.getVehicleType().isBlank()) {
            return null;
        }
        String s = carInfo.getVehicleType().trim();
        if ("1".equals(s) || "2".equals(s)) {
            return s;
        }
        return null;
    }

    private Integer resolveRoadCode(UcCarRealTime carRealTime) {
        if (carRealTime != null && carRealTime.getRoad() != null && carRealTime.getRoad() > 0) {
            return carRealTime.getRoad();
        }
        Integer direction = resolveDirectionCode(carRealTime);
        if (direction == null) {
            return null;
        }
        // 主线道路编号映射（按内部静态表）：
        // direction=1(下行) -> road=2(左幅)
        // direction=2(上行) -> road=1(右幅)
        if (direction == 1) {
            return 2;
        }
        if (direction == 2) {
            return 1;
        }
        return null;
    }

    private void maybeResetRealtimeDataOnEntry(String phone) {
        long now = System.currentTimeMillis();
        long minGapMs = Math.max(autoResetEntryGapSeconds, 5L) * 1000L;
        Long lastVisit = realtimeNavLastVisitMs.put(phone, now);
        if (lastVisit != null && now - lastVisit < minGapMs) {
            return;
        }
        synchronized (realtimeResetLock) {
            int deleted = ucCarRealTimeMapper.clearAll();
            log.info("Auto reset uc_car_real_time on navigation entry, phone={}, deleted={}", phone, deleted);
        }
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
        private Integer road;
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

        public Integer getRoad() {
            return road;
        }

        public void setRoad(Integer road) {
            this.road = road;
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
