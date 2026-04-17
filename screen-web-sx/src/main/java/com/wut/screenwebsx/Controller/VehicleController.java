package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.VehicleRegisterRequest;
import com.wut.screencommonsx.Request.VehicleUpdateRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Service.CarInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travelReservation")
@RequiredArgsConstructor
public class VehicleController {
    private final CarInfoService carInfoService;

    @GetMapping("/vehicleStatus")
    public ApiResponse<?> getMyVehicles(Authentication authentication) {
        String phone = authentication.getName();
        return carInfoService.getMyVehicles(phone);
    }

    @PostMapping("/registerVehicle")
    public ApiResponse<?> registerVehicle(@Valid @RequestBody VehicleRegisterRequest request,
                                          Authentication authentication) {
        String phone = authentication.getName();
        return carInfoService.registerVehicle(request, phone);
    }

    @PutMapping("/updateVehicle/{licensePlate}")
    public ApiResponse<?> updateVehicle(@PathVariable String licensePlate,
                                        @Valid @RequestBody VehicleUpdateRequest request,
                                        Authentication authentication) {
        String phone = authentication.getName();
        return carInfoService.updateVehicle(licensePlate, request, phone);
    }

    @DeleteMapping("/deleteVehicle/{licensePlate}")
    public ApiResponse<?> deleteVehicle(@PathVariable String licensePlate, Authentication authentication) {
        String phone = authentication.getName();
        return carInfoService.deleteVehicle(licensePlate, phone);
    }
}
