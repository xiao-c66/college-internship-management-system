package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysLogQueryService;
import com.college.internship.vo.SysLoginLogVO;
import com.college.internship.vo.SysOperationLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阶段8 审计日志检索控制器 (API-120 ~ API-121)
 * 严格按照 implementation_plan.md 原始契约暴露固定端点，不添加任何别名路径
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/logs")
@RequiredArgsConstructor
@Tag(name = "安全审计日志接口", description = "提供登录审计检索与全盘高危操作日志分级脱敏查询")
public class SystemLogController {

    private final ISysLogQueryService sysLogQueryService;

    @GetMapping("/login")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "检索登录安全审计日志 (API-120)")
    public Result<List<SysLoginLogVO>> getLoginLogs(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "status", required = false) Integer status,
            @AuthenticationPrincipal LoginUser loginUser) {
        List<SysLoginLogVO> list = sysLogQueryService.getLoginLogs(pageNum, pageSize, username, status, loginUser);
        return Result.success(list);
    }

    @GetMapping("/operation")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'DEPT_ADMIN')")
    @Operation(summary = "检索高危业务操作日志 (API-121)")
    public Result<List<SysOperationLogVO>> getOperationLogs(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "businessType", required = false) String businessType,
            @RequestParam(value = "status", required = false) Integer status,
            @AuthenticationPrincipal LoginUser loginUser) {
        List<SysOperationLogVO> list = sysLogQueryService.getOperationLogs(pageNum, pageSize, businessType, status, loginUser);
        return Result.success(list);
    }
}
