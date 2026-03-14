package com.wut.screenwebsx.Controller;


import com.wut.screencommonsx.Request.GreenCodeRequest;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Service.TravelReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/travel-reservation")
@RequiredArgsConstructor
public class ReservationController {
    private final TravelReservationService reservationService;

    // 4.1 生成绿码
    @PostMapping("/green-code")
    public ApiResponse<?> generateGreenCode(@Valid @RequestBody GreenCodeRequest request, Authentication authentication) {
        String phone = authentication.getName();
        return reservationService.generateGreenCode(request, phone);
    }

    // 4.2 创建通行凭证
    @PostMapping("/certificate")
    public ApiResponse<?> createCertificate(@Valid @RequestBody GreenCodeRequest request, Authentication authentication) {
        String phone = authentication.getName();
        return reservationService.createCertificate(request, phone);
    }

    // 4.3 获取通行凭证
    @GetMapping("/certificate")
    public ApiResponse<?> getCertificate(Authentication authentication) {
        String phone = authentication.getName();
        return reservationService.getCertificate(phone);
    }
}