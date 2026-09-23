package com.college.internship.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 在线活跃会话与令牌管理数据传输对象 (API-122)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "在线活跃会话与Token管理VO")
public class SysActiveTokenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "登录用户名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "用户类型 (STUDENT/TEACHER/DEPT_ADMIN/SYS_ADMIN)")
    private String userType;

    @Schema(description = "主要角色编码")
    private String roleCode;

    @Schema(description = "所属院系ID")
    private Long deptId;

    @Schema(description = "所属院系名称")
    private String deptName;

    @Schema(description = "当前Token版本 (踢下线即自增)")
    private Long tokenVersion;

    @Schema(description = "最近登录/活跃时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "最近登录IP")
    private String lastLoginIp;

    @Schema(description = "账号状态 (1-正常, 0-停用)")
    private Integer status;
}
