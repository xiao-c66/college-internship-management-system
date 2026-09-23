package com.college.internship.service.impl;

import com.college.internship.common.BusinessException;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.SysOperationLog;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.SysOperationLogMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysLogQueryService;
import com.college.internship.vo.SysLoginLogVO;
import com.college.internship.vo.SysOperationLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阶段8 审计日志查询服务实现类 (API-120 ~ API-121)
 * 严格落实细粒度数据隔离、权限控制、最大PageSize限制与敏感数据分级动态脱敏
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysLogQueryServiceImpl implements ISysLogQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final Pattern COST_MS_PATTERN = Pattern.compile("\"costMs\"\\s*:\\s*(\\d+)");
    private static final Pattern SENSITIVE_JSON_PATTERN = Pattern.compile("(?i)(\"(?:password|oldPassword|newPassword|token|secret|captcha)\"\\s*:\\s*\")[^\"]*(\")");
    private static final Pattern SENSITIVE_KV_PATTERN = Pattern.compile("(?i)((?:password|oldPassword|newPassword|token|secret|captcha)=)[^&,]*");

    private final SysOperationLogMapper sysOperationLogMapper;
    private final SysUserMapper sysUserMapper;
    private final BaseDepartmentMapper baseDepartmentMapper;

    private final Map<Long, String> deptNameCache = new ConcurrentHashMap<>();

    @Override
    public List<SysLoginLogVO> getLoginLogs(Integer pageNum, Integer pageSize, String username, Integer status, LoginUser loginUser) {
        // 1. 严格权限校验：仅限 SYS_ADMIN
        boolean isSysAdmin = loginUser != null && ("SYS_ADMIN".equals(loginUser.getUserType()) ||
                (loginUser.getPermissions() != null && loginUser.getPermissions().contains("ROLE_SYS_ADMIN")));
        if (!isSysAdmin) {
            throw new BusinessException(403, "无权访问登录审计数据，仅限系统管理员");
        }

        // 2. 分页参数校验与安全限制
        int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int offset = (page - 1) * size;

        // 3. 执行查询
        List<SysOperationLog> logs = sysOperationLogMapper.selectLoginLogs(username, status, offset, size);
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }

        List<SysLoginLogVO> result = new ArrayList<>(logs.size());
        for (SysOperationLog logEntity : logs) {
            result.add(SysLoginLogVO.builder()
                    .id(logEntity.getId())
                    .operatorName(logEntity.getOperatorName())
                    .operatorId(logEntity.getOperatorId())
                    .businessType(logEntity.getBusinessType())
                    .operIp(logEntity.getOperIp())
                    .operUrl(logEntity.getOperUrl())
                    .operParam(maskSensitiveParams(logEntity.getOperParam()))
                    .jsonResult(logEntity.getJsonResult())
                    .status(logEntity.getStatus())
                    .errorMsg(logEntity.getErrorMsg())
                    .operTime(logEntity.getOperTime())
                    .build());
        }
        return result;
    }

    @Override
    public List<SysOperationLogVO> getOperationLogs(Integer pageNum, Integer pageSize, String businessType, Integer status, LoginUser loginUser) {
        // 1. 权限拦截：学生与教师直接阻断 403
        if (loginUser == null) {
            throw new BusinessException(403, "用户未认证");
        }
        boolean isSysAdmin = "SYS_ADMIN".equals(loginUser.getUserType()) ||
                (loginUser.getPermissions() != null && loginUser.getPermissions().contains("ROLE_SYS_ADMIN"));
        boolean isDeptAdmin = "DEPT_ADMIN".equals(loginUser.getUserType()) ||
                (loginUser.getPermissions() != null && loginUser.getPermissions().contains("ROLE_DEPT_ADMIN"));
        if (!isSysAdmin && !isDeptAdmin) {
            throw new BusinessException(403, "无权访问操作审计数据，仅限管理员或院系负责人");
        }

        // 2. 分页参数校验与安全限制
        int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int offset = (page - 1) * size;

        // 3. 根据角色分支检索
        List<SysOperationLog> logs;
        if (isSysAdmin) {
            // 超管可检索全校范围
            logs = sysOperationLogMapper.selectAdminOperationLogs(businessType, status, offset, size);
        } else {
            // 院系负责人仅可检索本院系操作日志，且排除系统任务 operator_id = 0
            Long deptId = loginUser.getDeptId();
            if (deptId == null) {
                return Collections.emptyList();
            }
            logs = sysOperationLogMapper.selectDeptOperationLogs(deptId, businessType, status, offset, size);
        }

        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }

        List<SysOperationLogVO> result = new ArrayList<>(logs.size());
        for (SysOperationLog logEntity : logs) {
            // 解析耗时
            Long costMs = extractCostMs(logEntity.getJsonResult());

            // 院系信息填充
            Long deptId = null;
            String deptName = null;
            if (logEntity.getOperatorId() != null && logEntity.getOperatorId() > 0) {
                SysUser opUser = sysUserMapper.selectById(logEntity.getOperatorId());
                if (opUser != null && opUser.getDeptId() != null) {
                    deptId = opUser.getDeptId();
                    deptName = getDeptName(deptId);
                }
            }

            // 数据脱敏处理 (针对 DEPT_ADMIN 实施动态脱敏)
            String safeIp = isSysAdmin ? logEntity.getOperIp() : maskIp(logEntity.getOperIp());
            String safeParam = maskSensitiveParams(logEntity.getOperParam());
            String safeErrorMsg = isSysAdmin ? logEntity.getErrorMsg() : maskErrorMsg(logEntity.getStatus(), logEntity.getErrorMsg());

            result.add(SysOperationLogVO.builder()
                    .id(logEntity.getId())
                    .title(logEntity.getTitle())
                    .businessType(logEntity.getBusinessType())
                    .method(logEntity.getMethod())
                    .requestMethod(logEntity.getRequestMethod())
                    .operatorId(logEntity.getOperatorId())
                    .operatorName(logEntity.getOperatorName())
                    .deptId(deptId)
                    .deptName(deptName)
                    .operUrl(logEntity.getOperUrl())
                    .operIp(safeIp)
                    .operParam(safeParam)
                    .jsonResult(logEntity.getJsonResult())
                    .costMs(costMs)
                    .status(logEntity.getStatus())
                    .errorMsg(safeErrorMsg)
                    .operTime(logEntity.getOperTime())
                    .build());
        }
        return result;
    }

    private Long extractCostMs(String jsonResult) {
        if (!StringUtils.hasText(jsonResult)) {
            return null;
        }
        try {
            Matcher m = COST_MS_PATTERN.matcher(jsonResult);
            if (m.find()) {
                return Long.parseLong(m.group(1));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String getDeptName(Long deptId) {
        if (deptId == null) return null;
        return deptNameCache.computeIfAbsent(deptId, id -> {
            try {
                BaseDepartment dept = baseDepartmentMapper.selectById(id);
                return dept != null ? dept.getDeptName() : null;
            } catch (Exception e) {
                return null;
            }
        });
    }

    private String maskIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "";
        }
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***.***";
        }
        if (ip.contains(":")) {
            return "::***";
        }
        return "***.***.***.***";
    }

    private String maskSensitiveParams(String param) {
        if (!StringUtils.hasText(param)) {
            return param;
        }
        String masked = SENSITIVE_JSON_PATTERN.matcher(param).replaceAll("$1******$2");
        masked = SENSITIVE_KV_PATTERN.matcher(masked).replaceAll("$1******");
        return masked;
    }

    private String maskErrorMsg(Integer status, String errorMsg) {
        if (status == null || status == 1) {
            return null;
        }
        return "业务操作执行失败，详情请联系系统管理员";
    }
}
