package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.LoginDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IAuthService;
import com.college.internship.vo.CaptchaVO;
import com.college.internship.vo.LoginVO;
import com.college.internship.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证与权限核心控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证授权接口", description = "提供验证码获取、用户登录、全端注销以及当前登录信息查询")
public class AuthController {

    private final IAuthService authService;

    @GetMapping("/captcha")
    @Operation(summary = "获取图形验证码", description = "生成5分钟有效的验证码与凭据Key")
    public Result<CaptchaVO> getCaptcha() {
        CaptchaVO captchaVO = authService.generateCaptcha();
        return Result.success("获取验证码成功", captchaVO);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录认证", description = "基于账号、密码与验证码进行认证并签发JWT令牌")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        LoginVO loginVO = authService.login(loginDTO, clientIp);
        return Result.success("登录成功", loginVO);
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "原子自增用户token_version，使该用户所有历史签发Token即时失效")
    public Result<Void> logout(@AuthenticationPrincipal LoginUser loginUser, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        if (loginUser != null) {
            authService.logout(loginUser.getUserId(), clientIp);
        }
        return Result.success("退出登录成功", null);
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户信息", description = "根据已认证JWT令牌获取当前用户的个人信息与角色权限")
    public Result<UserInfoVO> getCurrentUserInfo(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return Result.failed(401, "尚未登录或登录凭证已失效");
        }
        UserInfoVO userInfo = authService.getCurrentUserInfo(loginUser.getUserId());
        return Result.success(userInfo);
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int firstComma = ip.indexOf(',');
            return (firstComma != -1) ? ip.substring(0, firstComma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
