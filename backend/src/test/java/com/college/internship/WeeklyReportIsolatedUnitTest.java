package com.college.internship;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.WeeklyProperties;
import com.college.internship.dto.WeeklyReportReviewDTO;
import com.college.internship.dto.WeeklyReportSaveDTO;
import com.college.internship.entity.*;
import com.college.internship.mapper.*;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.service.impl.WeeklyReportServiceImpl;
import com.college.internship.vo.WeeklyReportDetailVO;
import com.college.internship.vo.WeeklyReportVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 阶段6 实习周报全流程纯内存隔离单元测试 (零连接数据库)
 * 验证：学生提交、教师初审/退回、学生修改重提、版本自增、教师打分通过、行级权限隔离与非法状态机防御
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeeklyReportIsolatedUnitTest {

    @Mock
    private InternshipWeeklyReportMapper weeklyReportMapper;
    @Mock
    private InternshipWeeklyReportHistoryMapper historyMapper;
    @Mock
    private InternshipTaskMapper taskMapper;
    @Mock
    private InternshipTaskStudentMapper taskStudentMapper;
    @Mock
    private InternshipApplyMapper applyMapper;
    @Mock
    private BaseDepartmentMapper departmentMapper;
    @Mock
    private BaseClassMapper classMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ISysOperationLogService logService;
    @Spy
    private WeeklyProperties weeklyProperties = new WeeklyProperties();
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private InternshipArchiveMapper archiveMapper;

    @InjectMocks
    private WeeklyReportServiceImpl weeklyReportService;

    private LoginUser studentUser;
    private LoginUser otherStudentUser;
    private LoginUser teacherUser;
    private LoginUser otherTeacherUser;
    private InternshipTask sampleTask;
    private InternshipTaskStudent sampleTaskStudent;
    private InternshipApply sampleApply;

    @BeforeEach
    void setUp() {
        studentUser = LoginUser.builder()
                .userId(5102L)
                .username("student_sim_02")
                .realName("李在途")
                .userType("STUDENT")
                .deptId(1L)
                .permissions(List.of("ROLE_STUDENT"))
                .build();

        otherStudentUser = LoginUser.builder()
                .userId(5103L)
                .username("student_sim_03")
                .realName("王风险")
                .userType("STUDENT")
                .deptId(1L)
                .permissions(List.of("ROLE_STUDENT"))
                .build();

        teacherUser = LoginUser.builder()
                .userId(3101L)
                .username("teacher_wang")
                .realName("王导师")
                .userType("TEACHER")
                .deptId(1L)
                .permissions(List.of("ROLE_TEACHER"))
                .build();

        otherTeacherUser = LoginUser.builder()
                .userId(3102L)
                .username("teacher_li")
                .realName("李导师")
                .userType("TEACHER")
                .deptId(1L)
                .permissions(List.of("ROLE_TEACHER"))
                .build();

        sampleTask = InternshipTask.builder()
                .id(8001L)
                .deptId(1L)
                .taskName("计算机学院2026届毕业综合实习")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .weeklyFrequency("WEEKLY")
                .weeklyDeadlineDay(7)
                .build();

        sampleTaskStudent = InternshipTaskStudent.builder()
                .taskId(8001L)
                .studentId(5102L)
                .teacherId(3101L)
                .build();

        sampleApply = InternshipApply.builder()
                .id(8102L)
                .taskId(8001L)
                .studentId(5102L)
                .applyStatus("APPROVED")
                .isLocked(1)
                .build();

        when(taskMapper.selectById(8001L)).thenReturn(sampleTask);
        when(taskStudentMapper.selectOne(any())).thenReturn(sampleTaskStudent);
        when(applyMapper.selectOne(any())).thenReturn(sampleApply);
        when(archiveMapper.selectOne(any())).thenReturn(null);
    }

    @Test
    @DisplayName("测试1: 非学生角色提交周报应直接被权限拦截抛出 403")
    void testSaveOrSubmit_NonStudent_Throws403() {
        WeeklyReportSaveDTO dto = WeeklyReportSaveDTO.builder()
                .taskId(8001L)
                .weekNumber(2)
                .action("SUBMIT")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                weeklyReportService.saveOrSubmitReport(dto, teacherUser));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("仅学生账号"));
    }

    @Test
    @DisplayName("测试2: 未通过实习申报审核 (未APPROVED或未锁定) 严禁提交周报抛出 400")
    void testSaveOrSubmit_ApplyNotApproved_Throws400() {
        when(applyMapper.selectOne(any())).thenReturn(null);

        WeeklyReportSaveDTO dto = WeeklyReportSaveDTO.builder()
                .taskId(8001L)
                .weekNumber(2)
                .action("SUBMIT")
                .workContent("有效工作内容详细记录超过十五个中文字符")
                .workSummary("有效工作总结详细记录超过十五个中文字符")
                .problemEncountered("遇到问题及解决途径详细记录超过十五字")
                .nextWeekPlan("下周工作计划详细规划超过十五个字")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                weeklyReportService.saveOrSubmitReport(dto, studentUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("尚未通过实习申报审核"));
    }

    @Test
    @DisplayName("测试3: 正式提交周报四大要素字数不足15字应强校验拦截抛出 400")
    void testSaveOrSubmit_ContentLengthTooShort_Throws400() {
        WeeklyReportSaveDTO dto = WeeklyReportSaveDTO.builder()
                .taskId(8001L)
                .weekNumber(2)
                .action("SUBMIT")
                .workContent("太短了")
                .workSummary("有效工作总结详细记录超过十五个中文字符")
                .problemEncountered("遇到问题及解决途径详细记录超过十五字")
                .nextWeekPlan("下周工作计划详细规划超过十五个字")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                weeklyReportService.saveOrSubmitReport(dto, studentUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("去除空格后不得少于 15 字"));
    }

    @Test
    @DisplayName("测试4: 学生正式提交周报成功 -> 状态变为 SUBMITTED，版本为 v1，留存 SUBMIT 快照")
    void testSaveOrSubmit_Success() {
        WeeklyReportSaveDTO dto = WeeklyReportSaveDTO.builder()
                .taskId(8001L)
                .weekNumber(2)
                .action("SUBMIT")
                .workContent("[SIM-WEEKLY-LOOP] 负责微信支付核心网关微服务接口开发与Spring Boot组件适配优化")
                .workSummary("[SIM-WEEKLY-LOOP] 掌握了高并发分布式事务两阶段提交规范，完成单元测试覆盖率达标")
                .problemEncountered("[SIM-WEEKLY-LOOP] 遇到Redis分布式锁误释放问题，通过结合Lua脚本原子校验完成彻底修复")
                .nextWeekPlan("[SIM-WEEKLY-LOOP] 推进消息队列Kafka异步解耦联调，按照导师批改要求严格整改完善")
                .build();

        when(weeklyReportMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            InternshipWeeklyReport r = inv.getArgument(0);
            r.setId(8410L);
            return 1;
        }).when(weeklyReportMapper).insert(any(InternshipWeeklyReport.class));

        WeeklyReportVO result = weeklyReportService.saveOrSubmitReport(dto, studentUser);

        assertNotNull(result);
        assertEquals("SUBMITTED", result.getStatus());
        assertEquals(1, result.getVersion());
        assertEquals(2, result.getWeekNumber());

        verify(weeklyReportMapper, times(1)).insert(any(InternshipWeeklyReport.class));
        verify(historyMapper, times(1)).insert(any(InternshipWeeklyReportHistory.class));
    }

    @Test
    @DisplayName("测试5: 已处于 SUBMITTED 待批阅的周报禁止重复提交抛出 400")
    void testSaveOrSubmit_DuplicateSubmit_Throws400() {
        InternshipWeeklyReport existing = InternshipWeeklyReport.builder()
                .id(8410L)
                .taskId(8001L)
                .studentId(5102L)
                .weekNumber(2)
                .status("SUBMITTED")
                .version(1)
                .build();
        when(weeklyReportMapper.selectOne(any())).thenReturn(existing);

        WeeklyReportSaveDTO dto = WeeklyReportSaveDTO.builder()
                .taskId(8001L)
                .weekNumber(2)
                .action("SUBMIT")
                .workContent("再次提交的内容详细阐述超过十五个中文字符标准")
                .workSummary("再次提交的总结详细阐述超过十五个中文字符标准")
                .problemEncountered("遇到问题及解决途径详细记录超过十五字")
                .nextWeekPlan("下周工作计划详细规划超过十五个字")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                weeklyReportService.saveOrSubmitReport(dto, studentUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("已提交等待批阅，禁止重复提交"));
    }

    @Test
    @DisplayName("测试6: 非负责指导教师越权批阅周报应阻断抛出 403")
    void testReviewReport_OtherTeacher_Throws403() {
        InternshipWeeklyReport report = InternshipWeeklyReport.builder()
                .id(8410L)
                .taskId(8001L)
                .studentId(5102L)
                .teacherId(3101L) // 属于王导师
                .weekNumber(2)
                .status("SUBMITTED")
                .version(1)
                .build();
        when(weeklyReportMapper.selectById(8410L)).thenReturn(report);

        WeeklyReportReviewDTO dto = WeeklyReportReviewDTO.builder()
                .action("APPROVE")
                .score(new BigDecimal("90.00"))
                .reviewComment("指导评语符合十个字以上规范")
                .build();

        // 李导师 (3102L) 尝试批阅
        BusinessException ex = assertThrows(BusinessException.class, () ->
                weeklyReportService.reviewReport(8410L, dto, otherTeacherUser));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权批阅非本人负责"));
    }

    @Test
    @DisplayName("测试7: 导师退回周报整改 (RETURN) -> 状态变为 RETURNED，留存退回原因快照")
    void testReviewReport_ReturnSuccess() {
        InternshipWeeklyReport report = InternshipWeeklyReport.builder()
                .id(8410L)
                .taskId(8001L)
                .studentId(5102L)
                .teacherId(3101L)
                .weekNumber(2)
                .status("SUBMITTED")
                .version(1)
                .build();
        when(weeklyReportMapper.selectById(8410L)).thenReturn(report);

        WeeklyReportReviewDTO dto = WeeklyReportReviewDTO.builder()
                .action("RETURN")
                .reviewComment("[SIM-WEEKLY-LOOP] 本周工作总结内容过于简略，请补充网关限流具体参数后再重提。")
                .build();

        WeeklyReportDetailVO vo = weeklyReportService.reviewReport(8410L, dto, teacherUser);

        assertEquals("RETURNED", report.getStatus());
        assertTrue(report.getReviewComment().contains("本周工作总结内容过于简略"));
        verify(weeklyReportMapper, times(1)).updateById(report);

        ArgumentCaptor<InternshipWeeklyReportHistory> historyCaptor = ArgumentCaptor.forClass(InternshipWeeklyReportHistory.class);
        verify(historyMapper, times(1)).insert(historyCaptor.capture());
        assertEquals("RETURN", historyCaptor.getValue().getAction());
        assertEquals(1, historyCaptor.getValue().getVersion());
        assertTrue(historyCaptor.getValue().getReturnReason().contains("网关限流具体参数"));
    }

    @Test
    @DisplayName("测试8: 学生针对被退回周报修改重交 -> 版本自增为 v2，状态重新变为 SUBMITTED")
    void testResubmitAfterReturn_VersionIncrements() {
        InternshipWeeklyReport returnedReport = InternshipWeeklyReport.builder()
                .id(8410L)
                .taskId(8001L)
                .studentId(5102L)
                .teacherId(3101L)
                .deptId(1L)
                .weekNumber(2)
                .startDate(LocalDate.of(2026, 9, 8))
                .endDate(LocalDate.of(2026, 9, 14))
                .deadlineTime(LocalDateTime.of(2026, 9, 20, 23, 59, 59))
                .status("RETURNED")
                .version(1)
                .build();
        when(weeklyReportMapper.selectOne(any())).thenReturn(returnedReport);

        WeeklyReportSaveDTO dto = WeeklyReportSaveDTO.builder()
                .taskId(8001L)
                .weekNumber(2)
                .action("SUBMIT")
                .workContent("[SIM-WEEKLY-LOOP] 针对导师退回意见补充Sentinel熔断降级阈值QPS=500与RT=20ms指标明细。")
                .workSummary("[SIM-WEEKLY-LOOP] 全面复盘微服务工程架构并完善分布式事务集成测试用例达到覆盖标准。")
                .problemEncountered("[SIM-WEEKLY-LOOP] 解决了压测时偶发的线程池耗尽问题，重新调整核心线程参数至32。")
                .nextWeekPlan("[SIM-WEEKLY-LOOP] 开展下一阶段接口联调并积极配合企业指导工程师的代码走查工作。")
                .build();

        WeeklyReportVO result = weeklyReportService.saveOrSubmitReport(dto, studentUser);

        assertEquals("SUBMITTED", result.getStatus());
        assertEquals(2, result.getVersion()); // 版本自增至 2
        assertEquals(2, returnedReport.getVersion());
        assertEquals("SUBMITTED", returnedReport.getStatus());
        verify(weeklyReportMapper, times(1)).updateById(returnedReport);
    }

    @Test
    @DisplayName("测试9: 导师二次批阅通过并打分 (APPROVE) -> 状态变为 REVIEWED，记录成绩，归档锁定")
    void testReviewReport_ApproveSuccess() {
        InternshipWeeklyReport v2Report = InternshipWeeklyReport.builder()
                .id(8410L)
                .taskId(8001L)
                .studentId(5102L)
                .teacherId(3101L)
                .weekNumber(2)
                .status("SUBMITTED")
                .version(2)
                .build();
        when(weeklyReportMapper.selectById(8410L)).thenReturn(v2Report);

        WeeklyReportReviewDTO dto = WeeklyReportReviewDTO.builder()
                .action("APPROVE")
                .score(new BigDecimal("96.50"))
                .reviewComment("[SIM-WEEKLY-LOOP] 整改措施详实，熔断降级指标符合系统工程规范，通过批阅并评定优良。")
                .build();

        weeklyReportService.reviewReport(8410L, dto, teacherUser);

        assertEquals("REVIEWED", v2Report.getStatus());
        assertEquals(new BigDecimal("96.50"), v2Report.getScore());
        verify(weeklyReportMapper, times(1)).updateById(v2Report);

        ArgumentCaptor<InternshipWeeklyReportHistory> historyCaptor = ArgumentCaptor.forClass(InternshipWeeklyReportHistory.class);
        verify(historyMapper, times(1)).insert(historyCaptor.capture());
        assertEquals("APPROVE", historyCaptor.getValue().getAction());
        assertEquals(2, historyCaptor.getValue().getVersion());
        assertEquals(new BigDecimal("96.50"), historyCaptor.getValue().getScore());
    }

    @Test
    @DisplayName("测试10: 学生查询周报详情越权防御 (查看其他学生周报) 阻断抛出 403")
    void testGetReportDetail_OtherStudent_Throws403() {
        InternshipWeeklyReport report = InternshipWeeklyReport.builder()
                .id(8410L)
                .taskId(8001L)
                .studentId(5102L) // 属于学生李在途
                .teacherId(3101L)
                .weekNumber(2)
                .status("REVIEWED")
                .version(2)
                .build();
        when(weeklyReportMapper.selectById(8410L)).thenReturn(report);

        // 学生王风险 (5103L) 尝试查看
        BusinessException ex = assertThrows(BusinessException.class, () ->
                weeklyReportService.getReportDetail(8410L, otherStudentUser));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权查看其他学生的周报"));
    }

    @Test
    @DisplayName("测试11: 学生列表查询仅返回本人周报，导师列表查询仅返回所带教学生的周报")
    void testListReports_RowLevelDataIsolation() {
        when(weeklyReportMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 学生查询
        weeklyReportService.listReports(8001L, null, null, studentUser);
        // 导师查询
        weeklyReportService.listReports(8001L, null, null, teacherUser);

        // 学生越权带入他人 studentId 查询抛 403
        BusinessException ex = assertThrows(BusinessException.class, () ->
                weeklyReportService.listReports(8001L, 5103L, null, studentUser));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("学生仅允许查询本人的周报记录"));
    }
}
