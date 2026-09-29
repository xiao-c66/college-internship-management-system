package com.college.internship;

import com.college.internship.common.BusinessException;
import com.college.internship.config.Phase7Properties;
import com.college.internship.dto.WarnFeedbackDTO;
import com.college.internship.dto.WarnHandleDTO;
import com.college.internship.dto.WarnTicketDispatchDTO;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.entity.WarnProcessHistory;
import com.college.internship.entity.WarnRuleConfig;
import com.college.internship.entity.WarnTicket;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.InternshipApplyMapper;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.InternshipTaskStudentMapper;
import com.college.internship.mapper.InternshipWeeklyReportMapper;
import com.college.internship.mapper.MidtermInspectionMapper;
import com.college.internship.mapper.MidtermRectificationMapper;
import com.college.internship.mapper.SafetyCommitmentSignMapper;
import com.college.internship.mapper.ScoreSummaryMapper;
import com.college.internship.mapper.StudentMaterialItemMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.mapper.WarnProcessHistoryMapper;
import com.college.internship.mapper.WarnRuleConfigMapper;
import com.college.internship.mapper.WarnTicketMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.impl.WarnServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 异常预警三条分支纯内存隔离单元测试 (不连接真实/共享数据库)
 * 分支 1: 预警正常闭环 (Trigger -> Dispatch -> Feedback -> Closed, active_dedup_key 释放)
 * 分支 2: 工单指派/转派 (责任人、状态、流转记录与角色权限)
 * 分支 3: 超时自动升级与任务边界隔离 (升级为院系、TIMEOUT_UPGRADE 流转、任务范围严格隔离)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarnServiceIsolatedUnitTest {

    @Mock
    private WarnRuleConfigMapper ruleMapper;
    @Mock
    private WarnTicketMapper ticketMapper;
    @Mock
    private WarnProcessHistoryMapper historyMapper;
    @Mock
    private InternshipTaskMapper taskMapper;
    @Mock
    private InternshipTaskStudentMapper taskStudentMapper;
    @Mock
    private SafetyCommitmentSignMapper commitmentSignMapper;
    @Mock
    private InternshipApplyMapper applyMapper;
    @Mock
    private InternshipWeeklyReportMapper weeklyReportMapper;
    @Mock
    private MidtermInspectionMapper inspectionMapper;
    @Mock
    private MidtermRectificationMapper rectificationMapper;
    @Mock
    private StudentMaterialItemMapper materialItemMapper;
    @Mock
    private ScoreSummaryMapper scoreMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private BaseDepartmentMapper departmentMapper;
    @Mock
    private BaseClassMapper classMapper;
    @Mock
    private Phase7Properties phase7Properties;

    @InjectMocks
    private WarnServiceImpl warnService;

    private Phase7Properties.WarnConfig warnConfig;

    @BeforeEach
    void setUp() {
        warnConfig = new Phase7Properties.WarnConfig();
        warnConfig.setHandlingTimeoutDays(3);
        warnConfig.setScanRateLimitSeconds(10);
        when(phase7Properties.getWarn()).thenReturn(warnConfig);
    }

    // ==========================================
    // 分支 1: 预警正常闭环与 active_dedup_key 释放
    // ==========================================
    @Test
    @DisplayName("分支1-正常闭环: 学生提交申辩 -> 教师核查采取干预措施并完成正常闭环(CLOSED) -> 释放 active_dedup_key")
    void testBranch1_NormalClosedLoop_ReleasesActiveDedupKey() {
        Long ticketId = 9991L;
        Long studentId = 8881L;
        Long teacherId = 7771L;

        WarnTicket existingTicket = WarnTicket.builder()
                .id(ticketId)
                .ticketNo("WT202609280001")
                .taskId(5001L)
                .studentId(studentId)
                .teacherId(teacherId)
                .deptId(10L)
                .status("TRIGGERED")
                .isUpgraded(0)
                .currentAssigneeId(teacherId)
                .currentAssigneeRole("TEACHER")
                .dedupKey("DEDUP_WARN_01_5001_8881")
                .activeDedupKey("DEDUP_WARN_01_5001_8881")
                .isDeleted(0)
                .build();

        when(ticketMapper.selectById(ticketId)).thenReturn(existingTicket);

        // 步骤 1: 学生提交申辩
        LoginUser studentUser = LoginUser.builder()
                .userId(studentId)
                .username("test_student")
                .realName("测试学生甲")
                .userType("STUDENT")
                .build();

        WarnFeedbackDTO feedbackDTO = new WarnFeedbackDTO();
        feedbackDTO.setStudentFeedback("已于今日补齐三方协议并完成在线签字盖章");
        feedbackDTO.setAttachmentUrl("https://oss.example.com/voucher.pdf");

        warnService.submitFeedback(ticketId, feedbackDTO, studentUser);

        assertEquals("PROCESSING", existingTicket.getStatus());
        assertEquals("已于今日补齐三方协议并完成在线签字盖章", existingTicket.getStudentFeedback());
        assertNotNull(existingTicket.getStudentFeedbackTime());
        verify(ticketMapper, times(1)).updateById(existingTicket);

        ArgumentCaptor<WarnProcessHistory> historyCaptor = ArgumentCaptor.forClass(WarnProcessHistory.class);
        verify(historyMapper, atLeastOnce()).insert(historyCaptor.capture());
        WarnProcessHistory feedbackHistory = historyCaptor.getValue();
        assertEquals("STUDENT_FEEDBACK", feedbackHistory.getAction());
        assertEquals("测试学生甲", feedbackHistory.getOperatorName());

        // 步骤 2: 教师核实并采取措施，执行正常闭环 (CLOSED)
        LoginUser teacherUser = LoginUser.builder()
                .userId(teacherId)
                .username("test_teacher")
                .realName("测试指导教师李")
                .userType("TEACHER")
                .build();

        WarnHandleDTO handleDTO = new WarnHandleDTO();
        handleDTO.setAction("CLOSED");
        handleDTO.setHandlingMeasures("已电话回访用人单位HR并核实盖章件属实，辅导学生完成系统备案");

        warnService.handleTicket(ticketId, handleDTO, teacherUser);

        // 验证状态变为 CLOSED，处置措施记录在案，以及关键的 activeDedupKey 必须被释放为 null
        assertEquals("CLOSED", existingTicket.getStatus());
        assertEquals("已电话回访用人单位HR并核实盖章件属实，辅导学生完成系统备案", existingTicket.getHandlingMeasures());
        assertNull(existingTicket.getActiveDedupKey(), "正常闭环后必须将 active_dedup_key 释放为 null，允许未来异常再次触发");
        assertEquals(teacherId, existingTicket.getClosedBy());
        assertNotNull(existingTicket.getClosedTime());

        verify(ticketMapper, times(2)).updateById(existingTicket);
    }

    @Test
    @DisplayName("分支1-边界防御: 工单已升级至院系，指导教师尝试闭环被拒绝(403)")
    void testBranch1_UpgradedTicket_TeacherClosedForbidden() {
        Long ticketId = 9992L;
        WarnTicket upgradedTicket = WarnTicket.builder()
                .id(ticketId)
                .taskId(5001L)
                .studentId(8881L)
                .teacherId(7771L)
                .deptId(10L)
                .status("PROCESSING")
                .isUpgraded(1) // 已升级
                .isDeleted(0)
                .build();

        when(ticketMapper.selectById(ticketId)).thenReturn(upgradedTicket);

        LoginUser teacherUser = LoginUser.builder()
                .userId(7771L)
                .userType("TEACHER")
                .build();

        WarnHandleDTO handleDTO = new WarnHandleDTO();
        handleDTO.setAction("CLOSED");
        handleDTO.setHandlingMeasures("尝试直接关闭");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                warnService.handleTicket(ticketId, handleDTO, teacherUser));

        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("工单已升级至院系，只能由院系负责人闭环处置"));
    }

    // ==========================================
    // 分支 2: 工单指派/转派与权限控制
    // ==========================================
    @Test
    @DisplayName("分支2-权限控制: 教师和学生无权指派/转派工单(403)")
    void testBranch2_DispatchPermissions_TeacherAndStudentForbidden() {
        LoginUser teacherUser = LoginUser.builder().userId(7771L).userType("TEACHER").build();
        LoginUser studentUser = LoginUser.builder().userId(8881L).userType("STUDENT").build();

        WarnTicketDispatchDTO dto = new WarnTicketDispatchDTO();
        dto.setAssigneeId(7772L);
        dto.setAssigneeRole("TEACHER");

        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                warnService.dispatchTicket(9993L, dto, teacherUser));
        assertEquals(403, ex1.getCode());

        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                warnService.dispatchTicket(9993L, dto, studentUser));
        assertEquals(403, ex2.getCode());
    }

    @Test
    @DisplayName("分支2-转派成功: 院系管理员将工单指派/转派给新教师责任人并记录 DISPATCH 流转历史")
    void testBranch2_DeptAdminDispatch_Success() {
        Long ticketId = 9994L;
        Long targetTeacherId = 7773L;

        WarnTicket ticket = WarnTicket.builder()
                .id(ticketId)
                .taskId(5001L)
                .studentId(8881L)
                .teacherId(7771L)
                .currentAssigneeId(7771L)
                .currentAssigneeRole("TEACHER")
                .status("TRIGGERED")
                .isDeleted(0)
                .build();

        when(ticketMapper.selectById(ticketId)).thenReturn(ticket);

        LoginUser deptAdmin = LoginUser.builder()
                .userId(6661L)
                .username("dept_admin")
                .realName("计算机学院教学秘书")
                .userType("DEPT_ADMIN")
                .deptId(10L)
                .build();

        WarnTicketDispatchDTO dto = new WarnTicketDispatchDTO();
        dto.setAssigneeId(targetTeacherId);
        dto.setAssigneeRole("TEACHER");
        dto.setRemark("原导师出差，转派王老师协助核查督促");

        warnService.dispatchTicket(ticketId, dto, deptAdmin);

        assertEquals(targetTeacherId, ticket.getCurrentAssigneeId());
        assertEquals("TEACHER", ticket.getCurrentAssigneeRole());
        assertEquals("DISPATCHED", ticket.getStatus());
        verify(ticketMapper, times(1)).updateById(ticket);

        ArgumentCaptor<WarnProcessHistory> histCaptor = ArgumentCaptor.forClass(WarnProcessHistory.class);
        verify(historyMapper, times(1)).insert(histCaptor.capture());
        WarnProcessHistory dispatchHist = histCaptor.getValue();
        assertEquals("DISPATCH", dispatchHist.getAction());
        assertEquals(deptAdmin.getUserId(), dispatchHist.getOperatorId());
        assertTrue(dispatchHist.getContentRemark().contains("原导师出差"));
    }

    // ==========================================
    // 分支 3: 超时自动升级与任务范围严格隔离
    // ==========================================
    @Test
    @DisplayName("分支3-安全边界: 扫描必须传入 taskId，taskId 为空时禁止全局扫描(400)")
    void testBranch3_ScanTaskIdRequired_PreventGlobalScan() {
        LoginUser deptAdmin = LoginUser.builder().userId(6661L).userType("DEPT_ADMIN").deptId(10L).build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                warnService.executeScan(null, deptAdmin));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("实习任务ID不能为空，禁止执行全局预警扫描"));
    }

    @Test
    @DisplayName("分支3-超时自动升级: 扫描指定 taskId 时，将该任务下逾期未处置的活动工单自动升级至院系，并生成 TIMEOUT_UPGRADE 流转记录")
    void testBranch3_TimeoutUpgrade_UpgradesToDeptAdmin() {
        Long targetTaskId = 5002L;

        InternshipTask task = InternshipTask.builder()
                .id(targetTaskId)
                .deptId(10L)
                .taskName("2026计算机专业毕业实习")
                .build();
        when(taskMapper.selectById(targetTaskId)).thenReturn(task);
        when(taskStudentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(ruleMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 构造一个 5 天前创建、状态为 TRIGGERED 的工单（超时阈值为 3 天）
        WarnTicket overdueTicket = WarnTicket.builder()
                .id(9995L)
                .taskId(targetTaskId)
                .studentId(8882L)
                .teacherId(7771L)
                .currentAssigneeId(7771L)
                .currentAssigneeRole("TEACHER")
                .status("TRIGGERED")
                .isUpgraded(0)
                .isDeleted(0)
                .build();
        overdueTicket.setCreatedAt(LocalDateTime.now().minusDays(5));

        // 模拟 ticketMapper 针对 targetTaskId 查出该工单
        when(ticketMapper.selectList(any())).thenReturn(List.of(overdueTicket));

        LoginUser deptAdmin = LoginUser.builder()
                .userId(6661L)
                .userType("DEPT_ADMIN")
                .deptId(10L)
                .build();

        Map<String, Object> result = warnService.executeScan(targetTaskId, deptAdmin);

        assertEquals(1, result.get("upgradedCount"));
        assertEquals(1, overdueTicket.getIsUpgraded());
        assertEquals("DEPT_ADMIN", overdueTicket.getCurrentAssigneeRole());
        assertEquals("处置超时自动升级至院系", overdueTicket.getUpgradeReason());
        assertNotNull(overdueTicket.getUpgradedTime());

        verify(ticketMapper, times(1)).updateById(overdueTicket);

        ArgumentCaptor<WarnProcessHistory> histCaptor = ArgumentCaptor.forClass(WarnProcessHistory.class);
        verify(historyMapper, times(1)).insert(histCaptor.capture());
        WarnProcessHistory upgradeHist = histCaptor.getValue();
        assertEquals("TIMEOUT_UPGRADE", upgradeHist.getAction());
        assertEquals("SYSTEM", upgradeHist.getOperatorRole());
        assertTrue(upgradeHist.getContentRemark().contains("系统自动升级至院系责任人"));
    }
}
