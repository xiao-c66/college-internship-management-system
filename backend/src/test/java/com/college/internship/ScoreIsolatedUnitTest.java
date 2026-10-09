package com.college.internship;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.Phase7Properties;
import com.college.internship.dto.ScoreAppealDTO;
import com.college.internship.dto.ScoreArbitrateDTO;
import com.college.internship.dto.ScoreSubmitDTO;
import com.college.internship.entity.*;
import com.college.internship.mapper.*;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IArchiveService;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.service.impl.ScoreServiceImpl;
import com.college.internship.vo.ScoreSummaryVO;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 成绩录入、发布与学生申诉闭环纯内存隔离单元测试 (零连接数据库)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScoreIsolatedUnitTest {

    @Mock
    private ScoreSummaryMapper scoreMapper;
    @Mock
    private ScoreAuditHistoryMapper historyMapper;
    @Mock
    private InternshipTaskMapper taskMapper;
    @Mock
    private InternshipTaskStudentMapper taskStudentMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private BaseDepartmentMapper departmentMapper;
    @Mock
    private BaseMajorMapper majorMapper;
    @Mock
    private BaseClassMapper classMapper;
    @Mock
    private ISysOperationLogService logService;
    @Mock
    private IArchiveService archiveService;

    @Spy
    private Phase7Properties phase7Properties = new Phase7Properties();
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ScoreServiceImpl scoreService;

    private LoginUser teacherUser;
    private LoginUser otherTeacherUser;
    private LoginUser studentUser;
    private LoginUser otherStudentUser;
    private LoginUser deptAdminUser;
    private InternshipTask standardTask;
    private InternshipTaskStudent taskStudentBinding;

    @BeforeEach
    void setUp() {
        teacherUser = LoginUser.builder().userId(3102L).username("teacher_li").realName("李导师").userType("TEACHER").deptId(1L).build();
        otherTeacherUser = LoginUser.builder().userId(3101L).username("teacher_wang").realName("王导师").userType("TEACHER").deptId(1L).build();
        studentUser = LoginUser.builder().userId(5103L).username("student_sim_03").realName("王申诉").userType("STUDENT").deptId(1L).build();
        otherStudentUser = LoginUser.builder().userId(5101L).username("student_sim_01").realName("张优秀").userType("STUDENT").deptId(1L).build();
        deptAdminUser = LoginUser.builder().userId(2L).username("deptadmin").realName("计通院系负责人").userType("DEPT_ADMIN").deptId(1L).build();

        standardTask = new InternshipTask();
        standardTask.setId(8001L);
        standardTask.setTaskName("软件工程2026届毕业实习");
        standardTask.setDeptId(1L);
        standardTask.setWeightEnterprise(new BigDecimal("20.00"));
        standardTask.setWeightTeacherProcess(new BigDecimal("20.00"));
        standardTask.setWeightWeeklyReport(new BigDecimal("20.00"));
        standardTask.setWeightStageMaterial(new BigDecimal("20.00"));
        standardTask.setWeightSummary(new BigDecimal("20.00"));

        taskStudentBinding = new InternshipTaskStudent();
        taskStudentBinding.setTaskId(8001L);
        taskStudentBinding.setStudentId(5103L);
        taskStudentBinding.setTeacherId(3102L);

        when(taskMapper.selectById(8001L)).thenReturn(standardTask);
        when(taskStudentMapper.selectOne(any())).thenReturn(taskStudentBinding);
    }

    @Test
    @DisplayName("1. 教师只能录入自己负责学生的成绩 - 非负责教师提交抛出 403")
    void testSubmitScore_TeacherNotAssigned_Throws403() {
        ScoreSubmitDTO dto = new ScoreSubmitDTO();
        dto.setTaskId(8001L);
        dto.setStudentId(5103L);
        dto.setEnterpriseScore(new BigDecimal("85.0"));
        dto.setProcessScore(new BigDecimal("85.0"));
        dto.setWeeklyScore(new BigDecimal("85.0"));
        dto.setMaterialScore(new BigDecimal("85.0"));
        dto.setSummaryScore(new BigDecimal("85.0"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                scoreService.submitScore(dto, otherTeacherUser));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权为非负责管辖的学生录入成绩"));
    }

    @Test
    @DisplayName("2. 杜绝 0 分掩盖与分项 NULL 校验 - 存在 NULL 分项禁止提交")
    void testSubmitScore_NullDimension_Throws400() {
        ScoreSubmitDTO dto = new ScoreSubmitDTO();
        dto.setTaskId(8001L);
        dto.setStudentId(5103L);
        dto.setEnterpriseScore(new BigDecimal("85.0"));
        dto.setProcessScore(null); // 缺失分项
        dto.setWeeklyScore(new BigDecimal("85.0"));
        dto.setMaterialScore(new BigDecimal("85.0"));
        dto.setSummaryScore(new BigDecimal("85.0"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                scoreService.submitScore(dto, teacherUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("存在未录入项"));
    }

    @Test
    @DisplayName("3. 任务五项权重总和校验 - 不等于 100% 严格拦截")
    void testSubmitScore_WeightsNot100_Throws400() {
        InternshipTask badWeightTask = new InternshipTask();
        badWeightTask.setId(8002L);
        badWeightTask.setWeightEnterprise(new BigDecimal("20.00"));
        badWeightTask.setWeightTeacherProcess(new BigDecimal("20.00"));
        badWeightTask.setWeightWeeklyReport(new BigDecimal("20.00"));
        badWeightTask.setWeightStageMaterial(new BigDecimal("20.00"));
        badWeightTask.setWeightSummary(new BigDecimal("10.00")); // 合计 90%
        when(taskMapper.selectById(8002L)).thenReturn(badWeightTask);

        ScoreSubmitDTO dto = new ScoreSubmitDTO();
        dto.setTaskId(8002L);
        dto.setStudentId(5103L);
        dto.setEnterpriseScore(new BigDecimal("85.0"));
        dto.setProcessScore(new BigDecimal("85.0"));
        dto.setWeeklyScore(new BigDecimal("85.0"));
        dto.setMaterialScore(new BigDecimal("85.0"));
        dto.setSummaryScore(new BigDecimal("85.0"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                scoreService.submitScore(dto, teacherUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("必须严格等于 100.00%"));
    }

    @Test
    @DisplayName("4. 教师录入成绩草稿成功 - 正确加权汇算、状态设为 DRAFT，并记录 SCORE_DRAFT 审计历史")
    void testSubmitScore_DraftSuccess_CalculatesWeightedScoreAndRecordsAudit() {
        ScoreSubmitDTO dto = new ScoreSubmitDTO();
        dto.setTaskId(8001L);
        dto.setStudentId(5103L);
        dto.setEnterpriseScore(new BigDecimal("90.0"));
        dto.setProcessScore(new BigDecimal("85.0"));
        dto.setWeeklyScore(new BigDecimal("80.0"));
        dto.setMaterialScore(new BigDecimal("95.0"));
        dto.setSummaryScore(new BigDecimal("90.0"));
        dto.setSubmitToDept(false); // 保存草稿
        dto.setEvaluationComment("[SIM-SCORE-LOOP] 该生表现良好，各环节规范完成。");

        when(scoreMapper.selectOne(any())).thenReturn(null); // 新增

        Long id = scoreService.submitScore(dto, teacherUser);

        ArgumentCaptor<ScoreSummary> scoreCaptor = ArgumentCaptor.forClass(ScoreSummary.class);
        verify(scoreMapper).insert(scoreCaptor.capture());
        ScoreSummary saved = scoreCaptor.getValue();
        assertEquals("DRAFT", saved.getStatus());
        // 加权算分: 90*0.2 + 85*0.2 + 80*0.2 + 95*0.2 + 90*0.2 = 18 + 17 + 16 + 19 + 18 = 88.00
        assertEquals(new BigDecimal("88.00"), saved.getFinalScore());
        assertEquals("GOOD", saved.getScoreLevel());

        ArgumentCaptor<ScoreAuditHistory> histCaptor = ArgumentCaptor.forClass(ScoreAuditHistory.class);
        verify(historyMapper).insert(histCaptor.capture());
        ScoreAuditHistory hist = histCaptor.getValue();
        assertEquals("SCORE_DRAFT", hist.getAction());
        assertEquals(3102L, hist.getAuditUserId());
        assertTrue(hist.getAuditComment().contains("暂存五维成绩草稿"));
    }

    @Test
    @DisplayName("5. 学生查看成绩越权防护 - 学生只能查看自己的成绩 (403 IDOR 防御)")
    void testGetScoreDetail_StudentOther_Throws403() {
        ScoreSummary otherScore = new ScoreSummary();
        otherScore.setId(9001L);
        otherScore.setTaskId(8001L);
        otherScore.setStudentId(5101L); // 张优秀
        otherScore.setStatus("PUBLICITY");
        when(scoreMapper.selectById(9001L)).thenReturn(otherScore);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                scoreService.getScoreDetail(9001L, studentUser)); // 王申诉 (5103L) 查看张优秀 (5101L)
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权查看其他学生的成绩"));
    }

    @Test
    @DisplayName("6. 未发布成绩防泄漏 - 草稿状态学生直接访问详情抛出 403")
    void testGetScoreDetail_StudentUnpublished_Throws403() {
        ScoreSummary draftScore = new ScoreSummary();
        draftScore.setId(9002L);
        draftScore.setTaskId(8001L);
        draftScore.setStudentId(5103L);
        draftScore.setStatus("DRAFT");
        when(scoreMapper.selectById(9002L)).thenReturn(draftScore);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                scoreService.getScoreDetail(9002L, studentUser));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("学生端暂不可见"));
    }

    @Test
    @DisplayName("7. 学生获取本人成绩 - 草稿状态优雅返回 null，展示空态界面")
    void testGetStudentScore_StudentUnpublished_ReturnsNull() {
        ScoreSummary draftScore = new ScoreSummary();
        draftScore.setId(9002L);
        draftScore.setTaskId(8001L);
        draftScore.setStudentId(5103L);
        draftScore.setStatus("DRAFT");
        when(scoreMapper.selectOne(any())).thenReturn(draftScore);

        ScoreSummaryVO vo = scoreService.getStudentScore(8001L, 5103L, studentUser);
        assertNull(vo, "未公示成绩对学生端应返回 null 空态");
    }

    @Test
    @DisplayName("8. 成绩发布并记录发布人和时间 - 状态流转为 PUBLICITY 并写入审计日志")
    void testPublishScore_ByTeacher_Success() {
        ScoreSummary draftScore = new ScoreSummary();
        draftScore.setId(9003L);
        draftScore.setTaskId(8001L);
        draftScore.setStudentId(5103L);
        draftScore.setTeacherId(3102L);
        draftScore.setStatus("DRAFT");
        when(scoreMapper.selectById(9003L)).thenReturn(draftScore);

        scoreService.publishScore(9003L, teacherUser);

        ArgumentCaptor<ScoreSummary> captor = ArgumentCaptor.forClass(ScoreSummary.class);
        verify(scoreMapper).updateById(captor.capture());
        ScoreSummary published = captor.getValue();
        assertEquals("PUBLICITY", published.getStatus());
        assertNotNull(published.getPublicityStartTime());
        assertNotNull(published.getPublicityEndTime());
        assertEquals(3102L, published.getAuditedDeptUserId());

        ArgumentCaptor<ScoreAuditHistory> histCaptor = ArgumentCaptor.forClass(ScoreAuditHistory.class);
        verify(historyMapper).insert(histCaptor.capture());
        ScoreAuditHistory hist = histCaptor.getValue();
        assertEquals("SCORE_PUBLISH", hist.getAction());
        assertEquals("李导师", hist.getAuditUserName());
    }

    @Test
    @DisplayName("9. 成绩发布越权防护 - 非管辖教师禁止发布其他教师负责的成绩 (403)")
    void testPublishScore_TeacherNotAssigned_Throws403() {
        ScoreSummary otherScore = new ScoreSummary();
        otherScore.setId(9003L);
        otherScore.setTeacherId(3102L); // 李导师负责
        when(scoreMapper.selectById(9003L)).thenReturn(otherScore);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                scoreService.publishScore(9003L, otherTeacherUser)); // 王导师尝试发布
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权发布非负责管辖学生的成绩"));
    }

    @Test
    @DisplayName("10. 申诉原因必填与重复申诉阻断 - 校验未填写/字数不足/重复申诉抛出 400")
    void testSubmitAppeal_ValidationAndDuplicate_ThrowsException() {
        ScoreSummary publicityScore = new ScoreSummary();
        publicityScore.setId(9005L);
        publicityScore.setTaskId(8001L);
        publicityScore.setStudentId(5103L);
        publicityScore.setStatus("PUBLICITY");
        when(scoreMapper.selectById(9005L)).thenReturn(publicityScore);

        // 1. 空申诉理由
        ScoreAppealDTO emptyDto = new ScoreAppealDTO();
        emptyDto.setScoreId(9005L);
        emptyDto.setAppealReason("");
        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                scoreService.submitAppeal(emptyDto, studentUser));
        assertEquals(400, ex1.getCode());

        // 2. 字数不足 10 字
        ScoreAppealDTO shortDto = new ScoreAppealDTO();
        shortDto.setScoreId(9005L);
        shortDto.setAppealReason("不满意");
        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                scoreService.submitAppeal(shortDto, studentUser));
        assertEquals(400, ex2.getCode());
        assertTrue(ex2.getMessage().contains("字数不足"));

        // 3. 重复申诉阻断
        when(historyMapper.selectCount(any())).thenReturn(1L); // 已存在一次申诉
        ScoreAppealDTO normalDto = new ScoreAppealDTO();
        normalDto.setScoreId(9005L);
        normalDto.setAppealReason("[SIM-SCORE-LOOP] 周报均分核算存在误差，恳请重新核定。");
        BusinessException ex3 = assertThrows(BusinessException.class, () ->
                scoreService.submitAppeal(normalDto, studentUser));
        assertEquals(400, ex3.getCode());
        assertTrue(ex3.getMessage().contains("禁止重复提交"));
    }

    @Test
    @DisplayName("11. 非公示期禁止申诉 - 非法状态提交申诉严格阻断 (400)")
    void testSubmitAppeal_NotPublicity_Throws400() {
        ScoreSummary draftScore = new ScoreSummary();
        draftScore.setId(9006L);
        draftScore.setTaskId(8001L);
        draftScore.setStudentId(5103L);
        draftScore.setStatus("DRAFT");
        when(scoreMapper.selectById(9006L)).thenReturn(draftScore);

        ScoreAppealDTO dto = new ScoreAppealDTO();
        dto.setScoreId(9006L);
        dto.setAppealReason("[SIM-SCORE-LOOP] 申诉测试理由必须超过十个字符以上。");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                scoreService.submitAppeal(dto, studentUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("当前成绩不在公示期内"));
    }

    @Test
    @DisplayName("12. 申诉裁决越权防御 - 跨教师裁决申诉抛出 403")
    void testArbitrateAppeal_TeacherCrossArbitrate_Throws403() {
        ScoreAuditHistory appeal = new ScoreAuditHistory();
        appeal.setId(101L);
        appeal.setScoreId(9007L);
        appeal.setAction("APPEAL_APPLY");
        when(historyMapper.selectById(101L)).thenReturn(appeal);

        ScoreSummary score = new ScoreSummary();
        score.setId(9007L);
        score.setTeacherId(3102L); // 属于李导师
        score.setTaskId(8001L);
        when(scoreMapper.selectById(9007L)).thenReturn(score);

        ScoreArbitrateDTO dto = new ScoreArbitrateDTO();
        dto.setAction("PASS");
        dto.setAuditComment("测试意见");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                scoreService.arbitrateAppeal(101L, dto, otherTeacherUser)); // 王导师尝试裁决
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权处理非负责管辖学生的成绩申诉"));
    }

    @Test
    @DisplayName("13. 申诉仲裁通过调分 - 重算加权总分与等第，记录批文号并写入 APPEAL_PASS 审计快照")
    void testArbitrateAppeal_Pass_RecalculatesScoreAndRecordsAudit() {
        ScoreAuditHistory appeal = new ScoreAuditHistory();
        appeal.setId(102L);
        appeal.setScoreId(9008L);
        appeal.setAction("APPEAL_APPLY");
        appeal.setOldScoreSnapshot("{\"finalScore\":80.00}");
        when(historyMapper.selectById(102L)).thenReturn(appeal);

        ScoreSummary score = new ScoreSummary();
        score.setId(9008L);
        score.setTaskId(8001L);
        score.setStudentId(5103L);
        score.setTeacherId(3102L);
        score.setEnterpriseScore(new BigDecimal("80.0"));
        score.setProcessScore(new BigDecimal("80.0"));
        score.setWeeklyScore(new BigDecimal("80.0"));
        score.setMaterialScore(new BigDecimal("80.0"));
        score.setSummaryScore(new BigDecimal("80.0"));
        score.setFinalScore(new BigDecimal("80.00"));
        score.setScoreLevel("GOOD");
        score.setGradeRuleSnapshotJson("{\"rules\":{\"excellentMin\":90.00,\"goodMin\":80.00,\"mediumMin\":70.00,\"passMin\":60.00}}");
        when(scoreMapper.selectById(9008L)).thenReturn(score);
        when(historyMapper.selectList(any())).thenReturn(Collections.emptyList()); // 无后续裁决

        ScoreArbitrateDTO dto = new ScoreArbitrateDTO();
        dto.setAction("PASS");
        dto.setWeeklyScore(new BigDecimal("95.0")); // 周报调分至 95.0
        dto.setApprovalDocNo("[SIM-SCORE-LOOP] 计通教发[2026]10号批复");
        dto.setAuditComment("[SIM-SCORE-LOOP] 经教学督导组核对，周报得分确有漏算，更正为95分。");

        scoreService.arbitrateAppeal(102L, dto, teacherUser);

        ArgumentCaptor<ScoreSummary> scoreCaptor = ArgumentCaptor.forClass(ScoreSummary.class);
        verify(scoreMapper).updateById(scoreCaptor.capture());
        ScoreSummary updated = scoreCaptor.getValue();
        assertEquals(new BigDecimal("95.0"), updated.getWeeklyScore());
        // 加权算分: 80*0.2 + 80*0.2 + 95*0.2 + 80*0.2 + 80*0.2 = 16 + 16 + 19 + 16 + 16 = 83.00
        assertEquals(new BigDecimal("83.00"), updated.getFinalScore());
        assertEquals("GOOD", updated.getScoreLevel());

        ArgumentCaptor<ScoreAuditHistory> histCaptor = ArgumentCaptor.forClass(ScoreAuditHistory.class);
        verify(historyMapper).insert(histCaptor.capture());
        ScoreAuditHistory hist = histCaptor.getValue();
        assertEquals("APPEAL_PASS", hist.getAction());
        assertEquals("[SIM-SCORE-LOOP] 计通教发[2026]10号批复", hist.getApprovalDocNo());
        assertEquals("李导师", hist.getAuditUserName());
    }

    @Test
    @DisplayName("14. 申诉仲裁驳回 - 维持原分数，记录 APPEAL_REJECT 审计快照")
    void testArbitrateAppeal_Reject_MaintainsScoreAndRecordsAudit() {
        ScoreAuditHistory appeal = new ScoreAuditHistory();
        appeal.setId(103L);
        appeal.setScoreId(9009L);
        appeal.setAction("APPEAL_APPLY");
        appeal.setOldScoreSnapshot("{\"finalScore\":80.00}");
        when(historyMapper.selectById(103L)).thenReturn(appeal);

        ScoreSummary score = new ScoreSummary();
        score.setId(9009L);
        score.setTaskId(8001L);
        score.setStudentId(5103L);
        score.setTeacherId(3102L);
        score.setFinalScore(new BigDecimal("80.00"));
        score.setScoreLevel("GOOD");
        when(scoreMapper.selectById(9009L)).thenReturn(score);
        when(historyMapper.selectList(any())).thenReturn(Collections.emptyList());

        ScoreArbitrateDTO dto = new ScoreArbitrateDTO();
        dto.setAction("REJECT");
        dto.setAuditComment("[SIM-SCORE-LOOP] 经导师组严格复查，原评分核算准确无误，维持原评定成绩。");

        scoreService.arbitrateAppeal(103L, dto, teacherUser);

        // 驳回不更新 scoreMapper 主记录分数
        verify(scoreMapper, never()).updateById(any());

        ArgumentCaptor<ScoreAuditHistory> histCaptor = ArgumentCaptor.forClass(ScoreAuditHistory.class);
        verify(historyMapper).insert(histCaptor.capture());
        ScoreAuditHistory hist = histCaptor.getValue();
        assertEquals("APPEAL_REJECT", hist.getAction());
        assertEquals("李导师", hist.getAuditUserName());
        assertTrue(hist.getAuditComment().contains("维持原评定成绩"));
    }
}
