package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 忘记密码发送验证码 DTO
 * 遵循安全规范：必须传入已绑定的手机号或邮箱，不得仅凭学号找回
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordSendCodeDTO implements Serializable {

    @NotBlank(message = "登录账号不能为空")
    private String username;

    @NotBlank(message = "绑定的安全手机号或邮箱不能为空")
    private String target;
}
