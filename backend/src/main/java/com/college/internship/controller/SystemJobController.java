package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysJobService;
import com.college.internship.vo.SysJobVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 阶段8受限制定时任务管理控制器 (API-106 ~ API-108)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/jobs")
@RequiredArgsConstructor
@Tag(name = "系统受限定时任务接口", description = "提供白名单受控定时任务列表查询、手动单次触发与状态启停管理")
public class SystemJobController {

    private final ISysJobService sysJobService;

    @GetMapping
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "查询受限白名单定时任务列表 (API-106)")
    public Result<List<SysJobVO>> getJobList(@AuthenticationPrincipal LoginUser loginUser) {
        List<SysJobVO> list = sysJobService.getJobList(loginUser);
        return Result.success(list);
    }

    @PostMapping("/{id}/trigger")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "手动触发定时调度任务单次执行 (API-107)")
    public Result<Map<String, Object>> triggerJob(@PathVariable("id") Long id,
                                                  @AuthenticationPrincipal LoginUser loginUser) {
        Map<String, Object> result = sysJobService.triggerJob(id, loginUser);
        return Result.success(result);
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "启动/暂停受限定时任务 (API-108)")
    public Result<SysJobVO> toggleJob(@PathVariable("id") Long id,
                                      @AuthenticationPrincipal LoginUser loginUser) {
        SysJobVO vo = sysJobService.toggleJob(id, loginUser);
        return Result.success(vo);
    }
}
