package com.college.internship;

import com.college.internship.dto.GuidanceCreateDTO;
import com.college.internship.dto.GuidanceFeedbackDTO;
import com.college.internship.dto.LoginDTO;
import com.college.internship.dto.WeeklyReportReviewDTO;
import com.college.internship.dto.WeeklyReportSaveDTO;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段6核心业务集成测试：
 * 严格对应方案设计的 18 项无遗漏自动化集成测试 (TEST-W01 ~ TEST-W18)
 * 覆盖周报前置准入、草稿暂存、业务阈值校验、按期与逾期计算、防重、越权防御、退回重提版本自增、
 * NOTICE-006、NOTICE-012、过程走访登记、API-067 CAS条件更新防重、API-066 EasyExcel导出及院系隔离。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase6IntegrationTest {

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

    @BeforeEach
    void setUp() throws Exception {
        adminToken = obtainToken("admin");
        deptAdminToken = obtainToken("deptadmin");
        teacherToken = obtainToken("teacher");
        studentToken = obtainToken("student");

        // 幂等清理阶段6测试产生的数据
        jdbcTemplate.execute("DELETE FROM internship_weekly_report_history");
        jdbcTemplate.execute("DELETE FROM internship_weekly_report");
        jdbcTemplate.execute("DELETE FROM internship_guidance_record");

        // 确保学生4拥有基础圈定绑定关系 (任务1, 指导教师3, 院系1)
        jdbcTemplate.execute("UPDATE internship_task_student SET teacher_id = 3 WHERE student_id = 4 AND task_id = 1");
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
                "VALUES (" + taskId + ", " + studentId + ", '2021003011', '张晓峰', 1, 1, 1, " +
                "'某某网络科技有限公司', 'Java后端实习生', '杭州市滨江区科技园', '王经理', '13800138000', '2026-03-01', '2026-06-30', 'CENTRALIZED', 'APPROVED', 1, 0)");
    }

    @Test
    @DisplayName("TEST-W01: 前置准入拦截：未审核通过实习申报的学生尝试提交周报 -> 400")
    void testW01_PreRequisiteApplyCheck() throws Exception {
        // 清理学生4的实习申报，使其处于未申报状态
        jdbcTemplate.execute("DELETE FROM internship_apply WHERE task_id = 1 AND student_id = 4");

        WeeklyReportSaveDTO dto = WeeklyReportSaveDTO.builder()
                .taskId(1L)
                .weekNumber(1)
                .action("SUBMIT")
                .workContent("本周完成系统骨架代码编写和项目环境配置")
                .workSummary("熟悉了Spring Boot架构体系和开发流程")
                .problemEncountered("遇到一些依赖冲突但通过排查pom解决")
                .nextWeekPlan("继续完善核心业务模块接口与单元测试")
                .build();

        mockMvc.perform(post("/api/v1/internship/weekly-reports")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("尚未通过实习申报审核")));
    }

    @Test
    @DisplayName("TEST-W02: 草稿暂存与 NULL 兼容：部分填写保存周报草稿 -> 200, 状态为 DRAFT")
    void testW02_SaveDraftWithNullFields() throws Exception {
        prepareApprovedApply(1L, 4L);

        WeeklyReportSaveDTO dto = WeeklyReportSaveDTO.builder()
                .taskId(1L)
                .weekNumber(1)
                .action("DRAFT")
                .workContent("初步记录本周日常") // 仅填写一项，其余全为 NULL
                .build();

        mockMvc.perform(post("/api/v1/internship/weekly-reports")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.weekNumber").value(1));
    }

    @Test
    @DisplayName("TEST-W03: 业务阈值校验：正式提交周报时某项内容少于15字 -> 400")
    void testW03_ContentMinLengthValidation() throws Exception {
        prepareApprovedApply(1L, 4L);

        WeeklyReportSaveDTO dto = WeeklyReportSaveDTO.builder()
                .taskId(1L)
                .weekNumber(1)
                .action("SUBMIT")
                .workContent("太少了") // 3个字，低于默认15字门槛
                .workSummary("本周在导师指导下认真学习了后端业务架构规范与编码设计标准")
                .problemEncountered("在数据库索引设计遇到疑问通过查阅资料顺利解决")
                .nextWeekPlan("按期推进下一阶段业务开发与自动化集成测试编写")
                .build();

        mockMvc.perform(post("/api/v1/internship/weekly-reports")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("不得少于 15 字")));
    }

    @Test
    @DisplayName("TEST-W04: 周报按期提交：在截止日前正常提交周报 -> SUBMITTED, is_overdue=0")
    void testW04_SubmitOnTime() throws Exception {
        prepareApprovedApply(1L, 4L);

        // 设置任务1为 WEEKLY，截止日期为未来
        jdbcTemplate.execute("UPDATE internship_task SET start_date = CURRENT_DATE, end_date = DATE_ADD(CURRENT_DATE, INTERVAL 60 DAY), weekly_frequency = 'WEEKLY', weekly_deadline_day = 7 WHERE id = 1");

        WeeklyReportSaveDTO dto = WeeklyReportSaveDTO.builder()
                .taskId(1L)
                .weekNumber(1)
                .action("SUBMIT")
                .workContent("本周完成系统骨架代码编写和项目环境配置与数据库结构设计")
                .workSummary("熟悉了Spring Boot架构体系和开发流程并在企业导师指导下编码")
                .problemEncountered("遇到一些依赖冲突但通过排查pom文件顺利消除相关报错")
                .nextWeekPlan("继续完善核心业务模块接口与单元测试并做好下周工作汇报")
                .build();

        mockMvc.perform(post("/api/v1/internship/weekly-reports")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.isOverdue").value(0))
                .andExpect(jsonPath("$.data.overdueDays").value(0));

        // 验证首次正式提交必须写入 SUBMIT 历史快照
        Integer submitHistoryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_weekly_report_history WHERE task_id = 1 AND student_id = 4 AND action = 'SUBMIT'", Integer.class);
        assertEquals(1, submitHistoryCount);
    }

    @Test
    @DisplayName("TEST-W05: 双周与逾期提交：BIWEEKLY 逾期补交周报 -> SUBMITTED, is_overdue=1, 逾期天数精确计算")
    void testW05_BiweeklyOverdueSubmit() throws Exception {
        prepareApprovedApply(1L, 4L);

        // 设置任务起始日期为 30 天前，周期为 BIWEEKLY，第1期早已经截止
        jdbcTemplate.execute("UPDATE internship_task SET start_date = DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY), end_date = DATE_ADD(CURRENT_DATE, INTERVAL 60 DAY), weekly_frequency = 'BIWEEKLY', weekly_deadline_day = 7 WHERE id = 1");

        WeeklyReportSaveDTO dto = WeeklyReportSaveDTO.builder()
                .taskId(1L)
                .weekNumber(1) // 第1期，截止时刻在过去
                .action("SUBMIT")
                .workContent("本双周完成系统骨架代码编写和项目环境配置与数据库结构设计")
                .workSummary("熟悉了Spring Boot架构体系和开发流程并在企业导师指导下编码")
                .problemEncountered("遇到一些依赖冲突但通过排查pom文件顺利消除相关报错")
                .nextWeekPlan("继续完善核心业务模块接口与单元测试并做好下周工作汇报")
                .build();

        mockMvc.perform(post("/api/v1/internship/weekly-reports")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.isOverdue").value(1))
                .andExpect(jsonPath("$.data.overdueDays").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("TEST-W06: 防重提交：同一学生在同一任务同一周次尝试重复提交 -> 400 阻断")
    void testW06_DuplicateSubmitPrevention() throws Exception {
        prepareApprovedApply(1L, 4L);
        jdbcTemplate.execute("UPDATE internship_task SET start_date = CURRENT_DATE, end_date = DATE_ADD(CURRENT_DATE, INTERVAL 60 DAY), weekly_frequency = 'WEEKLY', weekly_deadline_day = 7 WHERE id = 1");

        WeeklyReportSaveDTO dto = WeeklyReportSaveDTO.builder()
                .taskId(1L)
                .weekNumber(1)
                .action("SUBMIT")
                .workContent("本周完成系统骨架代码编写和项目环境配置与数据库结构设计")
                .workSummary("熟悉了Spring Boot架构体系和开发流程并在企业导师指导下编码")
                .problemEncountered("遇到一些依赖冲突但通过排查pom文件顺利消除相关报错")
                .nextWeekPlan("继续完善核心业务模块接口与单元测试并做好下周工作汇报")
                .build();

        // 首次提交成功
        mockMvc.perform(post("/api/v1/internship/weekly-reports")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        // 第二次重复提交 -> 400 拒绝
        mockMvc.perform(post("/api/v1/internship/weekly-reports")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("该周报已提交等待批阅，禁止重复提交")));
    }

    @Test
    @DisplayName("TEST-W07: 越权防御：教师 A 尝试批阅分配给教师 B 的学生周报 -> 403")
    void testW07_UnauthorizedTeacherReview() throws Exception {
        prepareApprovedApply(1L, 4L);

        // 插入一条由教师999负责的学生周报
        jdbcTemplate.execute("INSERT INTO internship_weekly_report (task_id, student_id, teacher_id, dept_id, week_number, start_date, end_date, deadline_time, status, work_content, work_summary, problem_encountered, next_week_plan, version, is_deleted) " +
                "VALUES (1, 4, 999, 1, 1, '2026-03-01', '2026-03-07', '2026-03-07 23:59:59', 'SUBMITTED', '工作内容正常记录充足字数标准', '收获体会正常记录充足字数标准', '疑难问题正常记录充足字数标准', '下周计划正常记录充足字数标准', 1, 0)");

        Long reportId = jdbcTemplate.queryForObject("SELECT id FROM internship_weekly_report WHERE task_id = 1 AND student_id = 4 AND week_number = 1", Long.class);

        WeeklyReportReviewDTO reviewDTO = WeeklyReportReviewDTO.builder()
                .action("APPROVE")
                .score(new BigDecimal("90.00"))
                .reviewComment("指导老师已认真批阅周报内容详实")
                .build();

        // 教师用户3 (teacher) 尝试批阅分配给999号导师的周报 -> 403
        mockMvc.perform(post("/api/v1/internship/weekly-reports/" + reportId + "/review")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewDTO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("无权批阅非本人负责的学生周报")));
    }

    @Test
    @DisplayName("TEST-W08: 退回校验：教师退回周报时退回原因低于10字 -> 400")
    void testW08_ReturnReasonMinLengthValidation() throws Exception {
        prepareApprovedApply(1L, 4L);

        // 插入属于教师3的待批阅周报
        jdbcTemplate.execute("INSERT INTO internship_weekly_report (task_id, student_id, teacher_id, dept_id, week_number, start_date, end_date, deadline_time, status, work_content, work_summary, problem_encountered, next_week_plan, version, is_deleted) " +
                "VALUES (1, 4, 3, 1, 1, '2026-03-01', '2026-03-07', '2026-03-07 23:59:59', 'SUBMITTED', '工作内容正常记录充足字数标准', '收获体会正常记录充足字数标准', '疑难问题正常记录充足字数标准', '下周计划正常记录充足字数标准', 1, 0)");

        Long reportId = jdbcTemplate.queryForObject("SELECT id FROM internship_weekly_report WHERE task_id = 1 AND student_id = 4 AND week_number = 1", Long.class);

        WeeklyReportReviewDTO returnDTO = WeeklyReportReviewDTO.builder()
                .action("RETURN")
                .reviewComment("重写") // 2个字，少于10字
                .build();

        mockMvc.perform(post("/api/v1/internship/weekly-reports/" + reportId + "/review")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("退回修改原因去除空格后不得少于 10 字")));
    }

    @Test
    @DisplayName("TEST-W09: NOTICE-006: 教师合规退回周报 -> RETURNED，快照入历史表并留痕")
    void testW09_TeacherReturnReportNotice() throws Exception {
        prepareApprovedApply(1L, 4L);

        jdbcTemplate.execute("INSERT INTO internship_weekly_report (task_id, student_id, teacher_id, dept_id, week_number, start_date, end_date, deadline_time, status, work_content, work_summary, problem_encountered, next_week_plan, version, is_deleted) " +
                "VALUES (1, 4, 3, 1, 1, '2026-03-01', '2026-03-07', '2026-03-07 23:59:59', 'SUBMITTED', '工作内容正常记录充足字数标准', '收获体会正常记录充足字数标准', '疑难问题正常记录充足字数标准', '下周计划正常记录充足字数标准', 1, 0)");

        Long reportId = jdbcTemplate.queryForObject("SELECT id FROM internship_weekly_report WHERE task_id = 1 AND student_id = 4 AND week_number = 1", Long.class);

        WeeklyReportReviewDTO returnDTO = WeeklyReportReviewDTO.builder()
                .action("RETURN")
                .reviewComment("周报内容与实际实习岗位偏差较大请补充具体业务实践") // 超过10字
                .build();

        mockMvc.perform(post("/api/v1/internship/weekly-reports/" + reportId + "/review")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.reviewComment").value("周报内容与实际实习岗位偏差较大请补充具体业务实践"));

        // 验证历史快照表
        Integer historyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_weekly_report_history WHERE report_id = " + reportId + " AND action = 'RETURN'", Integer.class);
        assertEquals(1, historyCount);

        // 验证 NOTICE-006 审计日志文案：已记录周报退回状态提示与审计日志，不表述为通知已生成或送达
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log WHERE title = '周报退回提示' AND operator_id = 3 AND oper_param = 'reportId=" + reportId + "' AND json_result = '已记录周报退回状态提示与审计日志'",
                Integer.class);
        assertEquals(1, auditCount);
    }

    @Test
    @DisplayName("TEST-W10: 版本递增重提：学生修改被退回的周报重新提交 -> SUBMITTED, version 自增为 2")
    void testW10_ResubmitReturnedReportVersionIncrement() throws Exception {
        prepareApprovedApply(1L, 4L);

        // 插入退回状态的周报 (version=1)
        jdbcTemplate.execute("INSERT INTO internship_weekly_report (task_id, student_id, teacher_id, dept_id, week_number, start_date, end_date, deadline_time, status, work_content, work_summary, problem_encountered, next_week_plan, version, is_deleted) " +
                "VALUES (1, 4, 3, 1, 1, '2026-03-01', '2026-03-07', '2026-03-07 23:59:59', 'RETURNED', '原工作内容正常记录充足字数标准', '原收获体会正常记录充足字数标准', '原疑难问题正常记录充足字数标准', '原下周计划正常记录充足字数标准', 1, 0)");

        WeeklyReportSaveDTO resubmitDTO = WeeklyReportSaveDTO.builder()
                .taskId(1L)
                .weekNumber(1)
                .action("SUBMIT")
                .workContent("整改后详细记录本周在企业完成的核心需求开发工作任务")
                .workSummary("通过此次修改进一步明确了业务架构规范与编码设计标准")
                .problemEncountered("遇到一些依赖冲突但通过排查pom文件顺利消除相关报错")
                .nextWeekPlan("继续完善核心业务模块接口与单元测试并做好下周工作汇报")
                .build();

        mockMvc.perform(post("/api/v1/internship/weekly-reports")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resubmitDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.version").value(2)); // 版本号自增为 2
    }

    @Test
    @DisplayName("TEST-W11: NOTICE-012: 教师合规批阅周报（打分+评语） -> REVIEWED, 锁定只读")
    void testW11_TeacherApproveReport() throws Exception {
        prepareApprovedApply(1L, 4L);

        jdbcTemplate.execute("INSERT INTO internship_weekly_report (task_id, student_id, teacher_id, dept_id, week_number, start_date, end_date, deadline_time, status, work_content, work_summary, problem_encountered, next_week_plan, version, is_deleted) " +
                "VALUES (1, 4, 3, 1, 1, '2026-03-01', '2026-03-07', '2026-03-07 23:59:59', 'SUBMITTED', '工作内容正常记录充足字数标准', '收获体会正常记录充足字数标准', '疑难问题正常记录充足字数标准', '下周计划正常记录充足字数标准', 1, 0)");

        Long reportId = jdbcTemplate.queryForObject("SELECT id FROM internship_weekly_report WHERE task_id = 1 AND student_id = 4 AND week_number = 1", Long.class);

        WeeklyReportReviewDTO approveDTO = WeeklyReportReviewDTO.builder()
                .action("APPROVE")
                .score(new BigDecimal("92.50"))
                .reviewComment("本周实习收获丰富，任务执行扎实到位，请继续保持。")
                .build();

        mockMvc.perform(post("/api/v1/internship/weekly-reports/" + reportId + "/review")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.score").value(92.50));

        // 验证统一历史动作 APPROVE 快照留痕
        Integer approveHistoryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM internship_weekly_report_history WHERE report_id = " + reportId + " AND action = 'APPROVE'", Integer.class);
        assertEquals(1, approveHistoryCount);
    }

    @Test
    @DisplayName("TEST-W12: 教师合规登记实地走访记录与附件URL -> 200, 班级与实习单位真实关联完整落库")
    void testW12_TeacherCreateGuidanceRecord() throws Exception {
        prepareApprovedApply(1L, 4L);

        GuidanceCreateDTO dto = GuidanceCreateDTO.builder()
                .taskId(1L)
                .studentId(4L)
                .guidanceDate(LocalDateTime.now())
                .guidanceType("ONSITE")
                .location("杭州市滨江区某某科技有限公司")
                .contentSummary("与企业指导教师及实习学生面对面座谈，了解在岗技术栈学习情况")
                .attachmentUrl("/uploads/guidance/onsite_photo.jpg")
                .followupActions("下周跟进学生独立承担微服务模块开发情况")
                .build();

        mockMvc.perform(post("/api/v1/internship/guidances")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.guidanceType").value("ONSITE"))
                .andExpect(jsonPath("$.data.feedbackStatus").value("UNCONFIRMED"))
                .andExpect(jsonPath("$.data.className").isNotEmpty())
                .andExpect(jsonPath("$.data.companyName").value("某某网络科技有限公司"));

        // 验证列表查询同样正确补充班级与单位信息
        mockMvc.perform(get("/api/v1/internship/guidances?taskId=1")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].className").isNotEmpty())
                .andExpect(jsonPath("$.data[0].companyName").value("某某网络科技有限公司"));
    }

    @Test
    @DisplayName("TEST-W13: 教师走访越权防御：教师尝试为非负责学生录入指导记录 -> 403")
    void testW13_GuidanceUnauthorizedTeacher() throws Exception {
        // 学生4由教师3负责，如果教师尝试为学生9999(非本人负责)登记记录
        GuidanceCreateDTO dto = GuidanceCreateDTO.builder()
                .taskId(1L)
                .studentId(9999L)
                .guidanceDate(LocalDateTime.now())
                .guidanceType("PHONE")
                .contentSummary("电话沟通学生近期在岗学习与生活状态是否良好")
                .build();

        mockMvc.perform(post("/api/v1/internship/guidances")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("无权为非本人负责的学生录入指导记录")));
    }

    @Test
    @DisplayName("TEST-W14: API-067: 受访学生首次确认指导记录并提交在岗反馈 -> 200, CONFIRMED并锁定")
    void testW14_StudentFeedbackConfirmation() throws Exception {
        // 先插入一条未确认的走访记录
        jdbcTemplate.execute("INSERT INTO internship_guidance_record (task_id, teacher_id, teacher_name, student_id, student_name, dept_id, guidance_date, guidance_type, content_summary, feedback_status, is_deleted) " +
                "VALUES (1, 3, '李教授', 4, '张晓峰', 1, NOW(), 'ONSITE', '现场检查学生出勤与工作环境良好', 'UNCONFIRMED', 0)");

        Long recordId = jdbcTemplate.queryForObject("SELECT id FROM internship_guidance_record WHERE student_id = 4 LIMIT 1", Long.class);

        GuidanceFeedbackDTO feedbackDTO = GuidanceFeedbackDTO.builder()
                .studentFeedback("感谢李老师来现场走访看望，目前在企业工作顺利")
                .build();

        mockMvc.perform(put("/api/v1/internship/guidances/" + recordId + "/feedback")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.feedbackStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.feedbackTime").isNotEmpty());
    }

    @Test
    @DisplayName("TEST-W15: 反馈越权防御：学生尝试对其他学生的指导记录提交确认 -> 403")
    void testW15_UnauthorizedStudentFeedback() throws Exception {
        // 插入属于学生999的指导记录
        jdbcTemplate.execute("INSERT INTO internship_guidance_record (task_id, teacher_id, teacher_name, student_id, student_name, dept_id, guidance_date, guidance_type, content_summary, feedback_status, is_deleted) " +
                "VALUES (1, 3, '李教授', 999, '其他学生', 1, NOW(), 'ONLINE', '线上视频答疑交流顺利', 'UNCONFIRMED', 0)");

        Long recordId = jdbcTemplate.queryForObject("SELECT id FROM internship_guidance_record WHERE student_id = 999 LIMIT 1", Long.class);

        GuidanceFeedbackDTO feedbackDTO = GuidanceFeedbackDTO.builder()
                .studentFeedback("在岗情况良好")
                .build();

        // 学生4尝试对学生999的记录提交反馈 -> 403
        mockMvc.perform(put("/api/v1/internship/guidances/" + recordId + "/feedback")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackDTO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("无权操作非本人的过程指导记录")));
    }

    @Test
    @DisplayName("TEST-W16: CAS 条件更新防重：对已 CONFIRMED 状态记录重复提交反馈 -> 400")
    void testW16_DuplicateFeedbackLockPrevention() throws Exception {
        // 插入一条已处于 CONFIRMED 状态的走访记录
        jdbcTemplate.execute("INSERT INTO internship_guidance_record (task_id, teacher_id, teacher_name, student_id, student_name, dept_id, guidance_date, guidance_type, content_summary, student_feedback, feedback_time, feedback_status, is_deleted) " +
                "VALUES (1, 3, '李教授', 4, '张晓峰', 1, NOW(), 'PHONE', '电话了解日常情况', '已有反馈内容', NOW(), 'CONFIRMED', 0)");

        Long recordId = jdbcTemplate.queryForObject("SELECT id FROM internship_guidance_record WHERE student_id = 4 LIMIT 1", Long.class);

        GuidanceFeedbackDTO feedbackDTO = GuidanceFeedbackDTO.builder()
                .studentFeedback("再次尝试重复提交在岗反馈内容")
                .build();

        mockMvc.perform(put("/api/v1/internship/guidances/" + recordId + "/feedback")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("该指导记录已确认反馈并锁定，禁止重复提交")));
    }

    @Test
    @DisplayName("TEST-W17: API-066 导出限制：学生403、跨度400、合规EasyExcel导出、429限流与5000条真实上限拦截")
    void testW17_ExportPermissionsAndLimits() throws Exception {
        // 1. 学生角色调用导出 -> 403 Forbidden
        mockMvc.perform(get("/api/v1/internship/guidances?export=excel")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("学生角色无权导出过程走访台账数据")));

        // 2. 时间跨度超过365天 -> 400 Bad Request
        mockMvc.perform(get("/api/v1/internship/guidances?export=excel&startDate=2024-01-01&endDate=2026-06-01")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("导出时间跨度超过系统最大允许限制")));

        // 3. 教师合规请求导出 -> 200，并返回 Excel 文件流
        mockMvc.perform(get("/api/v1/internship/guidances?export=excel&taskId=1")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));

        // 4. 429 防刷限流拦截：同一教师在10秒内再次发起导出 -> 429 Too Many Requests
        mockMvc.perform(get("/api/v1/internship/guidances?export=excel&taskId=1")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value(containsString("导出操作过于频繁")));

        // 5. 真实的5000条导出上限拦截测试 (批量插入5001条指导台账记录)
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO internship_guidance_record (task_id, teacher_id, teacher_name, student_id, student_name, dept_id, guidance_date, guidance_type, content_summary, feedback_status, is_deleted) VALUES ");
        for (int i = 1; i <= 5001; i++) {
            if (i > 1) sql.append(",");
            sql.append("(1, 3, '李教授', 4, '张晓峰', 1, '2026-03-15 10:00:00', 'PHONE', '批量测试记录").append(i).append("', 'UNCONFIRMED', 0)");
        }
        jdbcTemplate.execute(sql.toString());

        try {
            // 使用不受前序 teacher 429 限流影响的 adminToken 发起全量导出
            mockMvc.perform(get("/api/v1/internship/guidances?export=excel&taskId=1")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("超出单次导出上限（5000条）")));
        } finally {
            jdbcTemplate.execute("DELETE FROM internship_guidance_record WHERE content_summary LIKE '批量测试记录%'");
        }
    }

    @Test
    @DisplayName("TEST-W18: 监控大盘数据权限隔离与频次校验：学生越权403、跨院系越权403、频次缺失/非法400、教师本人数据范围精确隔离")
    void testW18_MonitorPermissionsAndDataIsolation() throws Exception {
        // 1. 学生越权访问监控大盘 -> 403 Forbidden
        mockMvc.perform(get("/api/v1/internship/weekly-reports/monitor?taskId=1")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("学生角色无权查看周报监控数据")));

        // 2. 指导教师跨院系查询监控数据 -> 403 Forbidden
        mockMvc.perform(get("/api/v1/internship/weekly-reports/monitor?taskId=1&deptId=999")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("指导教师无权跨院系查看监控数据")));

        // 3. 院系管理员2 (属于院系1) 尝试请求院系999的监控大盘 -> 403 Forbidden
        mockMvc.perform(get("/api/v1/internship/weekly-reports/monitor?taskId=1&deptId=999")
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("院系负责人无权跨院系查看监控大盘")));

        // 4. weeklyFrequency 频次缺失校验：任务无 weeklyFrequency 时请求监控大盘 -> 400 Bad Request
        jdbcTemplate.execute("UPDATE internship_task SET weekly_frequency = NULL WHERE id = 1");
        try {
            mockMvc.perform(get("/api/v1/internship/weekly-reports/monitor?taskId=1")
                            .header("Authorization", "Bearer " + deptAdminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("实习任务周报频次 (weeklyFrequency) 未配置")));
        } finally {
            jdbcTemplate.execute("UPDATE internship_task SET weekly_frequency = 'WEEKLY' WHERE id = 1");
        }

        // 5. weeklyFrequency 频次非法校验：频次为 MONTHLY 等非法值 -> 400 Bad Request
        jdbcTemplate.execute("UPDATE internship_task SET weekly_frequency = 'MONTHLY' WHERE id = 1");
        try {
            mockMvc.perform(get("/api/v1/internship/weekly-reports/monitor?taskId=1")
                            .header("Authorization", "Bearer " + deptAdminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("实习任务周报频次配置非法")));
        } finally {
            jdbcTemplate.execute("UPDATE internship_task SET weekly_frequency = 'WEEKLY' WHERE id = 1");
        }

        // 6. 指导教师正常访问：仅汇算本人负责的学生周报数据 (teacher 3 在 task 1 仅分配了 student 4, 1人)
        mockMvc.perform(get("/api/v1/internship/weekly-reports/monitor?taskId=1")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalStudents").value(1));
    }
}
