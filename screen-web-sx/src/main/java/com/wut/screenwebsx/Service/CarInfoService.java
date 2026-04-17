package com.wut.screenwebsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screencommonsx.Model.CarInfo;
import com.wut.screencommonsx.Request.VehicleRegisterRequest;
import com.wut.screencommonsx.Request.VehicleUpdateRequest;
import com.wut.screencommonsx.Response.ApiResponse;

import java.util.List;

public interface CarInfoService extends IService<CarInfo> {
    ApiResponse<List<CarInfo>> getMyVehicles(String phone);

    ApiResponse<?> registerVehicle(VehicleRegisterRequest request, String phone);

    ApiResponse<?> updateVehicle(String licensePlate, VehicleUpdateRequest request, String phone);

    ApiResponse<?> deleteVehicle(String licensePlate, String phone);
}
