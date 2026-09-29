package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录成功出参 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoginVO extends BaseVO {

    private String token;
    private String tokenType;
    private Long expiresIn;
    private Long userId;
    private String username;
    private String realName;
    private String userType;
    private String roleCode;
    private Long deptId;
    private String deptName;
    private List<String> permissions;
    private Boolean mustChangePassword;
    private Boolean forcePasswordChange;
}
