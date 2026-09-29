package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户主动修改密码/首次登录强制改密 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordDTO implements Serializable {

    @NotBlank(message = "原密码/临时密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
