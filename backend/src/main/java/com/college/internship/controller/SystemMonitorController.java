package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IPerformanceMonitorService;
import com.college.internship.vo.CacheMonitorVO;
import com.college.internship.vo.ServerMonitorVO;
import com.college.internship.vo.SlowSqlMonitorVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阶段8 性能与运行时监控控制器 (API-116 ~ API-119)
 * 严格按照 implementation_plan.md 原始契约暴露固定端点，不添加任何别名路径
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/monitor")
@RequiredArgsConstructor
@Tag(name = "系统监控接口", description = "提供服务器运行指标、本地Caffeine缓存统计与慢调用分析看板")
public class SystemMonitorController {

    private final IPerformanceMonitorService performanceMonitorService;

    @GetMapping("/server")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "获取服务器与JVM监控指标 (API-116)")
    public Result<ServerMonitorVO> getServerMetrics() {
        ServerMonitorVO vo = performanceMonitorService.getServerMetrics();
        return Result.success(vo);
    }

    @GetMapping("/cache")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "获取有界Caffeine缓存命中率与监控统计 (API-117)")
    public Result<CacheMonitorVO> getCacheMetrics() {
        CacheMonitorVO vo = performanceMonitorService.getCacheMetrics();
        return Result.success(vo);
    }

    @PostMapping("/cache/clear")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "手动清理系统本地缓存 (API-118)")
    public Result<Void> clearCache(@RequestParam(value = "cacheName", required = false) String cacheName,
                                   @AuthenticationPrincipal LoginUser loginUser,
                                   HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = request.getRemoteAddr();
        } else if (clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }
        performanceMonitorService.clearCache(cacheName, loginUser, clientIp);
        return Result.success("本地缓存已成功清空", null);
    }

    @GetMapping("/slow-sql")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "获取慢查询与接口P95/P99性能看板 (API-119)")
    public Result<SlowSqlMonitorVO> getSlowSqlMetrics() {
        SlowSqlMonitorVO vo = performanceMonitorService.getSlowSqlMetrics();
        return Result.success(vo);
    }
}
