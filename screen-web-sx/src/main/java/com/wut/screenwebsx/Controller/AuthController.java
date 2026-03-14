package com.wut.screenwebsx.Controller;


import com.wut.screencommonsx.Request.*;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screenwebsx.Service.UserAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {
    private final UserAuthService userAuthService;

    // 1.1 用户登录
    @PostMapping("/login")
    public ApiResponse<?> login(@Valid @RequestBody LoginRequest request) {
        return userAuthService.login(request);
    }

    // 1.2 退出登录
    @PostMapping("/logout")
    public ApiResponse<?> logout() {
        // 前端清除Token即可，后端无需处理
        return ApiResponse.success("退出登录成功", null);
    }

    // 1.3 发送验证码
    @PostMapping("/auth/send-code")
    public ApiResponse<?> sendCode(@Valid @RequestBody SendCodeRequest request) {
        return userAuthService.sendCode(request);
    }

    // 1.4 用户注册
    @PostMapping("/register")
    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest request) {
        return userAuthService.register(request);
    }

    // 1.5 忘记密码（重置密码）
    @PostMapping("/auth/reset-password")
    public ApiResponse<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return userAuthService.resetPassword(request);
    }

    // 2.2 修改密码（个人中心）
    @PostMapping("/personal-center/change-password")
    public ApiResponse<?> changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        // 从Security上下文获取当前用户手机号
        String phone = authentication.getName();
        return userAuthService.changePassword(request, phone);
    }
}