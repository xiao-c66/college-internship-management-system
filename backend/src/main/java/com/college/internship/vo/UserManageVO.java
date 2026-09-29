package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户账号管理列表视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserManageVO extends BaseVO {

    private Long id;
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
    private Integer status; // 1: 正常, 0: 停用, 2: 待首次改密/重置改密
    private LocalDateTime createTime;
}
