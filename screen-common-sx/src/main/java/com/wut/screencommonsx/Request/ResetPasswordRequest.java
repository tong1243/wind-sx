package com.wut.screencommonsx.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 1.5 忘记密码（重置密码）请求DTO
 */
@Data
public class ResetPasswordRequest {
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^\\S{6,20}$", message = "密码必须6-20位，且不含空格")
    private String newPassword;
}