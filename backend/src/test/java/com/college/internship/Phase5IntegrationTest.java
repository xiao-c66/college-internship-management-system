package com.college.internship;

import com.college.internship.dto.ApplyDTO;
import com.college.internship.dto.AuditDTO;
import com.college.internship.dto.CommitmentSignDTO;
import com.college.internship.dto.ExamSubmitDTO;
import com.college.internship.dto.LoginDTO;
import com.college.internship.dto.SafetyMaterialDTO;
import com.college.internship.dto.SafetyQuestionDTO;
import com.college.internship.dto.TaskCreateDTO;
import com.college.internship.dto.TeacherAssignDTO;
import com.college.internship.service.IAuthService;
import com.college.internship.vo.CaptchaVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段5核心业务集成测试：
 * 覆盖实习任务管理、100%权重校验、安全资料与准入考试、实习申报、双级审核流转、
 * 退回不少于5字校验、以及 APPLY-009 终审生效后一票否决普通修改校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase5IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IAuthService authService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String deptAdminToken;
    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUpTokens() throws Exception {
        adminToken = obtainToken("admin");
        deptAdminToken = obtainToken("deptadmin");
        teacherToken = obtainToken("teacher");
        studentToken = obtainToken("student");

        // 物理清理学生4在测试库中的作答、签署与申请临时记录，确保多轮测试完全幂等独立
        jdbcTemplate.execute("DELETE FROM safety_exam_answer_detail");
        jdbcTemplate.execute("DELETE FROM safety_exam_attempt WHERE student_id = 4");
        jdbcTemplate.execute("DELETE FROM safety_commitment_sign WHERE student_id = 4");
        jdbcTemplate.execute("DELETE FROM apply_audit_history");
        jdbcTemplate.execute("DELETE FROM internship_apply WHERE student_id = 4");
        jdbcTemplate.execute("UPDATE internship_task_student SET teacher_id = 3, read_material_ids = '[]', read_material_count = 0, study_start_time = NULL, study_complete_time = NULL, safety_status = 'NOT_STARTED' WHERE student_id = 4");
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

    @Test
    @DisplayName("1. 任务创建成功与成绩权重不等于100%失败校验及发布防呆核验 (TASK-004 & TASK-008 & TASK-009)")
    void testTaskCreateAndWeightValidation() throws Exception {
        // 权重合计 90% -> 必须抛出 400
        TaskCreateDTO invalidTask = TaskCreateDTO.builder()
                .taskCode("TASK_FAIL_90_" + System.currentTimeMillis())
                .taskName("测试权重不符任务")
                .deptId(1L)
                .academicYear("2025-2026")
                .semester(2)
                .internshipMode("DISTRIBUTED")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .weightEnterprise(new BigDecimal("20.00"))
                .weightTeacherProcess(new BigDecimal("20.00"))
                .weightWeeklyReport(new BigDecimal("20.00"))
                .weightStageMaterial(new BigDecimal("20.00"))
                .weightSummary(new BigDecimal("10.00")) // 合计90%
                .build();

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidTask)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("五项评价成绩权重之和必须严格等于100.00%，当前合计为: 90.00% (TASK-009)"));

        // 管理员未指定所属院系创建任务 -> 400 拦截禁止默认院系 (TASK-001)
        TaskCreateDTO noDeptTask = TaskCreateDTO.builder()
                .taskCode("TASK_NO_DEPT_" + System.currentTimeMillis())
                .taskName("未指定院系任务")
                .deptId(null)
                .academicYear("2025-2026")
                .semester(2)
                .internshipMode("DISTRIBUTED")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .weightEnterprise(new BigDecimal("20.00"))
                .weightTeacherProcess(new BigDecimal("20.00"))
                .weightWeeklyReport(new BigDecimal("20.00"))
                .weightStageMaterial(new BigDecimal("20.00"))
                .weightSummary(new BigDecimal("20.00"))
                .build();

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noDeptTask)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("必须明确指定所属二级院系，禁止使用默认院系 (TASK-001)"));

        // 权重合计 100% -> 创建成功 (但关键发布参数未配置)
        String validTaskCode = "TASK_OK_" + System.currentTimeMillis();
        TaskCreateDTO validTask = TaskCreateDTO.builder()
                .taskCode(validTaskCode)
                .taskName("2026届计算机毕业实习批次测试")
                .deptId(1L)
                .academicYear("2025-2026")
                .semester(2)
                .internshipMode("DISTRIBUTED")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .weightEnterprise(new BigDecimal("20.00"))
                .weightTeacherProcess(new BigDecimal("20.00"))
                .weightWeeklyReport(new BigDecimal("20.00"))
                .weightStageMaterial(new BigDecimal("20.00"))
                .weightSummary(new BigDecimal("20.00")) // 合计100%
                .majorIds(List.of(1L))
                .classIds(List.of(1L))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTask)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskCode").value(validTaskCode))
                .andReturn();

        JsonNode root = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long taskId = root.path("data").path("id").asLong();

        // 未配置周报频次与及格分等核心参数，发布必须被 400 阻断 (TASK-004)
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/publish")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("实习任务尚未配置周报提交频次，未完整配置时禁止发布任务 (TASK-004)"));

        // 完整配置周报频次、截止日、安全及格分与最大尝试次数
        com.college.internship.dto.TaskUpdateDTO updateDTO = com.college.internship.dto.TaskUpdateDTO.builder()
                .taskName("2026届计算机毕业实习批次测试 (已完整配置)")
                .academicYear("2025-2026")
                .semester(2)
                .internshipMode("DISTRIBUTED")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .weightEnterprise(new BigDecimal("20.00"))
                .weightTeacherProcess(new BigDecimal("20.00"))
                .weightWeeklyReport(new BigDecimal("20.00"))
                .weightStageMaterial(new BigDecimal("20.00"))
                .weightSummary(new BigDecimal("20.00"))
                .weeklyFrequency("WEEKLY")
                .weeklyDeadlineDay(7)
                .safetyPassingScore(80)
                .safetyMaxAttempts(3)
                .majorIds(List.of(1L))
                .classIds(List.of(1L))
                .build();

        mockMvc.perform(put("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 再次正式发布任务 -> 成功
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/publish")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("2. 安全资料阅读驱动五阶段流转、考试交卷、教师监控与一键催办全闭环 (SAFE-001 ~ SAFE-008)")
    void testSafetyEducationAndExamLifecycle() throws Exception {
        // 学生角色越权配置全校通用资料 -> 403 拒绝
        SafetyMaterialDTO matDTO = SafetyMaterialDTO.builder()
                .title("学生越权配置通用资料")
                .contentType("TEXT")
                .contentBody("越权正文")
                .build();

        mockMvc.perform(post("/api/v1/safety/materials")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(matDTO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 初始状态查验：NOT_STARTED, 已读0篇
        mockMvc.perform(get("/api/v1/safety/status?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.statusCode").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.materialsRead").value(0));

        // 学生阅读第1篇安全资料 -> 状态流转为 STUDYING
        mockMvc.perform(post("/api/v1/safety/materials/1/read?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/safety/status?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCode").value("STUDYING"))
                .andExpect(jsonPath("$.data.materialsRead").value(1));

        // 学生阅读第2篇安全资料 (全部必读资料读完) -> 状态流转为 PENDING_TEST
        mockMvc.perform(post("/api/v1/safety/materials/2/read?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/safety/status?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCode").value("PENDING_TEST"))
                .andExpect(jsonPath("$.data.materialsRead").value(2));

        // 学生获取考试试卷 (正确答案必须被脱敏屏蔽)
        MvcResult paperResult = mockMvc.perform(get("/api/v1/safety/exam/paper?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String paperStr = paperResult.getResponse().getContentAsString();
        assertTrue(!paperStr.contains("correctAnswer"), "试卷返回中严禁暴露 correctAnswer");

        // 学生交卷答题 (答题满分100分通过及格线80分) -> 状态流转为 PASSED
        ExamSubmitDTO submitDTO = new ExamSubmitDTO();
        submitDTO.setTaskId(1L);
        List<ExamSubmitDTO.AnswerItem> answers = new ArrayList<>();
        answers.add(new ExamSubmitDTO.AnswerItem(1L, "B")); // 30分
        answers.add(new ExamSubmitDTO.AnswerItem(2L, "FALSE")); // 35分
        answers.add(new ExamSubmitDTO.AnswerItem(3L, "B")); // 35分
        submitDTO.setAnswers(answers);

        mockMvc.perform(post("/api/v1/safety/exam/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isPassed").value(1))
                .andExpect(jsonPath("$.data.totalScore").value(100.0));

        mockMvc.perform(get("/api/v1/safety/status?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCode").value("PASSED"));

        // 学生签署安全承诺书 -> 状态最终流转为 COMPLETED
        CommitmentSignDTO signDTO = CommitmentSignDTO.builder()
                .taskId(1L)
                .insuranceFileUrl("/uploads/insurance/demo_policy.pdf")
                .build();

        mockMvc.perform(post("/api/v1/safety/commitment/sign")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证学生五阶段安全教育状态已达标 (COMPLETED)
        mockMvc.perform(get("/api/v1/safety/status?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.statusCode").value("COMPLETED"))
                .andExpect(jsonPath("$.data.isPassed").value(1))
                .andExpect(jsonPath("$.data.isCommitmentSigned").value(1));

        // 验证指导教师查询学生安全教育监控名单 (SAFE-008 & API-125)
        mockMvc.perform(get("/api/v1/safety/students?taskId=1&status=ALL")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].studentNumber").value("2021003011"))
                .andExpect(jsonPath("$.data[0].statusCode").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].isPassed").value(1));

        // 验证指导教师发起一键催办提醒 (API-123 & SAFE-008)
        mockMvc.perform(post("/api/v1/safety/remind?taskId=1")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value(1));

        // 验证学生越权查询监控名单或越权催办 -> 403 拒绝
        mockMvc.perform(get("/api/v1/safety/students?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/api/v1/safety/remind?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("3. 实习申报、退回原因少于5字拦截、双级审核与APPLY-009生效后禁止直接修改全流程测试")
    void testApplyAuditAndApply009LockRule() throws Exception {
        // 1. 学生提交实习申报
        ApplyDTO applyDTO = ApplyDTO.builder()
                .taskId(1L)
                .companyName("浙江智能科技有限公司")
                .jobPosition("Java后端开发实习生")
                .jobAddress("杭州市西湖区文三路科技大厦808室")
                .companyContactPerson("王经理")
                .companyContactPhone("13800000000")
                .companyContactEmail("hr@zhineng.com")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .internshipMode("DISTRIBUTED")
                .jobDuties("负责微服务接口编码与单元测试编写")
                .agreementFileUrl("/uploads/agreements/2021003011_agreement.pdf")
                .build();

        MvcResult submitResult = mockMvc.perform(post("/api/v1/applies/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.applyStatus").value("SUBMITTED"))
                .andReturn();

        JsonNode root = objectMapper.readTree(submitResult.getResponse().getContentAsString());
        long applyId = root.path("data").path("id").asLong();

        // 2. 教师初审：退回原因少于5个字符 -> 必须 400 拦截报错！
        AuditDTO shortRejectDTO = AuditDTO.builder()
                .action("REJECTED")
                .opinion("重写") // 仅2个字符，短于5个字符
                .build();

        mockMvc.perform(post("/api/v1/applies/" + applyId + "/audit")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortRejectDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("审核退回原因必须填写且不得少于5个字符！"));

        // 3. 教师初审：退回原因合规 (不少于5个字符) -> 成功退回为 TEACHER_REJECTED
        AuditDTO validRejectDTO = AuditDTO.builder()
                .action("REJECTED")
                .opinion("请补充企业指导教师职务及具体工作内容职责 (不少于50字)")
                .build();

        mockMvc.perform(post("/api/v1/applies/" + applyId + "/audit")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRejectDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 查验状态已变更为 TEACHER_REJECTED
        mockMvc.perform(get("/api/v1/applies/" + applyId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applyStatus").value("TEACHER_REJECTED"));

        // 4. 学生修改并重新提交审核 -> 回到 SUBMITTED
        applyDTO.setId(applyId);
        applyDTO.setJobDuties("负责高校实习全过程管理系统后端开发、API接口设计与测试用例验证");

        mockMvc.perform(post("/api/v1/applies/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applyStatus").value("SUBMITTED"));

        // 5. 教师初审通过 -> TEACHER_APPROVED
        AuditDTO teacherPassDTO = AuditDTO.builder()
                .action("APPROVED")
                .opinion("申报单位资质核验通过，同意进入实习")
                .build();

        mockMvc.perform(post("/api/v1/applies/" + applyId + "/audit")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teacherPassDTO)))
                .andExpect(status().isOk());

        // 6. 院系终审通过 -> APPROVED 且 is_locked = 1
        AuditDTO deptPassDTO = AuditDTO.builder()
                .action("APPROVED")
                .opinion("学院实习工作领导小组复审同意")
                .build();

        mockMvc.perform(post("/api/v1/applies/" + applyId + "/audit")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptPassDTO)))
                .andExpect(status().isOk());

        // 验证当前状态已为 APPROVED 且 isLocked = 1
        mockMvc.perform(get("/api/v1/applies/" + applyId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applyStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.isLocked").value(1));

        // 7. 【APPLY-009核心测试】一旦处于 APPROVED / isLocked=1 状态，直接 PUT 普通修改必须被 Service 层一票否决！
        applyDTO.setCompanyName("试图擅自篡改的未经审核新企业名称");

        mockMvc.perform(put("/api/v1/applies/" + applyId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("实习信息已经审核生效，禁止直接修改。单位、岗位、地址、联系人和起止时间变动必须走实习变更审批流程！(APPLY-009)"));

        // 再次尝试直接 post /applies/submit 重新提交也必须被拒绝！
        mockMvc.perform(post("/api/v1/applies/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("4. 越权保护拦截验证 (学生不能审核、跨院系越权)")
    void testUnauthorizedAuditProtection() throws Exception {
        AuditDTO auditDTO = AuditDTO.builder()
                .action("APPROVED")
                .opinion("学生试图自行审核")
                .build();

        mockMvc.perform(post("/api/v1/applies/1/audit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(auditDTO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("当前角色无权执行实习申报审核"));
    }

    @Test
    @DisplayName("5. 指导教师仅限查询与催办本人分配学生越权拦截测试 (SAFE-008)")
    void testTeacherScopeAndUnauthorizedRemind() throws Exception {
        // 创建属于其他教师分配的学生记录 (学生ID 9999, 属于任务1, 指导教师ID 8888)
        jdbcTemplate.execute("DELETE FROM internship_task_student WHERE student_id = 9999");
        jdbcTemplate.execute("INSERT INTO internship_task_student (task_id, student_id, student_number, student_name, teacher_id, safety_status, is_deleted) VALUES (1, 9999, '20219999', '未分配给当前教师学生', 8888, 'STUDYING', 0)");

        try {
            // 当前教师(ID=3)越权查询非本人负责学生状态 -> 403 拦截
            mockMvc.perform(get("/api/v1/safety/status?taskId=1&studentId=9999")
                            .header("Authorization", "Bearer " + teacherToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("指导教师仅能查看本人负责分配学生的安全教育状态 (SAFE-008)"));

            // 当前教师(ID=3)越权单催非本人负责学生 -> 403 拦截
            mockMvc.perform(post("/api/v1/safety/remind?taskId=1&studentId=9999")
                            .header("Authorization", "Bearer " + teacherToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("指导教师仅能催办本人实际负责分配的学生 (SAFE-008)"));
        } finally {
            jdbcTemplate.execute("DELETE FROM internship_task_student WHERE student_id = 9999");
        }
    }

    @Test
    @DisplayName("6. 未圈定学生调用安全教育各操作接口拦截测试 (TASK-003)")
    void testUnenrolledStudentAccessDenied() throws Exception {
        // 学生4未圈定在任务9999中，调用各项安全接口均应被 403 拦截
        // 1. 标记阅读拦截
        mockMvc.perform(post("/api/v1/safety/materials/1/read?taskId=9999")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("您未圈定在当前实习任务学生名单中，无权操作 (TASK-003)"));

        // 2. 获取试卷拦截
        mockMvc.perform(get("/api/v1/safety/exam/paper?taskId=9999")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("您未圈定在当前实习任务学生名单中，无权获取试卷 (TASK-003)"));

        // 3. 提交答卷拦截
        ExamSubmitDTO submitDTO = new ExamSubmitDTO();
        submitDTO.setTaskId(9999L);
        mockMvc.perform(post("/api/v1/safety/exam/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDTO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("您未圈定在当前实习任务学生名单中，无权交卷 (TASK-003)"));

        // 4. 签署承诺书拦截
        CommitmentSignDTO signDTO = CommitmentSignDTO.builder().taskId(9999L).build();
        mockMvc.perform(post("/api/v1/safety/commitment/sign")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signDTO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("您未圈定在当前实习任务学生名单中，无权签署安全责任承诺书 (TASK-003)"));

        // 5. 状态查询拦截
        mockMvc.perform(get("/api/v1/safety/status?taskId=9999")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("您未圈定在当前实习任务学生名单中 (TASK-003)"));
    }

    @Test
    @DisplayName("7. 学生越权查询他人安全准入状态拦截测试 (SAFE-008)")
    void testStudentUnauthorizedQueryOthersStatus() throws Exception {
        mockMvc.perform(get("/api/v1/safety/status?taskId=1&studentId=999")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("学生无权查询他人安全教育准入状态 (SAFE-008)"));
    }

    @Test
    @DisplayName("8. 实习任务未配置安全及格分或最大重测次数时开考阻断 (TASK-004)")
    void testTaskUnconfiguredSafetyParamsBlockExam() throws Exception {
        // 创建未配置安全参数的临时任务并圈定学生4
        jdbcTemplate.execute("DELETE FROM internship_task WHERE task_code = 'TASK_NO_SAFETY_CFG'");
        jdbcTemplate.execute("INSERT INTO internship_task (task_code, task_name, dept_id, academic_year, semester, internship_mode, start_date, end_date, weight_enterprise, weight_teacher_process, weight_weekly_report, weight_stage_material, weight_summary, weekly_frequency, weekly_deadline_day, safety_passing_score, safety_max_attempts, status, is_deleted) VALUES ('TASK_NO_SAFETY_CFG', '未配置安全参数任务', 1, '2025-2026', 2, 'DISTRIBUTED', '2026-03-01', '2026-06-30', 20.00, 20.00, 20.00, 20.00, 20.00, 'WEEKLY', 7, NULL, NULL, 'DRAFT', 0)");
        Long taskId = jdbcTemplate.queryForObject("SELECT id FROM internship_task WHERE task_code = 'TASK_NO_SAFETY_CFG'", Long.class);

        jdbcTemplate.execute("DELETE FROM internship_task_student WHERE task_id = " + taskId + " AND student_id = 4");
        jdbcTemplate.execute("INSERT INTO internship_task_student (task_id, student_id, student_number, student_name, teacher_id, safety_status, is_deleted) VALUES (" + taskId + ", 4, '2021003011', '测试学生', 3, 'STUDYING', 0)");

        try {
            ExamSubmitDTO submitDTO = new ExamSubmitDTO();
            submitDTO.setTaskId(taskId);
            mockMvc.perform(post("/api/v1/safety/exam/submit")
                            .header("Authorization", "Bearer " + studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(submitDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("实习任务尚未配置安全考试及格分或最大尝试次数，无法开考"));
        } finally {
            jdbcTemplate.execute("DELETE FROM internship_task_student WHERE task_id = " + taskId);
            jdbcTemplate.execute("DELETE FROM internship_task WHERE id = " + taskId);
        }
    }

    @Test
    @DisplayName("9. SAFE-009 院系统计按任务及院系范围过滤与跨院系越权拦截")
    void testDeptSafetyStatisticsFilteringAndPermissions() throws Exception {
        // 学生访问统计接口 -> 403 拦截
        mockMvc.perform(get("/api/v1/safety/statistics?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("学生无权查看安全教育统计数据 (SAFE-009)"));

        // 院系负责人 (deptId=1) 越权查询院系 2 -> 403 拦截
        mockMvc.perform(get("/api/v1/safety/statistics?taskId=1&deptId=2")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("院系负责人仅能查看本院系安全教育统计数据 (SAFE-009)"));

        // 院系负责人查询本院系统计 -> 200 成功，校验 taskId 和 deptId
        mockMvc.perform(get("/api/v1/safety/statistics?taskId=1&deptId=1")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value(1))
                .andExpect(jsonPath("$.data.deptId").value(1))
                .andExpect(jsonPath("$.data.totalStudents").isNumber())
                .andExpect(jsonPath("$.data.passedStudents").isNumber())
                .andExpect(jsonPath("$.data.completedRate").isString());
    }

    @Test
    @DisplayName("10. 一键催办系统审计日志真实写入查验 (SAFE-008 & API-123)")
    void testRemindOperationLogPersistence() throws Exception {
        int logCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE oper_url = '/api/v1/safety/remind'", Integer.class);

        MvcResult result = mockMvc.perform(post("/api/v1/safety/remind?taskId=1")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode resJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String noticeMessage = resJson.path("data").path("noticeMessage").asText();
        assertTrue(noticeMessage.contains("记录催办指令至系统安全审计日志") || noticeMessage.contains("无需催办"),
                "催办响应信息必须清晰说明记录于系统安全审计日志");

        int logCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE oper_url = '/api/v1/safety/remind'", Integer.class);
        assertTrue(logCountAfter > logCountBefore, "催办指令必须真实持久化到 sys_operation_log");
    }

    @Test
    @DisplayName("11. 阻断未完成安全资料学习的学生获取试卷或交卷 (SAFE-004)")
    void testStudentBlockedFromExamBeforeCompletingMaterials() throws Exception {
        // 确保学生未读完规程资料 (已读 0 篇，共 2 篇)
        jdbcTemplate.execute("UPDATE internship_task_student SET read_material_ids = '[]', read_material_count = 0, safety_status = 'NOT_STARTED' WHERE student_id = 4 AND task_id = 1");

        // 未读完资料时直接获取试卷 -> 400 阻断
        mockMvc.perform(get("/api/v1/safety/exam/paper?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("尚未完成全部必学安全教育规程资料")));

        // 未读完资料时直接提交考试 -> 400 阻断
        ExamSubmitDTO submitDTO = new ExamSubmitDTO();
        submitDTO.setTaskId(1L);
        submitDTO.setAnswers(List.of(new ExamSubmitDTO.AnswerItem(1L, "B")));

        mockMvc.perform(post("/api/v1/safety/exam/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("尚未完成全部必学安全教育规程资料")));
    }

    @Test
    @DisplayName("12. 任务可用导师查询、圈定学生教师分配与跨院系指派拦截 (ASSIGN-001 ~ ASSIGN-003)")
    void testTeacherAssignmentAndCrossDeptRestriction() throws Exception {
        // 1. 查询任务可用指导教师列表 (GET /api/v1/tasks/1/teachers)
        mockMvc.perform(get("/api/v1/tasks/1/teachers")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(3))
                .andExpect(jsonPath("$.data[0].realName").value(containsString("李教授")));

        // 2. 查询任务圈定学生列表 (GET /api/v1/tasks/1/students)
        mockMvc.perform(get("/api/v1/tasks/1/students")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].studentId").value(4))
                .andExpect(jsonPath("$.data[0].studentNumber").value("2021003011"));

        // 3. 指派教师3给学生4 (POST /api/v1/tasks/1/assign-teacher)
        TeacherAssignDTO assignDTO = TeacherAssignDTO.builder()
                .teacherId(3L)
                .studentIds(List.of(4L))
                .build();

        mockMvc.perform(post("/api/v1/tasks/1/assign-teacher")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证数据库与查询接口返回已指派教师
        mockMvc.perform(get("/api/v1/tasks/1/students")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].teacherId").value(3))
                .andExpect(jsonPath("$.data[0].teacherName").value(containsString("李教授")));

        // 4. 越权测试：学生尝试分配导师 -> 403 拒绝
        mockMvc.perform(post("/api/v1/tasks/1/assign-teacher")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignDTO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 5. 教师角色非合规测试：指定的教师ID为非教师角色(如管理员1) -> 400 拒绝
        TeacherAssignDTO invalidRoleDTO = TeacherAssignDTO.builder()
                .teacherId(1L)
                .studentIds(List.of(4L))
                .build();

        mockMvc.perform(post("/api/v1/tasks/1/assign-teacher")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRoleDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("指定的指导教师不存在或非教师角色"));

        // 6. 圈定学生名单校验：指派包含非当前任务名单学生ID (如 99999L) -> 400 拒绝，禁止静默跳过 (ASSIGN-001)
        TeacherAssignDTO notInTaskDTO = TeacherAssignDTO.builder()
                .teacherId(3L)
                .studentIds(List.of(99999L))
                .build();

        mockMvc.perform(post("/api/v1/tasks/1/assign-teacher")
                        .header("Authorization", "Bearer " + deptAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notInTaskDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("不在当前实习任务圈定名单中，禁止分配")));
    }

    @Test
    @DisplayName("13. 任务圈定学生与教师列表角色访问控制及跨院系数据范围隔离 (TASK-003, ASSIGN-001)")
    void testTaskStudentAndTeacherListAccessControl() throws Exception {
        // 1. 学生尝试查看任务圈定学生列表 -> 403 拒绝
        mockMvc.perform(get("/api/v1/tasks/1/students")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("仅允许院系教学负责人或学校管理员查看")));

        // 2. 指导教师尝试查看任务圈定学生列表 -> 403 拒绝 (教师应通过专属的本人学生接口查询)
        mockMvc.perform(get("/api/v1/tasks/1/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("仅允许院系教学负责人或学校管理员查看")));

        // 3. 学生尝试查看任务指导教师列表 -> 403 拒绝
        mockMvc.perform(get("/api/v1/tasks/1/teachers")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("仅允许院系教学负责人或学校管理员查看")));

        // 4. 指导教师尝试查看任务指导教师列表 -> 403 拒绝
        mockMvc.perform(get("/api/v1/tasks/1/teachers")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("仅允许院系教学负责人或学校管理员查看")));

        // 5. 跨院系数据隔离：创建属于院系2的独立任务(id=999)，院系1管理员尝试查询 -> 403 拦截
        try {
            jdbcTemplate.execute("INSERT INTO internship_task (id, task_code, task_name, dept_id, academic_year, semester, start_date, end_date, status, is_deleted) " +
                    "VALUES (999, 'TASK-2026-MECH', '机械学院测试任务', 2, '2025-2026', 2, '2026-03-01', '2026-06-30', 'PUBLISHED', 0) " +
                    "ON DUPLICATE KEY UPDATE dept_id = 2, is_deleted = 0");

            mockMvc.perform(get("/api/v1/tasks/999/students")
                            .header("Authorization", "Bearer " + deptAdminToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value(containsString("无权查看其他二级院系")));

            mockMvc.perform(get("/api/v1/tasks/999/teachers")
                            .header("Authorization", "Bearer " + deptAdminToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value(containsString("无权查看其他二级院系")));
        } finally {
            jdbcTemplate.execute("DELETE FROM internship_task WHERE id = 999");
        }
    }

    @Test
    @DisplayName("14. 安全考试交卷试题合法性与跨任务作答专项校验 (SAFE-004, SAFE-005)")
    void testExamSubmissionQuestionValidation() throws Exception {
        // 先确保学生4已完成规程学习进入 PENDING_TEST 状态
        jdbcTemplate.execute("UPDATE internship_task_student SET read_material_ids = '[1,2]', read_material_count = 2, safety_status = 'PENDING_TEST', study_start_time = NOW(), study_complete_time = NOW() WHERE student_id = 4 AND task_id = 1");

        // 场景 A: 试卷答案为空 -> 400 阻断
        ExamSubmitDTO emptyDTO = new ExamSubmitDTO();
        emptyDTO.setTaskId(1L);
        emptyDTO.setAnswers(new ArrayList<>());

        mockMvc.perform(post("/api/v1/safety/exam/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("交卷失败：试卷未包含任何作答试题")));

        // 场景 B: 包含不存在或已停用的试题ID -> 400 阻断
        ExamSubmitDTO invalidQuestionDTO = new ExamSubmitDTO();
        invalidQuestionDTO.setTaskId(1L);
        invalidQuestionDTO.setAnswers(List.of(new ExamSubmitDTO.AnswerItem(999999L, "A")));

        mockMvc.perform(post("/api/v1/safety/exam/submit")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidQuestionDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("不存在或已被停用")));

        // 场景 C: 包含属于其他任务的专有题目 -> 400 阻断禁止跨任务作答
        try {
            jdbcTemplate.execute("INSERT INTO safety_test_question (id, task_id, question_type, stem, options, correct_answer, score, sort_order, status, is_deleted) " +
                    "VALUES (998, 2, 'SINGLE_CHOICE', '其他任务专属试题', '[{\"label\":\"A\",\"text\":\"是\"},{\"label\":\"B\",\"text\":\"否\"}]', 'A', 10, 1, 1, 0) " +
                    "ON DUPLICATE KEY UPDATE task_id = 2, status = 1, is_deleted = 0");

            ExamSubmitDTO crossTaskDTO = new ExamSubmitDTO();
            crossTaskDTO.setTaskId(1L);
            crossTaskDTO.setAnswers(List.of(new ExamSubmitDTO.AnswerItem(998L, "A")));

            mockMvc.perform(post("/api/v1/safety/exam/submit")
                            .header("Authorization", "Bearer " + studentToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(crossTaskDTO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("不属于当前实习任务题库，禁止跨任务作答")));
        } finally {
            jdbcTemplate.execute("DELETE FROM safety_test_question WHERE id = 998");
        }
    }
}
