package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.config.SystemProperties;
import com.college.internship.dto.ConfigUpdateDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysConfigService;
import com.college.internship.util.IpUtils;
import com.college.internship.vo.ConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阶段8 系统运维参数配置控制器 (API-103 ~ API-105)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/configs")
@RequiredArgsConstructor
@Tag(name = "系统运维参数接口", description = "提供系统全局运维参数查询、动态修改及一键出厂重置")
public class SystemConfigController {

    private final ISysConfigService sysConfigService;
    private final SystemProperties systemProperties;

    @GetMapping
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "查询系统全局运维参数列表 (API-103)")
    public Result<List<ConfigVO>> getConfigList(@AuthenticationPrincipal LoginUser loginUser) {
        List<ConfigVO> list = sysConfigService.getConfigList(loginUser);
        return Result.success(list);
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "修改系统全局运维参数 (API-104)")
    public Result<Void> updateConfig(@PathVariable("key") String key,
                                     @Valid @RequestBody ConfigUpdateDTO dto,
                                     HttpServletRequest request,
                                     @AuthenticationPrincipal LoginUser loginUser) {
        String clientIp = IpUtils.getClientIp(request, systemProperties);
        sysConfigService.updateConfig(key, dto, clientIp, loginUser);
        return Result.success();
    }

    @PostMapping("/{key}/reset")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "一键出厂重置指定运维参数 (API-105)")
    public Result<Void> resetConfig(@PathVariable("key") String key,
                                    HttpServletRequest request,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        String clientIp = IpUtils.getClientIp(request, systemProperties);
        sysConfigService.resetConfig(key, clientIp, loginUser);
        return Result.success();
    }
}
