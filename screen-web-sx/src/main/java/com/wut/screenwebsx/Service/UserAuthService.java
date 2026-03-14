package com.wut.screenwebsx.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wut.screencommonsx.Model.UserAccount;
import com.wut.screencommonsx.Request.*;
import com.wut.screencommonsx.Response.ApiResponse;


public interface UserAuthService extends IService<UserAccount> {
    // 登录
    ApiResponse<?> login(LoginRequest request);
    // 注册
    ApiResponse<?> register(RegisterRequest request);
    // 发送验证码
    ApiResponse<?> sendCode(SendCodeRequest request);
    // 重置密码
    ApiResponse<?> resetPassword(ResetPasswordRequest request);
    // 修改密码
    ApiResponse<?> changePassword(ChangePasswordRequest request, String phone);
}