package com.college.internship;

import com.college.internship.dto.ArchiveUnlockDTO;
import com.college.internship.dto.InspectPlanCreateDTO;
import com.college.internship.dto.InspectionSubmitDTO;
import com.college.internship.dto.LoginDTO;
import com.college.internship.dto.MaterialAuditDTO;
import com.college.internship.dto.MaterialSubmitDTO;
import com.college.internship.dto.RectifyReviewDTO;
import com.college.internship.dto.RectifySubmitDTO;
import com.college.internship.dto.ScoreAppealDTO;
import com.college.internship.dto.ScoreArbitrateDTO;
import com.college.internship.dto.ScoreSubmitDTO;
import com.college.internship.dto.WarnFeedbackDTO;
import com.college.internship.dto.WarnHandleDTO;
import com.college.internship.dto.WeeklyReportSaveDTO;
import com.college.internship.service.IAuthService;
import com.college.internship.vo.CaptchaVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段7核心业务自动化集成测试：
 * 严格覆盖第6版审定方案规划的 24 项待实施测试用例 (TEST-P7-01 ~ TEST-P7-24)
 * 采用专用测试数据隔离方案：创建唯一专用测试任务与专用测试学生，严禁使用正式业务 task_id=1 和 student_id=4
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Phase7IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IAuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String deptAdminToken;
    private String teacherToken;
    private String studentToken;

    // 阶段7隔离专用测试实体标识 (落实要求4、5)
    private Long testTaskId;
    private Long testStudentId;
    private String testStudentUsername;

    // 阶段7基准数据快照（包含记录数量、主键清单与关键字段完整比对，落实要求7）
    private int baselineTaskCount;
    private int baselineUserCount;
    private int baselineArchiveCount;
    private int baselineScoreCount;
    private int baselineWarnCount;
    private int baselineMaterialCount;
    private int baselineInspectionCount;
    private int baselinePlanCount;
    private int baselineRectificationCount;
    private int baselineReportCount;
    private int baselineGuidanceCount;
    private int baselineApplyCount;
    private int baselineSignCount;

    private List<Map<String, Object>> baselineTasks;
    private List<Map<String, Object>> baselineUsers;
    private List<Map<String, Object>> baselineTaskStudents;
    private List<Map<String, Object>> baselineArchives;
    private List<Map<String, Object>> baselineScores;
    private List<Map<String, Object>> baselineWarns;
    private List<Map<String, Object>> baselineMaterials;
    private List<Map<String, Object>> baselineInspections;
    private List<Map<String, Object>> baselinePlans;
    private List<Map<String, Object>> baselineRectifications;
    private List<Map<String, Object>> baselineReports;
    private List<Map<String, Object>> baselineGuidances;
    private List<Map<String, Object>> baselineApplies;
    private List<Map<String, Object>> baselineSigns;

    @BeforeAll
    void initSuite() throws Exception {
        // 测试启动防呆校验：必须连接独立测试数据库 internship_db_test
        String currentDb = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"internship_db_test".equalsIgnoreCase(currentDb)) {
            throw new IllegalStateException("【严重安全阻断】当前测试数据库为: [" + currentDb + "]，非 'internship_db_test'！已强制终止测试！");
        }

        // 1. 基准快照 (包括记录数量、主键清单与关键字段完整比对，落实要求7)
        baselineTaskCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_task", Integer.class);
        baselineUserCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
        baselineArchiveCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_archive", Integer.class);
        baselineScoreCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM score_summary", Integer.class);
        baselineWarnCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM warn_ticket", Integer.class);
        baselineMaterialCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM student_material_item", Integer.class);
        baselineInspectionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM midterm_inspection", Integer.class);
        baselinePlanCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM midterm_inspection_plan", Integer.class);
        baselineRectificationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM midterm_rectification", Integer.class);
        baselineReportCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_weekly_report", Integer.class);
        baselineGuidanceCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_guidance_record", Integer.class);
        baselineApplyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_apply", Integer.class);
        baselineSignCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM safety_commitment_sign", Integer.class);

        baselineTasks = jdbcTemplate.queryForList("SELECT id, task_code, task_name, status, weight_enterprise, weight_teacher_process, weight_weekly_report, weight_stage_material, weight_summary, grade_rules_json FROM internship_task ORDER BY id");
        baselineUsers = jdbcTemplate.queryForList("SELECT id, username FROM sys_user ORDER BY id");
        baselineTaskStudents = jdbcTemplate.queryForList("SELECT task_id, student_id, teacher_id FROM internship_task_student ORDER BY task_id, student_id");
        baselineArchives = jdbcTemplate.queryForList("SELECT id, task_id, student_id, status, version FROM internship_archive ORDER BY id");
        baselineScores = jdbcTemplate.queryForList("SELECT id, task_id, student_id, final_score, score_level, status FROM score_summary ORDER BY id");
        baselineWarns = jdbcTemplate.queryForList("SELECT id, task_id, student_id, warn_level, status FROM warn_ticket ORDER BY id");
        baselineMaterials = jdbcTemplate.queryForList("SELECT id, task_id, student_id, material_code, status FROM student_material_item ORDER BY id");
        baselineInspections = jdbcTemplate.queryForList("SELECT id, task_id, student_id, status FROM midterm_inspection ORDER BY id");
        baselinePlans = jdbcTemplate.queryForList("SELECT id, task_id, plan_name FROM midterm_inspection_plan ORDER BY id");
        baselineRectifications = jdbcTemplate.queryForList("SELECT id, task_id, student_id, status FROM midterm_rectification ORDER BY id");
        baselineReports = jdbcTemplate.queryForList("SELECT id, task_id, student_id, status FROM internship_weekly_report ORDER BY id");
        baselineGuidances = jdbcTemplate.queryForList("SELECT id, task_id, student_id FROM internship_guidance_record ORDER BY id");
        baselineApplies = jdbcTemplate.queryForList("SELECT id, task_id, student_id, apply_status FROM internship_apply ORDER BY id");
        baselineSigns = jdbcTemplate.queryForList("SELECT id, task_id, student_id, is_signed FROM safety_commitment_sign ORDER BY id");

        // 2. 落实要求4、5：创建阶段7专用测试学生与专用测试任务，严禁使用正式业务 task_id=1 和 student_id=4
        long suffix = System.currentTimeMillis() % 1000000;
        testStudentUsername = "test_stu_p7_" + suffix;
        String studentNumber = "STU_P7_" + suffix;

        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, real_name, user_type, user_number, dept_id, major_id, class_id, status, is_deleted) " +
                "VALUES (?, '$2a$10$yPGsnNqEVplIMFJzfBoDzO1q9bbT7fcHOAunO48kCu7kJwfRbRDf6', 'P7测试学生', 'STUDENT', ?, 1, 1, 1, 1, 0)",
                testStudentUsername, studentNumber
        );
        testStudentId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, testStudentUsername);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, 4)", testStudentId);

        String testTaskCode = "TASK_P7_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO internship_task (task_code, task_name, dept_id, academic_year, semester, internship_mode, start_date, end_date, status, weekly_frequency, weekly_deadline_day, " +
                "weight_enterprise, weight_teacher_process, weight_weekly_report, weight_stage_material, weight_summary, grade_rules_json) " +
                "VALUES (?, ?, 1, '2025-2026', 2, 'DISTRIBUTED', CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 90 DAY), 'PUBLISHED', 'WEEKLY', 7, " +
                "20.00, 20.00, 20.00, 20.00, 20.00, NULL)",
                testTaskCode, "阶段7隔离专用测试任务_" + suffix
        );
        testTaskId = jdbcTemplate.queryForObject("SELECT id FROM internship_task WHERE task_code = ?", Long.class, testTaskCode);

        jdbcTemplate.update("INSERT INTO internship_task_major (task_id, major_id) VALUES (?, 1)", testTaskId);
        jdbcTemplate.update("INSERT INTO internship_task_class (task_id, class_id) VALUES (?, 1)", testTaskId);
        jdbcTemplate.update(
                "INSERT INTO internship_task_student (task_id, student_id, student_number, student_name, class_id, teacher_id, safety_status) " +
                "VALUES (?, ?, ?, 'P7测试学生', 1, 3, 'COMPLETED')",
                testTaskId, testStudentId, studentNumber
        );

        adminToken = obtainToken("admin");
        deptAdminToken = obtainToken("deptadmin");
        teacherToken = obtainToken("teacher");
        studentToken = obtainToken(testStudentUsername);
    }

    @BeforeEach
    void setUp() throws Exception {
        // 专用测试数据隔离方案：严格限定仅清理专用测试学生在测试任务下的数据，严禁触碰正式业务数据
        jdbcTemplate.execute("DELETE FROM internship_archive WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);
        jdbcTemplate.execute("DELETE FROM score_audit_history WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);
        jdbcTemplate.execute("DELETE FROM score_summary WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);
        jdbcTemplate.execute("DELETE FROM warn_process_history WHERE ticket_id IN (SELECT id FROM warn_ticket WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId + ")");
        jdbcTemplate.execute("DELETE FROM warn_ticket WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);
        jdbcTemplate.execute("DELETE FROM midterm_rectification WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);
        jdbcTemplate.execute("DELETE FROM midterm_inspection WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);
        jdbcTemplate.execute("DELETE FROM midterm_inspection_plan WHERE task_id = " + testTaskId);
        jdbcTemplate.execute("DELETE FROM material_version_history WHERE material_id IN (SELECT id FROM student_material_item WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId + ")");
        jdbcTemplate.execute("DELETE FROM student_material_item WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);
        jdbcTemplate.execute("DELETE FROM internship_weekly_report_history WHERE report_id IN (SELECT id FROM internship_weekly_report WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId + ")");
        jdbcTemplate.execute("DELETE FROM internship_weekly_report WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);
        jdbcTemplate.execute("DELETE FROM internship_guidance_record WHERE task_id = " + testTaskId);
        jdbcTemplate.execute("DELETE FROM safety_commitment_sign WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);
        jdbcTemplate.execute("DELETE FROM apply_audit_history WHERE apply_id IN (SELECT id FROM internship_apply WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId + ")");
        jdbcTemplate.execute("DELETE FROM internship_apply WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);

        // 恢复专用测试任务基础配置 (五项权重和严格等于 100.00%)
        jdbcTemplate.execute("UPDATE internship_task SET " +
                "weight_enterprise = 20.00, weight_teacher_process = 20.00, weight_weekly_report = 20.00, " +
                "weight_stage_material = 20.00, weight_summary = 20.00, grade_rules_json = NULL, " +
                "start_date = CURRENT_DATE, end_date = DATE_ADD(CURRENT_DATE, INTERVAL 90 DAY), " +
                "weekly_frequency = 'WEEKLY', weekly_deadline_day = 7 WHERE id = " + testTaskId);

        // 确保专用测试学生在任务名单中且指导教师为3
        jdbcTemplate.execute("UPDATE internship_task_student SET teacher_id = 3 WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);
    }

    private String obtainToken(String username) throws Exception {
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
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private void prepareApprovedApply(Long taskId, Long studentId) {
        jdbcTemplate.execute("DELETE FROM internship_apply WHERE task_id = " + taskId + " AND student_id = " + studentId);
        jdbcTemplate.execute("INSERT INTO internship_apply (task_id, student_id, student_number, student_name, dept_id, major_id, class_id, " +
                "company_name, job_position, job_address, company_contact_person, company_contact_phone, start_date, end_date, internship_mode, apply_status, is_locked, is_deleted) " +
                "VALUES (" + taskId + ", " + studentId + ", 'STU_P7_" + studentId + "', 'P7测试学生', 1, 1, 1, " +
                "'某某科技创新有限公司', 'Java开发实习生', '杭州市高新园区', '李经理', '13900139000', CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 90 DAY), 'CENTRALIZED', 'APPROVED', 1, 0)");
    }

    // ============================================================================================
    // 阶段材料、实习总结报告与字数强约束 (TEST-P7-01 ~ TEST-P7-03)
    // ============================================================================================

    @Test
    @DisplayName("TEST-P7-01: 阶段材料提报与版本追溯：学生提交材料凭证 -> 导师查验评分 -> 产生版本快照与历史比对 (API-062~064)")
    void testP7_01_MaterialSubmitAndVersionAudit() throws Exception {
        prepareApprovedApply(testTaskId, testStudentId);

        // 1. 学生首次提交三方协议
        MaterialSubmitDTO submitDTO = new MaterialSubmitDTO();
        submitDTO.setTaskId(testTaskId);
        submitDTO.setMaterialCode("TRIPARTITE_AGREEMENT");
        submitDTO.setAttachmentUrl("https://oss.college.edu.cn/vouchers/tripartite_v1.pdf");
        submitDTO.setFileName("三方协议书盖章件_v1.pdf");
        submitDTO.setFileSize(204800L);

        MvcResult submitRes = mockMvc.perform(post("/api/v1/internship/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        Long materialId = objectMapper.readTree(submitRes.getResponse().getContentAsString()).path("data").asLong();

        // 2. 导师查验打分
        MaterialAuditDTO auditDTO = new MaterialAuditDTO();
        auditDTO.setAction("APPROVED");
        auditDTO.setAuditScore(new BigDecimal("90.00"));
        auditDTO.setAuditComment("材料盖章清晰，符合高校实习规范要求");

        mockMvc.perform(post("/api/v1/internship/materials/" + materialId + "/audit")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(auditDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 3. 学生重提材料 (产生第2版本)
        submitDTO.setAttachmentUrl("https://oss.college.edu.cn/vouchers/tripartite_v2.pdf");
        submitDTO.setFileName("三方协议书盖章件_v2.pdf");
        mockMvc.perform(post("/api/v1/internship/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDTO)))
                .andExpect(status().isOk());

        // 4. 查询版本历史追溯 (API-064)
        mockMvc.perform(get("/api/v1/internship/materials/" + materialId + "/versions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[1].version").value(1))
                .andExpect(jsonPath("$.data[1].auditScore").value(90.00));
    }

    @Test
    @DisplayName("TEST-P7-02: 总结报告字数动态门槛阻断：提交字数低于 min-summary-length (1500字) -> 400 明确拦截 (API-062)")
    void testP7_02_SummaryReportMinLengthValidation() throws Exception {
        prepareApprovedApply(testTaskId, testStudentId);

        MaterialSubmitDTO submitDTO = new MaterialSubmitDTO();
        submitDTO.setTaskId(testTaskId);
        submitDTO.setMaterialCode("SUMMARY_REPORT");
        submitDTO.setContentText("这是一份字数过少的实习总结报告正文内容。"); // 远少于 1500 字

        mockMvc.perform(post("/api/v1/internship/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("毕业实习总结报告正文字数不足")));
    }

    @Test
    @DisplayName("TEST-P7-03: 总结报告导师批阅与得分继承：字数达标提报 -> 导师打分通过 -> 状态流转 APPROVED 供成绩模块引用 (API-063)")
    void testP7_03_SummaryReportPassAndAudit() throws Exception {
        prepareApprovedApply(testTaskId, testStudentId);

        // 构造达标的 1500 字总结长文本
        String longText = "实习总结正文内容：在过去的数月实习期间，我严格遵守企事业单位各项规章制度，认真学习专业开发技能。"
                .repeat(35); // 超过 1500 字

        MaterialSubmitDTO submitDTO = new MaterialSubmitDTO();
        submitDTO.setTaskId(testTaskId);
        submitDTO.setMaterialCode("SUMMARY_REPORT");
        submitDTO.setContentText(longText);

        MvcResult res = mockMvc.perform(post("/api/v1/internship/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDTO)))
                .andExpect(status().isOk())
                .andReturn();

        Long id = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").asLong();

        // 导师打分
        MaterialAuditDTO auditDTO = new MaterialAuditDTO();
        auditDTO.setAction("APPROVED");
        auditDTO.setAuditScore(new BigDecimal("95.00"));
        auditDTO.setAuditComment("总结报告结构完整，认知深刻，论述详实");

        mockMvc.perform(post("/api/v1/internship/materials/" + id + "/audit")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(auditDTO)))
                .andExpect(status().isOk());

        // 核验状态流转为 APPROVED 且记录了审核分
        mockMvc.perform(get("/api/v1/internship/materials/" + id)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.auditScore").value(95.00));
    }

    // ============================================================================================
    // 中期检查与整改全流程 (TEST-P7-04 ~ TEST-P7-08)
    // ============================================================================================

    @Test
    @DisplayName("TEST-P7-04: 方案编制与配置校验：院系负责人创建检查方案，抽样比例超限 (如 > 100%) -> 400 阻断 (API-074)")
    void testP7_04_InspectPlanSamplingRatioExceeded() throws Exception {
        InspectPlanCreateDTO dto = new InspectPlanCreateDTO();
        dto.setTaskId(testTaskId);
        dto.setPlanName("2026春季毕业实习中期检查方案");
        dto.setSamplingMode("RANDOM_RATIO");
        dto.setSamplingRatio(new BigDecimal("120.00")); // 非法比例 > 100%
        dto.setStartDate(LocalDate.now());
        dto.setEndDate(LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/v1/internship/inspections/plans")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("抽样比例")));
    }

    @Test
    @DisplayName("TEST-P7-05: 双轨抽样与防重约束：执行抽样算法，验证 uk_plan_student 物理级拦截同一学生重复抽取 (API-075)")
    void testP7_05_SamplingDeduplicationConstraint() throws Exception {
        // 创建合法方案
        InspectPlanCreateDTO dto = new InspectPlanCreateDTO();
        dto.setTaskId(testTaskId);
        dto.setPlanName("中期检查方案_防重验证_" + testTaskId);
        dto.setSamplingRatio(new BigDecimal("100.00"));
        dto.setStartDate(LocalDate.now());
        dto.setEndDate(LocalDate.now().plusDays(30));

        MvcResult planRes = mockMvc.perform(post("/api/v1/internship/inspections/plans")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();
        Long planId = objectMapper.readTree(planRes.getResponse().getContentAsString()).path("data").asLong();

        // 首次执行抽样
        mockMvc.perform(post("/api/v1/internship/inspections/plans/" + planId + "/sample")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk());

        Integer countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM midterm_inspection WHERE plan_id = " + planId + " AND student_id = " + testStudentId, Integer.class);

        // 二次执行抽样，验证 uk_plan_student 物理拦截，不会插入重复记录
        mockMvc.perform(post("/api/v1/internship/inspections/plans/" + planId + "/sample")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk());

        Integer countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM midterm_inspection WHERE plan_id = " + planId + " AND student_id = " + testStudentId, Integer.class);
        assertEquals(countBefore, countAfter);
    }

    @Test
    @DisplayName("TEST-P7-06: 检查越权防御：指导教师尝试为非管辖带教学生录入督导检查记录 -> 403 (API-076)")
    void testP7_06_InspectionCrossTeacherUnauthorized() throws Exception {
        // 创建方案
        Long planId = createValidPlan();

        // 将专用测试学生的指导教师临时调整为 999
        jdbcTemplate.execute("UPDATE internship_task_student SET teacher_id = 999 WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);

        InspectionSubmitDTO submitDTO = new InspectionSubmitDTO();
        submitDTO.setPlanId(planId);
        submitDTO.setStudentId(testStudentId);
        submitDTO.setScore(new BigDecimal("88.00"));
        submitDTO.setHasProblem(0);

        mockMvc.perform(post("/api/v1/internship/inspections")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDTO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("无权为非负责管辖的学生录入督导检查记录")));

        // 恢复绑定
        jdbcTemplate.execute("UPDATE internship_task_student SET teacher_id = 3 WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId);
    }

    @Test
    @DisplayName("TEST-P7-07: 整改闭环全流转：下达整改通知 -> 学生提交材料凭证 -> 教师复核 -> 院系终审关闭 (API-078~081)")
    void testP7_07_RectificationFullLifecycle() throws Exception {
        Long planId = createValidPlan();

        // 1. 教师检查录入突出问题，自动生成整改单
        InspectionSubmitDTO submitDTO = new InspectionSubmitDTO();
        submitDTO.setPlanId(planId);
        submitDTO.setStudentId(testStudentId);
        submitDTO.setScore(new BigDecimal("65.00"));
        submitDTO.setHasProblem(1);
        submitDTO.setProblemDesc("现场走访发现擅自脱岗且未按规定报告，存在实习安全与纪律隐患");

        mockMvc.perform(post("/api/v1/internship/inspections")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDTO)))
                .andExpect(status().isOk());

        Long rectId = jdbcTemplate.queryForObject(
                "SELECT id FROM midterm_rectification WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId + " LIMIT 1", Long.class);
        assertNotNull(rectId);

        // 2. 学生提交整改报告 (字数满足门槛)
        RectifySubmitDTO rectSubmitDTO = new RectifySubmitDTO();
        rectSubmitDTO.setStudentExplanation("已深刻认识到脱岗的严重纪律问题，向企业导师与带教老师做出检讨，并承诺后续全程在岗。");
        rectSubmitDTO.setEvidenceAttachmentUrl("https://oss.college.edu.cn/rectify/self_review.pdf");

        mockMvc.perform(post("/api/v1/internship/rectifications/" + rectId + "/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rectSubmitDTO)))
                .andExpect(status().isOk());

        // 3. 教师复核整改通过
        RectifyReviewDTO reviewDTO = new RectifyReviewDTO();
        reviewDTO.setAction("PASSED");
        reviewDTO.setReviewComment("学生态度诚恳，企业反馈其已恢复正常出勤打卡，同意整改通过");

        mockMvc.perform(post("/api/v1/internship/rectifications/" + rectId + "/review")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewDTO)))
                .andExpect(status().isOk());

        // 4. 院系终审销号闭环
        mockMvc.perform(post("/api/v1/internship/rectifications/" + rectId + "/close")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM midterm_rectification WHERE id = " + rectId, String.class);
        assertEquals("CLOSED", status);
    }

    @Test
    @DisplayName("TEST-P7-08: 闭环防跳跃校验：整改单处于待复核状态时，未经验收尝试直接关闭销号 -> 400 业务阻断 (API-081)")
    void testP7_08_RectificationCloseWithoutReviewValidation() throws Exception {
        Long planId = createValidPlan();

        // 检查下达整改
        InspectionSubmitDTO submitDTO = new InspectionSubmitDTO();
        submitDTO.setPlanId(planId);
        submitDTO.setStudentId(testStudentId);
        submitDTO.setScore(new BigDecimal("60.00"));
        submitDTO.setHasProblem(1);
        submitDTO.setProblemDesc("实习单位考勤记录存在缺勤，需要整改补交请假单");

        mockMvc.perform(post("/api/v1/internship/inspections")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDTO)))
                .andExpect(status().isOk());

        Long rectId = jdbcTemplate.queryForObject(
                "SELECT id FROM midterm_rectification WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId + " LIMIT 1", Long.class);

        // 未经教师复核，院系直接尝试销号 -> 400 拦截
        mockMvc.perform(post("/api/v1/internship/rectifications/" + rectId + "/close")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("尚未经指导教师复核合格")));
    }

    // ============================================================================================
    // 异常预警引擎、双键去重与升级处置 (TEST-P7-09 ~ TEST-P7-14)
    // ============================================================================================

    @Test
    @DisplayName("TEST-P7-09: 手动扫描与防刷流控：学生越权403，10秒内连续调用 -> 429 拦截 (API-085)")
    void testP7_09_WarnScanRoleAndRateLimit() throws Exception {
        // 学生越权调用扫描 -> 403
        mockMvc.perform(post("/api/v1/warn/scan?taskId=" + testTaskId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 导师首次正常扫描 -> 200
        mockMvc.perform(post("/api/v1/warn/scan?taskId=" + testTaskId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 10秒内连续再次触发 -> 429 防刷限流拦截
        mockMvc.perform(post("/api/v1/warn/scan?taskId=" + testTaskId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value(containsString("扫描请求过于频繁")));
    }

    @Test
    @DisplayName("TEST-P7-10: 双键防重物理拦截：同一对象同规则同周期扫描二次命中，uk_active_dedup 物理阻断，工单保持单条")
    void testP7_10_DoubleKeyDeduplication() throws Exception {
        // 测试学生尚未签署安全承诺书，将命中 WARN_01
        jdbcTemplate.execute("DELETE FROM safety_commitment_sign WHERE student_id = " + testStudentId);

        // 直接插入一条活动预警工单模拟首次命中
        String dedupKey = "DEDUP_WARN_01_" + testTaskId + "_" + testStudentId;
        jdbcTemplate.execute("INSERT INTO warn_ticket (ticket_no, task_id, student_id, teacher_id, dept_id, rule_id, rule_version, " +
                "warn_level, warn_title, evidence_snapshot_json, status, current_assignee_id, current_assignee_role, dedup_key, active_dedup_key) " +
                "VALUES ('WT" + testStudentId + "0001', " + testTaskId + ", " + testStudentId + ", 3, 1, 1, 1, 'YELLOW', '未签署安全承诺书', '{}', 'TRIGGERED', 3, 'TEACHER', '" + dedupKey + "', '" + dedupKey + "')");

        Integer countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM warn_ticket WHERE active_dedup_key = '" + dedupKey + "'", Integer.class);
        assertEquals(1, countBefore);

        // 尝试通过数据库唯一键再插入相同 active_dedup_key
        boolean duplicateBlocked = false;
        try {
            jdbcTemplate.execute("INSERT INTO warn_ticket (ticket_no, task_id, student_id, teacher_id, dept_id, rule_id, rule_version, " +
                    "warn_level, warn_title, evidence_snapshot_json, status, current_assignee_id, current_assignee_role, dedup_key, active_dedup_key) " +
                    "VALUES ('WT" + testStudentId + "0002', " + testTaskId + ", " + testStudentId + ", 3, 1, 1, 1, 'YELLOW', '未签署安全承诺书', '{}', 'TRIGGERED', 3, 'TEACHER', '" + dedupKey + "', '" + dedupKey + "')");
        } catch (Exception e) {
            duplicateBlocked = true;
        }
        assertTrue(duplicateBlocked, "数据库 uk_active_dedup 必须物理级拦截重复活动键");
    }

    @Test
    @DisplayName("TEST-P7-11: 学生在线申辩存证：学生针对活动工单提交申诉陈述与凭证，验证证据链与历史流转表写入 (API-089)")
    void testP7_11_StudentFeedbackAuditTrail() throws Exception {
        String dedupKey = "DEDUP_WARN_01_" + testTaskId + "_" + testStudentId;
        jdbcTemplate.execute("INSERT INTO warn_ticket (ticket_no, task_id, student_id, teacher_id, dept_id, rule_id, rule_version, " +
                "warn_level, warn_title, evidence_snapshot_json, status, current_assignee_id, current_assignee_role, dedup_key, active_dedup_key) " +
                "VALUES ('WT" + testStudentId + "0003', " + testTaskId + ", " + testStudentId + ", 3, 1, 1, 1, 'YELLOW', '未签署安全承诺书', '{}', 'TRIGGERED', 3, 'TEACHER', '" + dedupKey + "', '" + dedupKey + "')");
        Long ticketId = jdbcTemplate.queryForObject("SELECT id FROM warn_ticket WHERE ticket_no = 'WT" + testStudentId + "0003'", Long.class);

        WarnFeedbackDTO feedbackDTO = new WarnFeedbackDTO();
        feedbackDTO.setStudentFeedback("因企业外派现场网络异常导致签署延迟，现已补签完成并上传证明");
        feedbackDTO.setAttachmentUrl("https://oss.college.edu.cn/evidence/sign_proof.jpg");

        mockMvc.perform(post("/api/v1/warn/tickets/" + ticketId + "/feedback")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 核验流转历史表中正确记录了 STUDENT_FEEDBACK
        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM warn_process_history WHERE ticket_id = " + ticketId + " AND action = 'STUDENT_FEEDBACK'", Integer.class);
        assertEquals(1, historyCount);
    }

    @Test
    @DisplayName("TEST-P7-12: 误报关闭释放活动键：教师录入误报报告申请误报关闭 -> 终态释放 active_dedup_key 为 NULL (API-090)")
    void testP7_12_FalseAlarmReleaseActiveKey() throws Exception {
        String dedupKey = "DEDUP_WARN_01_" + testTaskId + "_" + testStudentId;
        jdbcTemplate.execute("INSERT INTO warn_ticket (ticket_no, task_id, student_id, teacher_id, dept_id, rule_id, rule_version, " +
                "warn_level, warn_title, evidence_snapshot_json, status, current_assignee_id, current_assignee_role, dedup_key, active_dedup_key) " +
                "VALUES ('WT" + testStudentId + "0004', " + testTaskId + ", " + testStudentId + ", 3, 1, 1, 1, 'YELLOW', '未签署安全承诺书', '{}', 'TRIGGERED', 3, 'TEACHER', '" + dedupKey + "', '" + dedupKey + "')");
        Long ticketId = jdbcTemplate.queryForObject("SELECT id FROM warn_ticket WHERE ticket_no = 'WT" + testStudentId + "0004'", Long.class);

        WarnHandleDTO handleDTO = new WarnHandleDTO();
        handleDTO.setAction("FALSE_ALARM_CLOSED");
        handleDTO.setTeacherInvestigation("经核实该学生已于纸质档案处签署，系统同步延迟，判定为误报予以销号");

        mockMvc.perform(post("/api/v1/warn/tickets/" + ticketId + "/handle")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(handleDTO)))
                .andExpect(status().isOk());

        // 校验 active_dedup_key 被置为 NULL，但 dedup_key 永久留痕
        String activeKey = jdbcTemplate.queryForObject("SELECT active_dedup_key FROM warn_ticket WHERE id = " + ticketId, String.class);
        String historyKey = jdbcTemplate.queryForObject("SELECT dedup_key FROM warn_ticket WHERE id = " + ticketId, String.class);
        assertEquals(null, activeKey);
        assertEquals(dedupKey, historyKey);
    }

    @Test
    @DisplayName("TEST-P7-13: 超时自动升级至院系：模拟超时流转，工单标记 is_upgraded=1，责任人由导师变更为院系负责人")
    void testP7_13_TimeoutAutoUpgrade() throws Exception {
        String dedupKey = "DEDUP_WARN_TIMEOUT_" + testStudentId;
        // 插入一条创建时间为 5 天前的未处置工单
        jdbcTemplate.execute("INSERT INTO warn_ticket (ticket_no, task_id, student_id, teacher_id, dept_id, rule_id, rule_version, " +
                "warn_level, warn_title, evidence_snapshot_json, status, is_upgraded, current_assignee_id, current_assignee_role, dedup_key, active_dedup_key, created_at) " +
                "VALUES ('WT" + testStudentId + "0005', " + testTaskId + ", " + testStudentId + ", 3, 1, 1, 1, 'ORANGE', '连续未交周报超期', '{}', 'TRIGGERED', 0, 3, 'TEACHER', '" + dedupKey + "', '" + dedupKey + "', DATE_SUB(NOW(), INTERVAL 5 DAY))");
        Long ticketId = jdbcTemplate.queryForObject("SELECT id FROM warn_ticket WHERE ticket_no = 'WT" + testStudentId + "0005'", Long.class);

        // 触发超时升级逻辑 (由超管触发一次扫描或接口探测)
        mockMvc.perform(post("/api/v1/warn/scan?taskId=" + testTaskId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Integer isUpgraded = jdbcTemplate.queryForObject("SELECT is_upgraded FROM warn_ticket WHERE id = " + ticketId, Integer.class);
        String assigneeRole = jdbcTemplate.queryForObject("SELECT current_assignee_role FROM warn_ticket WHERE id = " + ticketId, String.class);
        assertEquals(1, isUpgraded);
        assertEquals("DEPT_ADMIN", assigneeRole);
    }

    @Test
    @DisplayName("TEST-P7-14: 预警只读与独立性校验：预警记录不自动扣减学生学业成绩")
    void testP7_14_WarningIndependenceFromScores() throws Exception {
        // 存在活跃预警工单
        String dedupKey = "DEDUP_WARN_INDEP_" + testStudentId;
        jdbcTemplate.execute("INSERT INTO warn_ticket (ticket_no, task_id, student_id, teacher_id, dept_id, rule_id, rule_version, " +
                "warn_level, warn_title, evidence_snapshot_json, status, current_assignee_id, current_assignee_role, dedup_key, active_dedup_key) " +
                "VALUES ('WT" + testStudentId + "0006', " + testTaskId + ", " + testStudentId + ", 3, 1, 1, 1, 'RED', '严重违纪预警', '{}', 'TRIGGERED', 3, 'TEACHER', '" + dedupKey + "', '" + dedupKey + "')");

        // 导师录入正常分数为各 90 分
        ScoreSubmitDTO scoreDTO = new ScoreSubmitDTO();
        scoreDTO.setTaskId(testTaskId);
        scoreDTO.setStudentId(testStudentId);
        scoreDTO.setEnterpriseScore(new BigDecimal("90.00"));
        scoreDTO.setProcessScore(new BigDecimal("90.00"));
        scoreDTO.setWeeklyScore(new BigDecimal("90.00"));
        scoreDTO.setMaterialScore(new BigDecimal("90.00"));
        scoreDTO.setSummaryScore(new BigDecimal("90.00"));

        mockMvc.perform(post("/api/v1/score/summaries")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scoreDTO)))
                .andExpect(status().isOk());

        BigDecimal finalScore = jdbcTemplate.queryForObject(
                "SELECT final_score FROM score_summary WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId, BigDecimal.class);
        assertEquals(new BigDecimal("90.00"), finalScore);
    }

    // ============================================================================================
    // 五维考核、任务规则优先级、快照固化与审计 (TEST-P7-15 ~ TEST-P7-19)
    // ============================================================================================

    @Test
    @DisplayName("TEST-P7-15: 权重校验与单位转换：任务五项权重单位为百分比，总和不等于 100.00% 时创建与评定均抛 400")
    void testP7_15_WeightSumValidation() throws Exception {
        // 破坏专用测试任务五项权重总和为 90%
        jdbcTemplate.execute("UPDATE internship_task SET weight_enterprise = 10.00 WHERE id = " + testTaskId);

        ScoreSubmitDTO scoreDTO = new ScoreSubmitDTO();
        scoreDTO.setTaskId(testTaskId);
        scoreDTO.setStudentId(testStudentId);
        scoreDTO.setEnterpriseScore(new BigDecimal("80.00"));
        scoreDTO.setProcessScore(new BigDecimal("80.00"));
        scoreDTO.setWeeklyScore(new BigDecimal("80.00"));
        scoreDTO.setMaterialScore(new BigDecimal("80.00"));
        scoreDTO.setSummaryScore(new BigDecimal("80.00"));

        mockMvc.perform(post("/api/v1/score/summaries")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scoreDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("五项权重总和必须严格等于 100.00%")));

        // 恢复权重
        jdbcTemplate.execute("UPDATE internship_task SET weight_enterprise = 20.00 WHERE id = " + testTaskId);
    }

    @Test
    @DisplayName("TEST-P7-16: 杜绝0分掩盖与分项NULL校验：分项存在 NULL 尝试提审 -> 400 明确拦截 (API-091)")
    void testP7_16_ScoreNullFieldValidation() throws Exception {
        ScoreSubmitDTO scoreDTO = new ScoreSubmitDTO();
        scoreDTO.setTaskId(testTaskId);
        scoreDTO.setStudentId(testStudentId);
        scoreDTO.setEnterpriseScore(new BigDecimal("85.00"));
        scoreDTO.setProcessScore(new BigDecimal("85.00"));
        scoreDTO.setWeeklyScore(null); // 周报未汇算为 NULL
        scoreDTO.setMaterialScore(new BigDecimal("85.00"));
        scoreDTO.setSummaryScore(new BigDecimal("85.00"));

        mockMvc.perform(post("/api/v1/score/summaries")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scoreDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("TEST-P7-17: SCORE-012 任务级规则优先级与快照固化：配置任务自定义区间，验证加权总分折算及 grade_rule_snapshot_json 正确固化")
    void testP7_17_TaskCustomGradeRuleSnapshot() throws Exception {
        // 设置专用测试任务自定义规则：优秀门槛提高到 92 分
        jdbcTemplate.execute("UPDATE internship_task SET grade_rules_json = '{\"excellentMin\":92.00,\"goodMin\":82.00,\"mediumMin\":72.00,\"passMin\":60.00}' WHERE id = " + testTaskId);

        // 录入五项均为 91 分，若按全局默认(90分)为优秀，但按任务自定义(92分)应判定为良好 (GOOD)
        ScoreSubmitDTO scoreDTO = new ScoreSubmitDTO();
        scoreDTO.setTaskId(testTaskId);
        scoreDTO.setStudentId(testStudentId);
        scoreDTO.setEnterpriseScore(new BigDecimal("91.00"));
        scoreDTO.setProcessScore(new BigDecimal("91.00"));
        scoreDTO.setWeeklyScore(new BigDecimal("91.00"));
        scoreDTO.setMaterialScore(new BigDecimal("91.00"));
        scoreDTO.setSummaryScore(new BigDecimal("91.00"));

        mockMvc.perform(post("/api/v1/score/summaries")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scoreDTO)))
                .andExpect(status().isOk());

        String scoreLevel = jdbcTemplate.queryForObject(
                "SELECT score_level FROM score_summary WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId, String.class);
        String snapshotJson = jdbcTemplate.queryForObject(
                "SELECT grade_rule_snapshot_json FROM score_summary WHERE student_id = " + testStudentId + " AND task_id = " + testTaskId, String.class);

        assertEquals("GOOD", scoreLevel);
        assertTrue(snapshotJson.contains("TASK_CUSTOM"));
        assertTrue(snapshotJson.contains("92.00"));
    }

    @Test
    @DisplayName("TEST-P7-18: 成绩发布后规则不可变防漂移：成绩公示发布后，修改全局或任务配置，已发布成绩等第与快照保持不变")
    void testP7_18_RuleSnapshotImmutableAfterPublish() throws Exception {
        // 录入并发布成绩
        ScoreSubmitDTO scoreDTO = new ScoreSubmitDTO();
        scoreDTO.setTaskId(testTaskId);
        scoreDTO.setStudentId(testStudentId);
        scoreDTO.setEnterpriseScore(new BigDecimal("85.00"));
        scoreDTO.setProcessScore(new BigDecimal("85.00"));
        scoreDTO.setWeeklyScore(new BigDecimal("85.00"));
        scoreDTO.setMaterialScore(new BigDecimal("85.00"));
        scoreDTO.setSummaryScore(new BigDecimal("85.00"));
        scoreDTO.setSubmitToDept(true);

        mockMvc.perform(post("/api/v1/score/summaries")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scoreDTO)))
                .andExpect(status().isOk());

        // 院系发布成绩公示
        mockMvc.perform(post("/api/v1/score/tasks/" + testTaskId + "/publicity")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk());

        // 篡改专用测试任务规则
        jdbcTemplate.execute("UPDATE internship_task SET grade_rules_json = '{\"excellentMin\":99.00,\"goodMin\":95.00,\"mediumMin\":90.00,\"passMin\":88.00}' WHERE id = " + testTaskId);

        // 查询学生成绩详情，核验等级仍为原来的 GOOD，快照不变
        mockMvc.perform(get("/api/v1/score/my?taskId=" + testTaskId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scoreLevel").value("GOOD"));
    }

    @Test
    @DisplayName("TEST-P7-19: 公示申诉与批文调分审计：公示期内提交申诉；院系调分强制填写批文号并触发 sys_operation_log 审计留痕")
    void testP7_19_ScoreAppealArbitrationWithAuditLog() throws Exception {
        // 准备公示中成绩
        ScoreSubmitDTO scoreDTO = new ScoreSubmitDTO();
        scoreDTO.setTaskId(testTaskId);
        scoreDTO.setStudentId(testStudentId);
        scoreDTO.setEnterpriseScore(new BigDecimal("75.00"));
        scoreDTO.setProcessScore(new BigDecimal("75.00"));
        scoreDTO.setWeeklyScore(new BigDecimal("75.00"));
        scoreDTO.setMaterialScore(new BigDecimal("75.00"));
        scoreDTO.setSummaryScore(new BigDecimal("75.00"));
        scoreDTO.setSubmitToDept(true);

        MvcResult scoreRes = mockMvc.perform(post("/api/v1/score/summaries")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scoreDTO)))
                .andExpect(status().isOk())
                .andReturn();
        Long scoreId = objectMapper.readTree(scoreRes.getResponse().getContentAsString()).path("data").asLong();

        mockMvc.perform(post("/api/v1/score/tasks/" + testTaskId + "/publicity")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk());

        // 1. 学生提交申诉 (理由不少于 10 字)
        ScoreAppealDTO appealDTO = new ScoreAppealDTO();
        appealDTO.setScoreId(scoreId);
        appealDTO.setAppealReason("本人过程表现部分与企业导师核定存在偏差，特申请调分复核");
        appealDTO.setAppealAttachmentUrl("https://oss.college.edu.cn/appeal/evidence.pdf");

        MvcResult appealRes = mockMvc.perform(post("/api/v1/score/appeals")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appealDTO)))
                .andExpect(status().isOk())
                .andReturn();
        Long appealId = objectMapper.readTree(appealRes.getResponse().getContentAsString()).path("data").asLong();

        // 2. 院系无红头批文号尝试调分 -> 400 阻断
        ScoreArbitrateDTO arbitrateDTO = new ScoreArbitrateDTO();
        arbitrateDTO.setAction("PASS");
        arbitrateDTO.setProcessScore(new BigDecimal("90.00")); // 调高过程表现分
        arbitrateDTO.setAuditComment("经核实企业鉴定补充证明有效，同意调整");
        arbitrateDTO.setApprovalDocNo(""); // 留空批文号

        mockMvc.perform(post("/api/v1/score/appeals/" + appealId + "/arbitrate")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(arbitrateDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("批文备案号")));

        // 3. 填写红头批文号成功调分
        arbitrateDTO.setApprovalDocNo("EDU-DEPT-2026-088");
        mockMvc.perform(post("/api/v1/score/appeals/" + appealId + "/arbitrate")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(arbitrateDTO)))
                .andExpect(status().isOk());

        // 4. 校验 sys_operation_log 审计留痕
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE title LIKE '%成绩异议申诉裁决调分%'", Integer.class);
        assertTrue(logCount > 0, "必须在 sys_operation_log 写入调分审计流水");
    }

    // ============================================================================================
    // 归档前9项硬核验、特批解锁时效与 ZIP 导出 (TEST-P7-20 ~ TEST-P7-24)
    // ============================================================================================

    @Test
    @DisplayName("TEST-P7-20: 归档诊断一票否决：分别验证未闭环整改、活动预警工单、五维成绩含 NULL 分项触发 400 阻断 (API-098)")
    void testP7_20_ArchiveDiagnosisVeto() throws Exception {
        // 当前未满足9项硬指标，直接尝试归档冻结
        mockMvc.perform(post("/api/v1/archives/freeze?taskId=" + testTaskId + "&studentId=" + testStudentId)
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("归档前置硬条件未全部满足，一票否决")));
    }

    @Test
    @DisplayName("TEST-P7-21: 9项全通与全局只读写保护：归档冻结后，学生修改周报、教师调分、提交材料全部被写保护拦截 400 (API-099)")
    void testP7_21_ArchiveWriteProtection() throws Exception {
        // 模拟 9 项条件全满足环境
        prepareAll9ArchivePrerequisites(testTaskId, testStudentId);

        // 执行归档冻结
        mockMvc.perform(post("/api/v1/archives/freeze?taskId=" + testTaskId + "&studentId=" + testStudentId)
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 1. 学生尝试提交周报 -> 400 拦截
        WeeklyReportSaveDTO weeklyDTO = WeeklyReportSaveDTO.builder()
                .taskId(testTaskId)
                .weekNumber(1)
                .action("SUBMIT")
                .workContent("归档后尝试提交周报内容测试写保护功能是否生效")
                .workSummary("归档后尝试提交周报内容测试写保护功能是否生效")
                .problemEncountered("归档后尝试提交周报内容测试写保护功能是否生效")
                .nextWeekPlan("归档后尝试提交周报内容测试写保护功能是否生效")
                .build();

        mockMvc.perform(post("/api/v1/internship/weekly-reports")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weeklyDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("已归档锁定，处于全局只读写保护状态")));

        // 2. 学生尝试提报材料 -> 400 拦截
        MaterialSubmitDTO matDTO = new MaterialSubmitDTO();
        matDTO.setTaskId(testTaskId);
        matDTO.setMaterialCode("TRIPARTITE_AGREEMENT");
        matDTO.setAttachmentUrl("https://oss.college.edu.cn/vouchers/test.pdf");

        mockMvc.perform(post("/api/v1/internship/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(matDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("已归档锁定")));

        // 3. 教师尝试直接修改成绩 -> 400 拦截
        ScoreSubmitDTO scoreDTO = new ScoreSubmitDTO();
        scoreDTO.setTaskId(testTaskId);
        scoreDTO.setStudentId(testStudentId);
        scoreDTO.setEnterpriseScore(new BigDecimal("99.00"));
        scoreDTO.setProcessScore(new BigDecimal("99.00"));
        scoreDTO.setWeeklyScore(new BigDecimal("99.00"));
        scoreDTO.setMaterialScore(new BigDecimal("99.00"));
        scoreDTO.setSummaryScore(new BigDecimal("99.00"));

        mockMvc.perform(post("/api/v1/score/summaries")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scoreDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("已归档锁定")));
    }

    @Test
    @DisplayName("TEST-P7-22: 超管特批解锁与 sys_operation_log 审计留痕：超管录入批文号解锁，验证时效写入与审计日志记录 (API-102)")
    void testP7_22_SuperAdminSpecialUnlockWithAudit() throws Exception {
        prepareAll9ArchivePrerequisites(testTaskId, testStudentId);

        MvcResult freezeRes = mockMvc.perform(post("/api/v1/archives/freeze?taskId=" + testTaskId + "&studentId=" + testStudentId)
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long archiveId = objectMapper.readTree(freezeRes.getResponse().getContentAsString()).path("data").asLong();

        // 院系端尝试特批解锁 -> 403 (仅超管允许)
        ArchiveUnlockDTO unlockDTO = new ArchiveUnlockDTO();
        unlockDTO.setSpecialUnlockReason("省厅专家督导复查抽查，需重新完善企业鉴定表盖章页");
        unlockDTO.setSpecialDocNo("SPECIAL-DOC-2026-999");

        mockMvc.perform(post("/api/v1/archives/" + archiveId + "/unlock")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlockDTO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 超管特批解锁 -> 200
        mockMvc.perform(post("/api/v1/archives/" + archiveId + "/unlock")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlockDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 校验状态为 SPECIAL_UNLOCKED，且时效写入
        String status = jdbcTemplate.queryForObject("SELECT status FROM internship_archive WHERE id = " + archiveId, String.class);
        assertEquals("SPECIAL_UNLOCKED", status);

        // 校验 sys_operation_log 记录
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE title LIKE '%特批解锁实习电子卷宗%'", Integer.class);
        assertTrue(logCount > 0, "必须在 sys_operation_log 写入特批解锁审计流水");
    }

    @Test
    @DisplayName("TEST-P7-23: 特批解锁 24 小时超时自动重锁：模拟时效过期，自动恢复 ARCHIVED 状态并写回写保护")
    void testP7_23_SpecialUnlockAutoRelockOnExpired() throws Exception {
        prepareAll9ArchivePrerequisites(testTaskId, testStudentId);

        MvcResult freezeRes = mockMvc.perform(post("/api/v1/archives/freeze?taskId=" + testTaskId + "&studentId=" + testStudentId)
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long archiveId = objectMapper.readTree(freezeRes.getResponse().getContentAsString()).path("data").asLong();

        // 超管特批解锁
        ArchiveUnlockDTO unlockDTO = new ArchiveUnlockDTO();
        unlockDTO.setSpecialUnlockReason("调改材料");
        unlockDTO.setSpecialDocNo("DOC-001");
        mockMvc.perform(post("/api/v1/archives/" + archiveId + "/unlock")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlockDTO)))
                .andExpect(status().isOk());

        // 模拟 24 小时已过期：将 unlock_expire_time 调整为 1 小时前
        jdbcTemplate.execute("UPDATE internship_archive SET unlock_expire_time = DATE_SUB(NOW(), INTERVAL 1 HOUR) WHERE id = " + archiveId);

        // 学生此时再次尝试提交材料 -> 触发超时判定，自动重新归档锁定并拦截 400
        MaterialSubmitDTO matDTO = new MaterialSubmitDTO();
        matDTO.setTaskId(testTaskId);
        matDTO.setMaterialCode("TRIPARTITE_AGREEMENT");
        matDTO.setAttachmentUrl("https://oss.college.edu.cn/vouchers/test.pdf");

        mockMvc.perform(post("/api/v1/internship/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(matDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("特批解锁时效已过期，卷宗已自动恢复归档锁定")));

        String currentStatus = jdbcTemplate.queryForObject("SELECT status FROM internship_archive WHERE id = " + archiveId, String.class);
        assertEquals("ARCHIVED", currentStatus);
    }

    @Test
    @DisplayName("TEST-P7-24: 再次归档二次9项核验与 ZIP 导出防抖：调改后二次核验通过再次归档版本自增；测试 ZIP 流式导出及 10s 防刷限流 (API-101)")
    void testP7_24_ReArchiveAndZipExportRateLimit() throws Exception {
        prepareAll9ArchivePrerequisites(testTaskId, testStudentId);

        // 1. 首次归档
        MvcResult freezeRes = mockMvc.perform(post("/api/v1/archives/freeze?taskId=" + testTaskId + "&studentId=" + testStudentId)
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long archiveId = objectMapper.readTree(freezeRes.getResponse().getContentAsString()).path("data").asLong();

        // 2. 超管特批解锁
        ArchiveUnlockDTO unlockDTO = new ArchiveUnlockDTO();
        unlockDTO.setSpecialUnlockReason("重新归档测试");
        unlockDTO.setSpecialDocNo("DOC-002");
        mockMvc.perform(post("/api/v1/archives/" + archiveId + "/unlock")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlockDTO)))
                .andExpect(status().isOk());

        // 3. 再次归档 (版本递增至 2)
        mockMvc.perform(post("/api/v1/archives/freeze?taskId=" + testTaskId + "&studentId=" + testStudentId)
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk());

        Integer version = jdbcTemplate.queryForObject("SELECT version FROM internship_archive WHERE id = " + archiveId, Integer.class);
        assertEquals(2, version);

        // 4. 首次流式导出 ZIP -> 200 OK 并解析校验 7 份标准 PDF 与 manifest.json SHA-256 完整性摘要
        MvcResult exportRes = mockMvc.perform(get("/api/v1/archives/" + archiveId + "/export")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/zip")))
                .andReturn();

        byte[] zipBytes = exportRes.getResponse().getContentAsByteArray();
        assertTrue(zipBytes.length > 0, "导出的 ZIP 字节数组不得为空");

        List<String> entryNames = new ArrayList<>();
        String manifestJson = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                if ("manifest.json".equals(entry.getName())) {
                    manifestJson = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
                zis.closeEntry();
            }
        }

        // 验证 7 份标准 PDF、诊断清单与 manifest
        assertTrue(entryNames.contains("01_学生实习岗位申报材料.pdf"), "ZIP必须包含01号申报材料PDF");
        assertTrue(entryNames.contains("02_安全教育考核与安全承诺书.pdf"), "ZIP必须包含02号安全承诺书PDF");
        assertTrue(entryNames.contains("03_实习周报汇编合集.pdf"), "ZIP必须包含03号周报合集PDF");
        assertTrue(entryNames.contains("04_过程指导走访台账.pdf"), "ZIP必须包含04号过程指导台账PDF");
        assertTrue(entryNames.contains("05_中期检查督导与限期整改单.pdf"), "ZIP必须包含05号中期检查整改PDF");
        assertTrue(entryNames.contains("06_实习总结报告与阶段材料.pdf"), "ZIP必须包含06号总结报告与阶段材料PDF");
        assertTrue(entryNames.contains("07_五维考核评价与成绩综合评定单.pdf"), "ZIP必须包含07号五维成绩单PDF");
        assertTrue(entryNames.contains("08_归档前置核验诊断单.json"), "ZIP必须包含08号核验诊断JSON");
        assertTrue(entryNames.contains("manifest.json"), "ZIP必须包含manifest.json");

        assertNotNull(manifestJson, "manifest.json 内容不得为空");
        assertTrue(manifestJson.contains("SHA-256"), "manifest 必须标明 SHA-256 完整性摘要算法");
        assertFalse(manifestJson.contains("数字签名"), "严禁使用数字签名表述");
        assertFalse(manifestJson.contains("CA签章"), "严禁使用CA签章表述");

        // 5. 10秒内再次流式导出 -> 429 防刷限流拦截
        mockMvc.perform(get("/api/v1/archives/" + archiveId + "/export")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value(containsString("导出请求过于频繁")));

        // 6. SSRF 深度防护断言验证 (拦截 127.0.0.1、localhost 及云元数据 169.254)
        MaterialSubmitDTO ssrfDTO = new MaterialSubmitDTO();
        ssrfDTO.setTaskId(testTaskId);
        ssrfDTO.setMaterialCode("TRIPARTITE_AGREEMENT");
        ssrfDTO.setAttachmentUrl("http://127.0.0.1:8080/internal/secret.pdf");
        mockMvc.perform(post("/api/v1/internship/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ssrfDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("内部回环")));

        ssrfDTO.setAttachmentUrl("http://169.254.169.254/latest/meta-data/");
        mockMvc.perform(post("/api/v1/internship/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ssrfDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("禁止引用")));
    }

    // ============================================================================================
    // 辅助数据准备方法
    // ============================================================================================

    private Long createValidPlan() throws Exception {
        InspectPlanCreateDTO dto = new InspectPlanCreateDTO();
        dto.setTaskId(testTaskId);
        dto.setPlanName("督导测试方案_" + testTaskId);
        dto.setSamplingRatio(new BigDecimal("30.00"));
        dto.setStartDate(LocalDate.now());
        dto.setEndDate(LocalDate.now().plusDays(30));

        MvcResult res = mockMvc.perform(post("/api/v1/internship/inspections/plans")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).path("data").asLong();
    }

    private void prepareAll9ArchivePrerequisites(Long taskId, Long studentId) {
        // 1. 承诺书签署
        jdbcTemplate.execute("DELETE FROM safety_commitment_sign WHERE task_id = " + taskId + " AND student_id = " + studentId);
        jdbcTemplate.execute("INSERT INTO safety_commitment_sign (task_id, student_id, commitment_text, is_signed, sign_ip, sign_time, is_deleted) " +
                "VALUES (" + taskId + ", " + studentId + ", '高校实习安全知晓承诺书', 1, '127.0.0.1', NOW(), 0)");

        // 2. 实习申报 APPROVED
        prepareApprovedApply(taskId, studentId);

        // 3. 周报 REVIEWED
        jdbcTemplate.execute("DELETE FROM internship_weekly_report WHERE task_id = " + taskId + " AND student_id = " + studentId);
        jdbcTemplate.execute("INSERT INTO internship_weekly_report (task_id, student_id, teacher_id, dept_id, week_number, work_content, work_summary, problem_encountered, next_week_plan, status, start_date, end_date, deadline_time) " +
                "VALUES (" + taskId + ", " + studentId + ", 3, 1, 1, '正常按期完成', '正常按期完成', '正常按期完成', '正常按期完成', 'REVIEWED', CURRENT_DATE, CURRENT_DATE, NOW())");

        // 4. 指导台账 (2次)
        jdbcTemplate.execute("DELETE FROM internship_guidance_record WHERE task_id = " + taskId + " AND student_id = " + studentId);
        jdbcTemplate.execute("INSERT INTO internship_guidance_record (task_id, teacher_id, teacher_name, student_id, student_name, dept_id, guidance_type, guidance_date, content_summary, feedback_status) " +
                "VALUES (" + taskId + ", 3, '李教授', " + studentId + ", 'P7测试学生', 1, 'ONSITE', CURRENT_DATE, '现场安全巡查', 'CONFIRMED'), " +
                "(" + taskId + ", 3, '李教授', " + studentId + ", 'P7测试学生', 1, 'ONLINE', CURRENT_DATE, '线上周报辅导', 'CONFIRMED')");

        // 5. 中期检查已完成 (INSPECTED, has_problem = 0)
        jdbcTemplate.execute("DELETE FROM midterm_inspection WHERE task_id = " + taskId + " AND student_id = " + studentId);
        jdbcTemplate.execute("DELETE FROM midterm_inspection_plan WHERE task_id = " + taskId);
        jdbcTemplate.execute("INSERT INTO midterm_inspection_plan (plan_name, task_id, dept_id, sampling_mode, sampling_ratio, start_date, end_date, created_by) " +
                "VALUES ('归档测试方案_" + taskId + "', " + taskId + ", 1, 'RANDOM_RATIO', 20.00, CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY), 2)");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM midterm_inspection_plan WHERE task_id = " + taskId + " ORDER BY id DESC LIMIT 1", Long.class);
        jdbcTemplate.execute("INSERT INTO midterm_inspection (plan_id, task_id, student_id, teacher_id, inspector_id, sampling_batch_no, status, has_problem, score, inspection_date) " +
                "VALUES (" + planId + ", " + taskId + ", " + studentId + ", 3, 2, 'BATCH01', 'INSPECTED', 0, 90.00, NOW())");

        // 6. 无未闭环整改
        jdbcTemplate.execute("DELETE FROM midterm_rectification WHERE task_id = " + taskId + " AND student_id = " + studentId);

        // 7. 无未闭环预警工单
        jdbcTemplate.execute("DELETE FROM warn_ticket WHERE task_id = " + taskId + " AND student_id = " + studentId);

        // 8. 阶段材料 APPROVED
        jdbcTemplate.execute("DELETE FROM student_material_item WHERE task_id = " + taskId + " AND student_id = " + studentId);
        jdbcTemplate.execute("INSERT INTO student_material_item (task_id, student_id, material_code, material_name, material_type, status, audit_score) " +
                "VALUES (" + taskId + ", " + studentId + ", 'TRIPARTITE_AGREEMENT', '三方协议', 'VOUCHER_FILE', 'APPROVED', 92.00)");

        // 9. 五维成绩 PUBLISHED 且分项非空
        jdbcTemplate.execute("DELETE FROM score_summary WHERE task_id = " + taskId + " AND student_id = " + studentId);
        jdbcTemplate.execute("INSERT INTO score_summary (task_id, student_id, teacher_id, dept_id, enterprise_score, process_score, weekly_score, material_score, summary_score, final_score, score_level, grade_rule_snapshot_json, status) " +
                "VALUES (" + taskId + ", " + studentId + ", 3, 1, 88.00, 90.00, 92.00, 90.00, 95.00, 91.00, 'GOOD', '{\"source\":\"GLOBAL_DEFAULT\",\"rules\":{\"excellentMin\":90.00,\"goodMin\":80.00,\"mediumMin\":70.00,\"passMin\":60.00}}', 'PUBLISHED')");
    }

    @AfterAll
    void cleanSuite() {
        // 严格落实要求5：测试结束后按依赖反序完整清理专用测试数据
        if (testTaskId != null && testStudentId != null) {
            jdbcTemplate.execute("DELETE FROM internship_archive WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM score_audit_history WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM score_summary WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM warn_process_history WHERE ticket_id IN (SELECT id FROM warn_ticket WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId + ")");
            jdbcTemplate.execute("DELETE FROM warn_ticket WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM midterm_rectification WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM midterm_inspection WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM midterm_inspection_plan WHERE task_id = " + testTaskId);
            jdbcTemplate.execute("DELETE FROM material_version_history WHERE material_id IN (SELECT id FROM student_material_item WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId + ")");
            jdbcTemplate.execute("DELETE FROM student_material_item WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM internship_weekly_report_history WHERE report_id IN (SELECT id FROM internship_weekly_report WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId + ")");
            jdbcTemplate.execute("DELETE FROM internship_weekly_report WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM internship_guidance_record WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM safety_commitment_sign WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM apply_audit_history WHERE apply_id IN (SELECT id FROM internship_apply WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId + ")");
            jdbcTemplate.execute("DELETE FROM internship_apply WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM internship_task_student WHERE task_id = " + testTaskId + " OR student_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM internship_task_class WHERE task_id = " + testTaskId);
            jdbcTemplate.execute("DELETE FROM internship_task_major WHERE task_id = " + testTaskId);
            jdbcTemplate.execute("DELETE FROM internship_task WHERE id = " + testTaskId);
            jdbcTemplate.execute("DELETE FROM sys_user_role WHERE user_id = " + testStudentId);
            jdbcTemplate.execute("DELETE FROM sys_user WHERE id = " + testStudentId);
        }

        // 严格落实要求7：断言测试前后数据快照一致性（比对主键、关键字段与记录数量）
        Integer currentTaskCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_task", Integer.class);
        Integer currentUserCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
        Integer currentArchiveCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_archive", Integer.class);
        Integer currentScoreCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM score_summary", Integer.class);
        Integer currentWarnCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM warn_ticket", Integer.class);
        Integer currentMaterialCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM student_material_item", Integer.class);
        Integer currentInspectionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM midterm_inspection", Integer.class);
        Integer currentPlanCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM midterm_inspection_plan", Integer.class);
        Integer currentRectificationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM midterm_rectification", Integer.class);
        Integer currentReportCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_weekly_report", Integer.class);
        Integer currentGuidanceCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_guidance_record", Integer.class);
        Integer currentApplyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_apply", Integer.class);
        Integer currentSignCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM safety_commitment_sign", Integer.class);

        assertEquals(baselineTaskCount, currentTaskCount, "任务总数测试前后必须完全一致");
        assertEquals(baselineUserCount, currentUserCount, "用户总数测试前后必须完全一致");
        assertEquals(baselineArchiveCount, currentArchiveCount, "归档卷宗总数测试前后必须完全一致");
        assertEquals(baselineScoreCount, currentScoreCount, "成绩总数测试前后必须完全一致");
        assertEquals(baselineWarnCount, currentWarnCount, "预警工单总数测试前后必须完全一致");
        assertEquals(baselineMaterialCount, currentMaterialCount, "阶段材料总数测试前后必须完全一致");
        assertEquals(baselineInspectionCount, currentInspectionCount, "中期检查总数测试前后必须完全一致");
        assertEquals(baselinePlanCount, currentPlanCount, "检查方案总数测试前后必须完全一致");
        assertEquals(baselineRectificationCount, currentRectificationCount, "整改单总数测试前后必须完全一致");
        assertEquals(baselineReportCount, currentReportCount, "周报总数测试前后必须完全一致");
        assertEquals(baselineGuidanceCount, currentGuidanceCount, "指导台账总数测试前后必须完全一致");
        assertEquals(baselineApplyCount, currentApplyCount, "实习申报总数测试前后必须完全一致");
        assertEquals(baselineSignCount, currentSignCount, "安全承诺书签署总数测试前后必须完全一致");

        List<Map<String, Object>> currentTasks = jdbcTemplate.queryForList("SELECT id, task_code, task_name, status, weight_enterprise, weight_teacher_process, weight_weekly_report, weight_stage_material, weight_summary, grade_rules_json FROM internship_task ORDER BY id");
        List<Map<String, Object>> currentUsers = jdbcTemplate.queryForList("SELECT id, username FROM sys_user ORDER BY id");
        List<Map<String, Object>> currentTaskStudents = jdbcTemplate.queryForList("SELECT task_id, student_id, teacher_id FROM internship_task_student ORDER BY task_id, student_id");
        List<Map<String, Object>> currentArchives = jdbcTemplate.queryForList("SELECT id, task_id, student_id, status, version FROM internship_archive ORDER BY id");
        List<Map<String, Object>> currentScores = jdbcTemplate.queryForList("SELECT id, task_id, student_id, final_score, score_level, status FROM score_summary ORDER BY id");
        List<Map<String, Object>> currentWarns = jdbcTemplate.queryForList("SELECT id, task_id, student_id, warn_level, status FROM warn_ticket ORDER BY id");
        List<Map<String, Object>> currentMaterials = jdbcTemplate.queryForList("SELECT id, task_id, student_id, material_code, status FROM student_material_item ORDER BY id");
        List<Map<String, Object>> currentInspections = jdbcTemplate.queryForList("SELECT id, task_id, student_id, status FROM midterm_inspection ORDER BY id");
        List<Map<String, Object>> currentPlans = jdbcTemplate.queryForList("SELECT id, task_id, plan_name FROM midterm_inspection_plan ORDER BY id");
        List<Map<String, Object>> currentRectifications = jdbcTemplate.queryForList("SELECT id, task_id, student_id, status FROM midterm_rectification ORDER BY id");
        List<Map<String, Object>> currentReports = jdbcTemplate.queryForList("SELECT id, task_id, student_id, status FROM internship_weekly_report ORDER BY id");
        List<Map<String, Object>> currentGuidances = jdbcTemplate.queryForList("SELECT id, task_id, student_id FROM internship_guidance_record ORDER BY id");
        List<Map<String, Object>> currentApplies = jdbcTemplate.queryForList("SELECT id, task_id, student_id, apply_status FROM internship_apply ORDER BY id");
        List<Map<String, Object>> currentSigns = jdbcTemplate.queryForList("SELECT id, task_id, student_id, is_signed FROM safety_commitment_sign ORDER BY id");

        assertEquals(baselineTasks, currentTasks, "任务表主键与关键字段测试前后必须完全一致");
        assertEquals(baselineUsers, currentUsers, "用户表主键与关键字段测试前后必须完全一致");
        assertEquals(baselineTaskStudents, currentTaskStudents, "任务学生关联主键与关联测试前后必须完全一致");
        assertEquals(baselineArchives, currentArchives, "归档卷宗表主键与关键字段测试前后必须完全一致");
        assertEquals(baselineScores, currentScores, "成绩表主键与关键字段测试前后必须完全一致");
        assertEquals(baselineWarns, currentWarns, "预警工单表主键与关键字段测试前后必须完全一致");
        assertEquals(baselineMaterials, currentMaterials, "阶段材料表主键与关键字段测试前后必须完全一致");
        assertEquals(baselineInspections, currentInspections, "中期检查表主键与关键字段测试前后必须完全一致");
        assertEquals(baselinePlans, currentPlans, "中期检查方案表主键与关键字段测试前后必须完全一致");
        assertEquals(baselineRectifications, currentRectifications, "整改单表主键与关键字段测试前后必须完全一致");
        assertEquals(baselineReports, currentReports, "周报表主键与关键字段测试前后必须完全一致");
        assertEquals(baselineGuidances, currentGuidances, "指导台账表主键与关键字段测试前后必须完全一致");
        assertEquals(baselineApplies, currentApplies, "实习申请表主键与关键字段测试前后必须完全一致");
        assertEquals(baselineSigns, currentSigns, "安全承诺书签署表主键与关键字段测试前后必须完全一致");
    }
}
