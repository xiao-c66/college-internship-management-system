package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前登录用户信息出参 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserInfoVO extends BaseVO {

    private Long userId;
    private String username;
    private String realName;
    private String userType;
    private String userNumber;
    private String phone;
    private String email;
    private Long deptId;
    private String deptName;
    private Long majorId;
    private String majorName;
    private Long classId;
    private String className;
    private List<String> roles;
    private List<String> permissions;
}
