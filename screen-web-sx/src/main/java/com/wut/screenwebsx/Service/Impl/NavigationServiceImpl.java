package com.wut.screenwebsx.Service.Impl;

import com.wut.screencommonsx.Model.UcCarRealTime;

import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Controller.NavigationController;
import com.wut.screenwebsx.Service.NavigationService;
import com.wut.screenwebsx.Mapper.UcCarRealTimeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 实时导航服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationServiceImpl implements NavigationService {
    private final UcCarRealTimeMapper ucCarRealTimeMapper;

    @Override
    public ApiResponse<?> getCarRealInfo(String phone) {
        // 查询用户最新的车辆实时数据
        UcCarRealTime carRealTime = ucCarRealTimeMapper.selectLatestByPhone(phone);
        if (carRealTime == null) {
            return ApiResponse.badRequest("暂无车辆实时数据");
        }

        // 构建返回数据（对接API 3.1）
        CarInfoResponse response = new CarInfoResponse();
        response.setSpeed(carRealTime.getRealSpeed());
        response.setLane(carRealTime.getLaneNumber());
        response.setPile(carRealTime.getCurrentPile());
        response.setVehicleType(getCarType(carRealTime.getCarLicense()));

        return ApiResponse.success("获取成功", response);
    }

    @Override
    public ApiResponse<List<NavigationController.WindZoneInfo>> getWindZoneInfo() {
        // Mock风区数据（实际从数据库/配置读取）
        List<NavigationController.WindZoneInfo> windZones = List.of(
                new NavigationController.WindZoneInfo(1000, 1500),
                new NavigationController.WindZoneInfo(2000, 2500),
                new NavigationController.WindZoneInfo(3000, 3500)
        );
        return ApiResponse.success("获取成功", windZones);
    }

    // 辅助方法：根据车牌号判断车型
    private String getCarType(String licensePlate) {
        // 简化逻辑：实际可关联CarInfo表
        return licensePlate.endsWith("挂") ? "货车" : "小型客车";
    }

    // 车辆信息响应内部类（对接API 3.1）
    public static class CarInfoResponse {
        private Integer speed;    // 实时车速
        private Integer lane;     // 车道编号
        private String pile;      // 当前桩号
        private String vehicleType; // 车辆类型

        public Integer getSpeed() { return speed; }
        public void setSpeed(Integer speed) { this.speed = speed; }
        public Integer getLane() { return lane; }
        public void setLane(Integer lane) { this.lane = lane; }
        public String getPile() { return pile; }
        public void setPile(String pile) { this.pile = pile; }
        public String getVehicleType() { return vehicleType; }
        public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    }
}