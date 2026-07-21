package com.wut.screenwebsx.Controller;

import com.wut.screencommonsx.Request.NavigationSettlementRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Service.NavigationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/realtimeNavigation", "/api/realtime-navigation"})
@RequiredArgsConstructor
public class NavigationController {
    private final NavigationService navigationService;

    @PostMapping("/reset")
    public ApiResponse<?> resetRealtimeData() {
        return navigationService.resetRealTimeNavigationData();
    }

    @GetMapping("/carInfo")
    public ApiResponse<?> getCarInfo(Authentication authentication) {
        String phone = authentication.getName();
        return navigationService.getCarRealInfo(phone);
    }

    @GetMapping("/windZoneInfo")
    public ApiResponse<List<WindZoneInfo>> getWindZoneInfo() {
        return navigationService.getWindZoneInfo();
    }

    @GetMapping("/overview")
    public ApiResponse<OverviewInfo> getOverview() {
        return navigationService.getOverview();
    }

    @PostMapping("/settlements")
    public ApiResponse<?> settleNavigation(@RequestBody NavigationSettlementRequest request,
                                           Authentication authentication) {
        String phone = authentication == null ? null : authentication.getName();
        return navigationService.settleNavigation(request, phone);
    }

    public static class WindZoneInfo {
        private Integer startPile;
        private Integer endPile;

        public WindZoneInfo(Integer startPile, Integer endPile) {
            this.startPile = startPile;
            this.endPile = endPile;
        }

        public Integer getStartPile() {
            return startPile;
        }

        public Integer getEndPile() {
            return endPile;
        }
    }

    public static class OverviewInfo {
        private Long reservedVehicleCount;
        private Long riskSectionVehicleCount;

        public OverviewInfo(Long reservedVehicleCount, Long riskSectionVehicleCount) {
            this.reservedVehicleCount = reservedVehicleCount;
            this.riskSectionVehicleCount = riskSectionVehicleCount;
        }

        public Long getReservedVehicleCount() {
            return reservedVehicleCount;
        }

        public Long getRiskSectionVehicleCount() {
            return riskSectionVehicleCount;
        }
    }
}
