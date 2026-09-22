package com.college.internship;

import com.college.internship.dto.LoginDTO;
import com.college.internship.vo.CaptchaVO;
import com.college.internship.service.IAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IAuthService authService;

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
    @DisplayName("7. 全端退出使旧Token即时失效核心闭环验证 (token_version)")
    void testLogoutInvalidatesToken() throws Exception {
        // 1. 学生账号登录
        CaptchaVO captcha = authService.generateCaptcha();
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("student");
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

        // 2. 携带有效 Token 访问工作台数据 -> 正常 200
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userType").value("STUDENT"))
                .andExpect(jsonPath("$.data.metrics.internshipStatus").isNotEmpty());

        // 3. 执行退出登录接口 -> 成功
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 4. 再次携带旧 Token 请求受保护资源 -> 必须被 401 拦截拒绝 (因为 token_version 已原子递增)
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
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
