package com.college.internship.security;

import com.college.internship.entity.SysUser;
import com.college.internship.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 首次改密策略与测试沙箱白名单环境隔离安全测试
 * 包含：生产强阻断、白名单测试账号跳过、非白名单账号 Fail-Close 403 阻断、白名单空/全空格/未配置阻断、生产防误配
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterSecurityTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private HttpServletRequest request;

    @Mock
    private FilterChain filterChain;

    private PasswordPolicyManager passwordPolicyManager;
    private JwtAuthenticationFilter filter;

    private Claims claims;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        passwordPolicyManager = new PasswordPolicyManager();
        filter = new JwtAuthenticationFilter(jwtTokenProvider, sysUserMapper, passwordPolicyManager);

        claims = mock(Claims.class);
        when(claims.get("userId", Long.class)).thenReturn(100L);
        when(claims.get("tokenVersion", Long.class)).thenReturn(1L);
        when(claims.getSubject()).thenReturn("test_student");
        when(claims.get("roleCode", String.class)).thenReturn("ROLE_STUDENT");
        when(claims.get("userType", String.class)).thenReturn("STUDENT");

        when(request.getHeader("Authorization")).thenReturn("Bearer mock.jwt.token");
        when(jwtTokenProvider.parseClaims("mock.jwt.token")).thenReturn(claims);
    }

    @Test
    @DisplayName("生产环境(默认forcePasswordChange=true): status=2 用户访问业务接口返回 403 强阻断")
    void testProdEnvironment_ForcePasswordChange_Status2_BlocksBusinessEndpoints() throws ServletException, IOException {
        passwordPolicyManager.setProductionOverride(true);
        passwordPolicyManager.setForcePasswordChange(true);
        passwordPolicyManager.setTestSkippableUsernamesSet(Set.of("test_student")); // 即使在测试白名单中，生产环境也绝不跳过

        SysUser user = SysUser.builder()
                .id(100L)
                .username("test_student")
                .status(2) // 待改密状态
                .tokenVersion(1L)
                .isDeleted(0)
                .build();
        when(sysUserMapper.selectById(100L)).thenReturn(user);
        when(request.getRequestURI()).thenReturn("/api/v1/internship/tasks");

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(stringWriter.toString().contains("您的账号处于待修改密码状态"));
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "生产强阻断模式下不得设置已认证上下文");
    }

    @Test
    @DisplayName("生产环境防误配: 即使误设forcePasswordChange=false且注入白名单，也必须绝对Fail-Close返回403")
    void testProdEnvironment_MisconfiguredWithFalseSwitchAndWhitelist_BlocksWith403() throws ServletException, IOException {
        passwordPolicyManager.setProductionOverride(true); // 生产运行环境
        passwordPolicyManager.setForcePasswordChange(false); // 误配为 false
        passwordPolicyManager.setTestSkippableUsernamesSet(Set.of("test_student")); // 误配白名单

        SysUser user = SysUser.builder()
                .id(100L)
                .username("test_student")
                .status(2)
                .tokenVersion(1L)
                .isDeleted(0)
                .build();
        when(sysUserMapper.selectById(100L)).thenReturn(user);
        when(request.getRequestURI()).thenReturn("/api/v1/internship/tasks");

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(stringWriter.toString().contains("您的账号处于待修改密码状态"), "生产环境严防配置漂移，必须强制阻断");
    }

    @Test
    @DisplayName("生产环境(默认forcePasswordChange=true): status=2 用户访问改密与登出白名单正常放行")
    void testProdEnvironment_ForcePasswordChange_Status2_AllowsWhitelistedEndpoints() throws ServletException, IOException {
        passwordPolicyManager.setProductionOverride(true);
        passwordPolicyManager.setForcePasswordChange(true);

        SysUser user = SysUser.builder()
                .id(100L)
                .username("test_student")
                .status(2)
                .tokenVersion(1L)
                .isDeleted(0)
                .build();
        when(sysUserMapper.selectById(100L)).thenReturn(user);
        when(sysUserMapper.selectRoleCodesByUserId(100L)).thenReturn(List.of("ROLE_STUDENT"));

        // 白名单接口: 改密
        when(request.getRequestURI()).thenReturn("/api/v1/users/change-password");

        HttpServletResponse response = mock(HttpServletResponse.class);

        filter.doFilter(request, response, filterChain);

        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, times(1)).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication(), "改密接口必须完成身份认证");
    }

    @Test
    @DisplayName("开发/测试环境(forcePasswordChange=false) + 白名单测试账号: status=2 允许跳过首次改密，正常放行")
    void testTestEnvironment_WhitelistedAccount_AllowsSkip_PassesThrough() throws ServletException, IOException {
        passwordPolicyManager.setProductionOverride(false); // 开发/测试环境
        passwordPolicyManager.setForcePasswordChange(false);
        passwordPolicyManager.setTestSkippableUsernamesSet(Set.of("test_student", "teacher_fixture"));

        SysUser user = SysUser.builder()
                .id(100L)
                .username("test_student") // 在白名单中
                .status(2)
                .tokenVersion(1L)
                .isDeleted(0)
                .build();
        when(sysUserMapper.selectById(100L)).thenReturn(user);
        when(sysUserMapper.selectRoleCodesByUserId(100L)).thenReturn(List.of("ROLE_STUDENT"));

        // 普通业务接口
        when(request.getRequestURI()).thenReturn("/api/v1/internship/tasks");

        HttpServletResponse response = mock(HttpServletResponse.class);

        filter.doFilter(request, response, filterChain);

        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, times(1)).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication(), "白名单测试账号允许跳过强制改密，正常建立认证");
    }

    @Test
    @DisplayName("开发/测试环境(forcePasswordChange=false) + 非白名单账号: status=2 仍然返回 403 强阻断 (Fail-Close)")
    void testTestEnvironment_NonWhitelistedAccount_Status2_BlocksWith403() throws ServletException, IOException {
        passwordPolicyManager.setProductionOverride(false); // 开发/测试环境
        passwordPolicyManager.setForcePasswordChange(false);
        passwordPolicyManager.setTestSkippableUsernamesSet(Set.of("special_qa_fixture")); // 白名单中不包含 regular_user

        when(claims.getSubject()).thenReturn("regular_user");

        SysUser user = SysUser.builder()
                .id(100L)
                .username("regular_user") // 不在白名单中
                .status(2) // 待改密
                .tokenVersion(1L)
                .isDeleted(0)
                .build();
        when(sysUserMapper.selectById(100L)).thenReturn(user);
        when(request.getRequestURI()).thenReturn("/api/v1/internship/tasks");

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(stringWriter.toString().contains("您的账号处于待修改密码状态"), "非白名单账号即使在测试环境也必须被阻断");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("开发/测试环境(forcePasswordChange=false) + 白名单显式为空字符串: 必须 Fail-Close 403 强阻断")
    void testTestEnvironment_EmptyWhitelist_BlocksWith403() throws ServletException, IOException {
        passwordPolicyManager.setProductionOverride(false); // 开发/测试环境
        passwordPolicyManager.setForcePasswordChange(false);
        passwordPolicyManager.setTestSkippableUsernames(""); // 白名单显式配置为空

        SysUser user = SysUser.builder()
                .id(101L)
                .username("test_student")
                .status(2)
                .tokenVersion(1L)
                .isDeleted(0)
                .build();
        when(sysUserMapper.selectById(101L)).thenReturn(user);
        when(claims.get("userId", Long.class)).thenReturn(101L);
        when(request.getRequestURI()).thenReturn("/api/v1/internship/tasks");

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(stringWriter.toString().contains("您的账号处于待修改密码状态"), "白名单为空时必须阻断");
    }

    @Test
    @DisplayName("开发/测试环境(forcePasswordChange=false) + 白名单仅包含空格与逗号: 必须 Fail-Close 403 强阻断")
    void testTestEnvironment_WhitespaceOnlyWhitelist_BlocksWith403() throws ServletException, IOException {
        passwordPolicyManager.setProductionOverride(false); // 开发/测试环境
        passwordPolicyManager.setForcePasswordChange(false);
        passwordPolicyManager.setTestSkippableUsernames("   ,   \t,  \n  "); // 全空格

        SysUser user = SysUser.builder()
                .id(102L)
                .username("test_student")
                .status(2)
                .tokenVersion(1L)
                .isDeleted(0)
                .build();
        when(sysUserMapper.selectById(102L)).thenReturn(user);
        when(claims.get("userId", Long.class)).thenReturn(102L);
        when(request.getRequestURI()).thenReturn("/api/v1/internship/tasks");

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(stringWriter.toString().contains("您的账号处于待修改密码状态"));
    }

    @Test
    @DisplayName("配置缺失或缺省未配置: 默认锁定为生产环境 Fail-Close 阻断所有 status=2 账号")
    void testDefaultOrMissingConfig_FailClose_BlocksAllStatus2Accounts() throws ServletException, IOException {
        // 全新构造未注入任何外部白名单且未设置任何 override 的管理器 (默认生产锁定)
        PasswordPolicyManager unconfiguredManager = new PasswordPolicyManager();
        JwtAuthenticationFilter unconfiguredFilter = new JwtAuthenticationFilter(jwtTokenProvider, sysUserMapper, unconfiguredManager);

        SysUser user = SysUser.builder()
                .id(103L)
                .username("any_user")
                .status(2)
                .tokenVersion(1L)
                .isDeleted(0)
                .build();
        when(sysUserMapper.selectById(103L)).thenReturn(user);
        when(claims.get("userId", Long.class)).thenReturn(103L);
        when(request.getRequestURI()).thenReturn("/api/v1/internship/tasks");

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        unconfiguredFilter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(unconfiguredManager.isMandatoryForceChange("any_user", 2));
    }
}
