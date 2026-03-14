package com.wut.screenwebsx.Controller;


import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Service.NavigationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/realtimeNavigation")
@RequiredArgsConstructor
public class NavigationController {
    private final NavigationService navigationService;

    // 3.1 获取车辆信息
    @GetMapping("/carInfo")
    public ApiResponse<?> getCarInfo(Authentication authentication) {
        String phone = authentication.getName();
        return navigationService.getCarRealInfo(phone);
    }

    // 3.2 获取风区信息集合
    @GetMapping("/windZoneInfo")
    public ApiResponse<List<WindZoneInfo>> getWindZoneInfo() {
        return navigationService.getWindZoneInfo();
    }

    // 风区信息内部类（对接API）
    public static class WindZoneInfo {
        private Integer startPile;
        private Integer endPile;

        public WindZoneInfo(Integer startPile, Integer endPile) {
            this.startPile = startPile;
            this.endPile = endPile;
        }

        public Integer getStartPile() { return startPile; }
        public Integer getEndPile() { return endPile; }
    }
}