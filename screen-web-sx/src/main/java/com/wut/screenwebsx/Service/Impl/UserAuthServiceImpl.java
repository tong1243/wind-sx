package com.wut.screenwebsx.Service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wut.screencommonsx.Exception.BusinessException;
import com.wut.screencommonsx.Model.UserAccount;

import com.wut.screencommonsx.Request.*;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screencommonsx.Util.JwtUtil;
import com.wut.screenwebsx.Service.UserAuthService;
import com.wut.screenwebsx.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAuthService {
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    // 手机号正则
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    // Mock验证码（生产替换为短信服务）
    private static final String MOCK_CODE = "123456";

    @Override
    public ApiResponse<?> login(LoginRequest request) {
        // 验证手机号格式
        if (!PHONE_PATTERN.matcher(request.getPhone()).matches()) {
            throw BusinessException.badRequest("手机号格式不正确");
        }
        // 查询用户
        UserAccount user = getOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getPhone, request.getPhone()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw BusinessException.badRequest("手机号或密码错误");
        }
        // 生成Token
        String token = jwtUtil.generateToken(user.getPhone());
        // 构建返回数据
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserInfo(new UserInfo(user.getPhone(), user.getNickname() == null ? user.getPhone() : user.getNickname()));
        return ApiResponse.success("登录成功", response);
    }

    @Override
    public ApiResponse<?> register(RegisterRequest request) {
        // 验证参数
        if (!PHONE_PATTERN.matcher(request.getPhone()).matches() || !PHONE_PATTERN.matcher(request.getEmergencyContact()).matches()) {
            throw BusinessException.badRequest("手机号格式不正确");
        }
        if (!MOCK_CODE.equals(request.getCode())) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }
        if (request.getPassword().length() < 6) {
            throw BusinessException.badRequest("密码至少6位");
        }
        // 检查手机号是否已注册
        if (exists(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getPhone, request.getPhone()))) {
            throw BusinessException.badRequest("手机号已被注册");
        }
        // 保存用户（密码加密）
        UserAccount user = new UserAccount();
        user.setPhone(request.getPhone());
        user.setEmergencyContact(request.getEmergencyContact());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1); // 账号正常
        save(user);
        // 生成Token
        String token = jwtUtil.generateToken(user.getPhone());
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserInfo(new UserInfo(user.getPhone(), user.getPhone()));
        return ApiResponse.success("注册成功", response);
    }

    @Override
    public ApiResponse<?> sendCode(SendCodeRequest request) {
        if (!PHONE_PATTERN.matcher(request.getPhone()).matches()) {
            throw BusinessException.badRequest("手机号格式不正确");
        }
        // 生产环境：对接短信服务商发送验证码，添加60秒限流
        log.info("向{}发送{}类型验证码，Mock验证码：{}", request.getPhone(), request.getType(), MOCK_CODE);
        return ApiResponse.success("验证码发送成功", null);
    }

    @Override
    public ApiResponse<?> resetPassword(ResetPasswordRequest request) {
        // 验证参数
        if (!PHONE_PATTERN.matcher(request.getPhone()).matches()) {
            throw BusinessException.badRequest("手机号格式不正确");
        }
        if (!MOCK_CODE.equals(request.getCode())) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }
        if (request.getNewPassword().length() < 6) {
            throw BusinessException.badRequest("密码至少6位");
        }
        // 查询用户
        UserAccount user = getOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getPhone, request.getPhone()));
        if (user == null) {
            throw BusinessException.notFound("手机号未注册");
        }
        // 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        updateById(user);
        // 生成新Token
        String token = jwtUtil.generateToken(user.getPhone());
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserInfo(new UserInfo(user.getPhone(), user.getNickname() == null ? user.getPhone() : user.getNickname()));
        return ApiResponse.success("密码重置成功", response);
    }

    @Override
    public ApiResponse<?> changePassword(ChangePasswordRequest request, String phone) {
        // 验证参数
        if (request.getNewPassword().length() < 6) {
            throw BusinessException.badRequest("密码至少6位");
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw BusinessException.badRequest("新密码与旧密码不能相同");
        }
        // 查询用户
        UserAccount user = getOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getPhone, phone));
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw BusinessException.badRequest("旧密码错误");
        }
        // 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        updateById(user);
        return ApiResponse.success("密码修改成功", null);
    }

    // 内部用户信息类
    public static class UserInfo {
        private String phone;
        private String name;

        public UserInfo(String phone, String name) {
            this.phone = phone;
            this.name = name;
        }

        public String getPhone() { return phone; }
        public String getName() { return name; }
    }

    // 登录响应内部类
    public static class LoginResponse {
        private String token;
        private UserInfo userInfo;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public UserInfo getUserInfo() { return userInfo; }
        public void setUserInfo(UserInfo userInfo) { this.userInfo = userInfo; }
    }
}