package com.wut.screenwebsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screencommonsx.Model.CarInfo;
import com.wut.screencommonsx.Request.VehicleRegisterRequest;
import com.wut.screencommonsx.Response.ApiResponse;

import java.util.List;

public interface CarInfoService extends IService<CarInfo> {
    // 5.1 获取用户所有登记车辆
    ApiResponse<List<CarInfo>> getMyVehicles(String phone);
    // 5.2 登记新车辆
    ApiResponse<?> registerVehicle(VehicleRegisterRequest request, String phone);
}