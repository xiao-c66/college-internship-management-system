package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysSecurityTokenService;
import com.college.internship.vo.SysActiveTokenVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阶段8 在线会话与安全令牌管理控制器 (API-122)
 * 严格按照 implementation_plan.md 原始契约暴露固定端点，不添加任何别名路径
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/security/tokens")
@RequiredArgsConstructor
@Tag(name = "安全会话令牌管理接口", description = "提供在线活跃Token查阅与超管一键强制踢下线")
public class SystemSecurityController {

    private final ISysSecurityTokenService sysSecurityTokenService;

    @GetMapping
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "查询在线活跃会话与Token列表 (API-122)")
    public Result<List<SysActiveTokenVO>> getActiveTokens(@AuthenticationPrincipal LoginUser loginUser) {
        List<SysActiveTokenVO> list = sysSecurityTokenService.getActiveTokens(loginUser);
        return Result.success(list);
    }

    @PostMapping("/{userId}/kick")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "强制踢下线指定用户会话 (API-122)")
    public Result<Void> kickUserToken(@PathVariable("userId") Long userId,
                                      @AuthenticationPrincipal LoginUser loginUser,
                                      HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = request.getRemoteAddr();
        } else if (clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }
        sysSecurityTokenService.kickUserToken(userId, loginUser, clientIp);
        return Result.success("用户历史会话已强制踢下线并即时失效", null);
    }
}
