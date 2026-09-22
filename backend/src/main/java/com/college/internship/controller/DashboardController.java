package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IDashboardService;
import com.college.internship.vo.DashboardSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台数据核心控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "工作台接口", description = "提供四类角色工作台真实动态统计数据查询")
public class DashboardController {

    private final IDashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "获取当前角色工作台指标摘要", description = "按学生、教师、院系负责人、管理员不同角色聚合返回真实数据库业务统计数据")
    public Result<DashboardSummaryVO> getDashboardSummary(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return Result.failed(401, "请先完成身份认证后访问");
        }
        DashboardSummaryVO summary = dashboardService.getSummary(loginUser);
        return Result.success(summary);
    }
}
