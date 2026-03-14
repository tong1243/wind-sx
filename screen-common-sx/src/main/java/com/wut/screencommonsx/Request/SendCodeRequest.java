package com.wut.screencommonsx.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 1.3 发送验证码请求DTO
 */
@Data
public class SendCodeRequest {
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码类型不能为空")
    // type: register-注册, reset-重置密码, login-登录验证
    private String type;
}