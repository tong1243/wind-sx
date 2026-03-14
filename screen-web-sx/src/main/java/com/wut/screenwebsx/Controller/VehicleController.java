package com.wut.screenwebsx.Controller;


import com.wut.screencommonsx.Request.VehicleRegisterRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Service.CarInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/travelReservation")
@RequiredArgsConstructor
public class VehicleController {
    private final CarInfoService carInfoService;

    // 5.1 获取用户的所有登记车辆
    @GetMapping("/vehicleStatus")
    public ApiResponse<?> getMyVehicles(Authentication authentication) {
        String phone = authentication.getName();
        return carInfoService.getMyVehicles(phone);
    }

    // 5.2 登记新车辆
    @PostMapping("/registerVehicle")
    public ApiResponse<?> registerVehicle(@Valid @RequestBody VehicleRegisterRequest request, Authentication authentication) {
        String phone = authentication.getName();
        return carInfoService.registerVehicle(request, phone);
    }
}