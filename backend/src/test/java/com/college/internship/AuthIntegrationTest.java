package com.college.internship;

import com.college.internship.dto.LoginDTO;
import com.college.internship.vo.CaptchaVO;
import com.college.internship.service.IAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段4：真实登录认证、JWT全生命周期、全端注销与工作台数据集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IAuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<Map<String, Object>> baselineUserTokens;

    @BeforeAll
    void initSuite() {
        // 测试启动防呆校验：必须连接独立测试数据库 internship_db_test
        String currentDb = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"internship_db_test".equalsIgnoreCase(currentDb)) {
            throw new IllegalStateException("【严重安全阻断】当前测试数据库为: [" + currentDb + "]，非 'internship_db_test'！已强制终止测试！");
        }

        // 快照：记录所有账号在测试前的初始 token_version
        baselineUserTokens = jdbcTemplate.queryForList("SELECT id, username, token_version FROM sys_user ORDER BY id");
    }

    @AfterAll
    void cleanSuite() {
        // 严格落实要求4：断言全表账号 token_version 测试前后快照完全一致
        List<Map<String, Object>> currentUserTokens = jdbcTemplate.queryForList("SELECT id, username, token_version FROM sys_user ORDER BY id");
        assertEquals(baselineUserTokens, currentUserTokens, "所有正式账号的 token_version 测试前后必须完全一致，零残留、零副作用");
    }

    @Test
    @DisplayName("1. 验证码生成接口验证")
    void testGetCaptcha() throws Exception {
        mockMvc.perform(get("/api/v1/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.captchaKey").isNotEmpty())
                .andExpect(jsonPath("$.data.captchaCode").isNotEmpty());
    }

    @Test
    @DisplayName("2. 四类角色账号正常登录验证")
    void testFourRolesLogin() throws Exception {
        String[] usernames = {"admin", "deptadmin", "teacher", "student"};
        String[] expectedRoles = {"SYS_ADMIN", "DEPT_ADMIN", "TEACHER", "STUDENT"};

        for (int i = 0; i < usernames.length; i++) {
            String username = usernames[i];
            String expectedRole = expectedRoles[i];

            // 生成有效验证码
            CaptchaVO captcha = authService.generateCaptcha();

            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername(username);
            loginDTO.setPassword("123456");
            loginDTO.setCaptchaKey(captcha.getCaptchaKey());
            loginDTO.setCaptcha(captcha.getCaptchaCode());

            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").isNotEmpty())
                    .andExpect(jsonPath("$.data.username").value(username))
                    .andExpect(jsonPath("$.data.userType").value(expectedRole))
                    .andReturn();

            String responseStr = result.getResponse().getContentAsString();
            JsonNode root = objectMapper.readTree(responseStr);
            String token = root.path("data").path("token").asText();
            assertNotNull(token);
            assertFalse(token.isBlank());
        }
    }

    @Test
    @DisplayName("3. 密码错误登录失败与中文提示验证")
    void testLoginWithWrongPassword() throws Exception {
        CaptchaVO captcha = authService.generateCaptcha();

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("admin");
        loginDTO.setPassword("wrong_password_999");
        loginDTO.setCaptchaKey(captcha.getCaptchaKey());
        loginDTO.setCaptcha(captcha.getCaptchaCode());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名或密码错误，请核对后重试"));
    }

    @Test
    @DisplayName("4. 验证码错误拦截与中文提示验证")
    void testLoginWithWrongCaptcha() throws Exception {
        CaptchaVO captcha = authService.generateCaptcha();

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("admin");
        loginDTO.setPassword("123456");
        loginDTO.setCaptchaKey(captcha.getCaptchaKey());
        loginDTO.setCaptcha("WRONG");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("验证码错误，请重新输入"));
    }

    @Test
    @DisplayName("5. 未携带Token请求受保护资源应返回401")
    void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("认证凭据无效、Token已过期或账号已在其他终端登出，请重新登录"));
    }

    @Test
    @DisplayName("6. 携带伪造或非法Token请求应返回401")
    void testInvalidTokenAccess() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer invalid-tampered-token-value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("7. 全端退出使旧Token即时失效核心闭环验证 (基于独立动态测试学生账号)")
    void testLogoutInvalidatesToken() throws Exception {
        // 1. 动态生成专用测试学生账号 (隔离正式账号 student，实现零读取、零写入、零副作用)
        String dynamicSuffix = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String testUsername = "test_auth_stu_" + dynamicSuffix;
        Long testUserId = null;

        try {
            // BCrypt 散列密码 123456
            String hashPassword = "$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO sys_user (username, password, real_name, user_type, user_number, phone, email, dept_id, major_id, class_id, status, token_version, is_deleted) " +
                                "VALUES (?, ?, '动态认证测试学生', 'STUDENT', ?, '13900009999', 'dynamic_stu@college.edu.cn', 1, 1, 1, 1, 0, 0)",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, testUsername);
                ps.setString(2, hashPassword);
                ps.setString(3, "STU_" + dynamicSuffix);
                return ps;
            }, keyHolder);
            testUserId = keyHolder.getKey().longValue();

            // 动态查询 STUDENT 角色ID 并绑定
            Long studentRoleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_role WHERE role_code = 'ROLE_STUDENT' OR role_code = 'STUDENT' LIMIT 1",
                    Long.class
            );
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", testUserId, studentRoleId);

            // 2. 专用测试学生账号登录
            CaptchaVO captcha = authService.generateCaptcha();
            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername(testUsername);
            loginDTO.setPassword("123456");
            loginDTO.setCaptchaKey(captcha.getCaptchaKey());
            loginDTO.setCaptcha(captcha.getCaptchaCode());

            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginDTO)))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode root = objectMapper.readTree(loginResult.getResponse().getContentAsString());
            String token = root.path("data").path("token").asText();

            // 3. 携带有效 Token 访问工作台数据 -> 正常 200
            mockMvc.perform(get("/api/v1/dashboard/summary")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.userType").value("STUDENT"))
                    .andExpect(jsonPath("$.data.metrics.internshipStatus").isNotEmpty());

            // 4. 执行退出登录接口 -> 成功 (应用层自增该动态测试账号的 token_version 并记录 sys_operation_log)
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            // 验证应用层确实使数据库中该动态测试账号的 token_version 原子自增 +1 (0 -> 1)
            Integer incrementedVersion = jdbcTemplate.queryForObject(
                    "SELECT token_version FROM sys_user WHERE id = ?", Integer.class, testUserId
            );
            assertEquals(1, incrementedVersion, "注销成功后动态测试账号 token_version 必须自增 +1");

            // 5. 再次携带旧 Token 请求受保护资源 -> 必须被 401 拦截拒绝 (因为 token_version 已原子递增)
            mockMvc.perform(get("/api/v1/dashboard/summary")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        } finally {
            // 6. 物理清理专用动态测试账号与角色关系 (零触碰正式账号 student，保留 sys_operation_log 审计日志)
            if (testUserId != null) {
                jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", testUserId);
                jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", testUserId);
            }
        }
    }

    @Test
    @DisplayName("8. 真实数据库工作台指标查询验证")
    void testDashboardMetricsRealData() throws Exception {
        // 管理员登录
        CaptchaVO captcha = authService.generateCaptcha();
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("admin");
        loginDTO.setPassword("123456");
        loginDTO.setCaptchaKey(captcha.getCaptchaKey());
        loginDTO.setCaptcha(captcha.getCaptchaCode());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDTO)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = root.path("data").path("token").asText();

        // 校验管理员工作台数据取自数据库 7 张基础表
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userType").value("SYS_ADMIN"))
                .andExpect(jsonPath("$.data.metrics.totalUsers").isNumber())
                .andExpect(jsonPath("$.data.metrics.totalDepts").isNumber());
    }
}
