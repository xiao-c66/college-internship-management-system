package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.SysOperationLog;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.SysOperationLogMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.service.ISysSecurityTokenService;
import com.college.internship.vo.SysActiveTokenVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阶段8 在线活跃会话与Token管理服务实现类 (API-122)
 * 严格落实基于 token_version 原子递增实现即时强制踢下线并向 sys_operation_log 记入审计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysSecurityTokenServiceImpl implements ISysSecurityTokenService {

    private final SysUserMapper sysUserMapper;
    private final SysOperationLogMapper sysOperationLogMapper;
    private final BaseDepartmentMapper baseDepartmentMapper;
    private final ISysOperationLogService operationLogService;

    private final Map<Long, String> deptNameCache = new ConcurrentHashMap<>();

    @Override
    public List<SysActiveTokenVO> getActiveTokens(LoginUser loginUser) {
        // 严格权限：仅限 SYS_ADMIN
        boolean isSysAdmin = loginUser != null && ("SYS_ADMIN".equals(loginUser.getUserType()) ||
                (loginUser.getPermissions() != null && loginUser.getPermissions().contains("ROLE_SYS_ADMIN")));
        if (!isSysAdmin) {
            throw new BusinessException(403, "无权访问在线会话管理，仅限系统管理员");
        }

        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsDeleted, 0)
                .orderByDesc(SysUser::getId));

        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }

        List<SysActiveTokenVO> list = new ArrayList<>(users.size());
        for (SysUser u : users) {
            List<String> roles = sysUserMapper.selectRoleCodesByUserId(u.getId());
            String primaryRole = (roles != null && !roles.isEmpty()) ? roles.get(0) : ("ROLE_" + u.getUserType());

            String deptName = null;
            if (u.getDeptId() != null) {
                deptName = deptNameCache.computeIfAbsent(u.getDeptId(), id -> {
                    try {
                        BaseDepartment d = baseDepartmentMapper.selectById(id);
                        return d != null ? d.getDeptName() : null;
                    } catch (Exception e) {
                        return null;
                    }
                });
            }

            // 查询最近一次登录记录
            List<SysOperationLog> lastLoginLogs = sysOperationLogMapper.selectList(
                    new LambdaQueryWrapper<SysOperationLog>()
                            .eq(SysOperationLog::getOperatorId, u.getId())
                            .eq(SysOperationLog::getBusinessType, "LOGIN")
                            .eq(SysOperationLog::getStatus, 1)
                            .orderByDesc(SysOperationLog::getOperTime)
                            .last("LIMIT 1")
            );

            LocalDateTime lastLoginTime = null;
            String lastLoginIp = null;
            if (lastLoginLogs != null && !lastLoginLogs.isEmpty()) {
                SysOperationLog last = lastLoginLogs.get(0);
                lastLoginTime = last.getOperTime();
                lastLoginIp = last.getOperIp();
            }

            list.add(SysActiveTokenVO.builder()
                    .userId(u.getId())
                    .username(u.getUsername())
                    .realName(u.getRealName())
                    .userType(u.getUserType())
                    .roleCode(primaryRole)
                    .deptId(u.getDeptId())
                    .deptName(deptName)
                    .tokenVersion(u.getTokenVersion() != null ? u.getTokenVersion() : 1L)
                    .lastLoginTime(lastLoginTime)
                    .lastLoginIp(lastLoginIp)
                    .status(u.getStatus())
                    .build());
        }

        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kickUserToken(Long userId, LoginUser loginUser, String clientIp) {
        boolean isSysAdmin = loginUser != null && ("SYS_ADMIN".equals(loginUser.getUserType()) ||
                (loginUser.getPermissions() != null && loginUser.getPermissions().contains("ROLE_SYS_ADMIN")));
        if (!isSysAdmin) {
            throw new BusinessException(403, "无权强制踢下线用户，仅限系统管理员");
        }

        if (userId == null) {
            throw new BusinessException(400, "目标用户ID不可为空");
        }

        // 禁止超级管理员误踢自身
        if (userId.equals(loginUser.getUserId())) {
            throw new BusinessException(400, "不可强制踢出当前正在操作的管理员自身会话");
        }

        SysUser targetUser = sysUserMapper.selectById(userId);
        if (targetUser == null || targetUser.getIsDeleted() == 1) {
            throw new BusinessException(400, "目标用户不存在或已被删除");
        }

        // 核心执行：原子递增 token_version，使得该用户所有已签发 JWT 在下一次请求时被拦截返回 401
        int rows = sysUserMapper.incrementTokenVersion(userId);
        if (rows <= 0) {
            throw new BusinessException(500, "强制踢下线失败，数据库更新无匹配行");
        }

        long newTokenVersion = (targetUser.getTokenVersion() != null ? targetUser.getTokenVersion() : 1L) + 1;

        // 写入安全操作审计日志
        operationLogService.logOperation("强制下线会话", "KICK_OFFLINE", "kickUserToken", "POST",
                loginUser.getUserId(), loginUser.getUsername(),
                "/api/v1/system/security/tokens/" + userId + "/kick",
                clientIp,
                "{\"targetUserId\":" + userId + ",\"targetUsername\":\"" + targetUser.getUsername() + "\"}",
                "{\"status\":\"KICKED\",\"newTokenVersion\":" + newTokenVersion + "}",
                1, null);

        log.info("管理员 [{}] 成功强制踢下线用户 [ID={}, username={}], token_version 已原子递增为 {}",
                loginUser.getUsername(), userId, targetUser.getUsername(), newTokenVersion);
    }
}
