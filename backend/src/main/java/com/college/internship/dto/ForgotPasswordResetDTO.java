package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 忘记密码验证码重置 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordResetDTO implements Serializable {

    @NotBlank(message = "登录账号不能为空")
    private String username;

    @NotBlank(message = "绑定的安全手机号或邮箱不能为空")
    private String target;

    @NotBlank(message = "短信/邮箱验证码不能为空")
    private String code;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    @NotBlank(message = "确认新密码不能为空")
    private String confirmPassword;
}
