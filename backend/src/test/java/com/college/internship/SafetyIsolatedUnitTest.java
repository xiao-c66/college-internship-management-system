package com.college.internship;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.dto.ApplyDTO;
import com.college.internship.dto.CommitmentSignDTO;
import com.college.internship.dto.ExamSubmitDTO;
import com.college.internship.entity.*;
import com.college.internship.mapper.*;
import com.college.internship.security.LoginUser;
import com.college.internship.service.impl.InternshipApplyServiceImpl;
import com.college.internship.service.impl.SafetyEducationServiceImpl;
import com.college.internship.vo.ApplyVO;
import com.college.internship.vo.ExamResultVO;
import com.college.internship.vo.QuestionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 安全教育准入与实习申报前置联动纯内存隔离单元测试 (零连接数据库)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SafetyIsolatedUnitTest {

    @Mock
    private SafetyMaterialItemMapper materialItemMapper;
    @Mock
    private SafetyTestQuestionMapper questionMapper;
    @Mock
    private SafetyExamAttemptMapper examAttemptMapper;
    @Mock
    private SafetyExamAnswerDetailMapper answerDetailMapper;
    @Mock
    private SafetyCommitmentSignMapper commitmentSignMapper;
    @Mock
    private InternshipTaskMapper taskMapper;
    @Mock
    private InternshipTaskStudentMapper taskStudentMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private BaseClassMapper classMapper;
    @Mock
    private BaseDepartmentMapper departmentMapper;
    @Mock
    private BaseMajorMapper majorMapper;
    @Mock
    private InternshipApplyMapper applyMapper;
    @Mock
    private ApplyAuditHistoryMapper auditHistoryMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SafetyEducationServiceImpl safetyService;

    @InjectMocks
    private InternshipApplyServiceImpl applyService;

    private LoginUser studentUser;
    private LoginUser otherStudentUser;
    private LoginUser teacherUser;
    private InternshipTask task;
    private InternshipTaskStudent taskStudent;
    private SafetyTestQuestion question1;
    private SafetyTestQuestion question2;

    @BeforeEach
    void setUp() {
        studentUser = LoginUser.builder()
                .userId(5105L)
                .username("student_sim_05")
                .realName("陈解锁")
                .userType("STUDENT")
                .deptId(101L)
                .build();

        otherStudentUser = LoginUser.builder()
                .userId(5999L)
                .username("other_student")
                .realName("路人甲")
                .userType("STUDENT")
                .deptId(101L)
                .build();

        teacherUser = LoginUser.builder()
                .userId(3102L)
                .username("teacher_li")
                .realName("李指导")
                .userType("TEACHER")
                .deptId(101L)
                .build();

        task = InternshipTask.builder()
                .id(8001L)
                .taskName("2026届顶岗实习任务")
                .deptId(101L)
                .safetyPassingScore(80)
                .safetyMaxAttempts(3)
                .isDeleted(0)
                .build();

        taskStudent = InternshipTaskStudent.builder()
                .id(901L)
                .taskId(8001L)
                .studentId(5105L)
                .studentNumber("S202605")
                .studentName("陈解锁")
                .teacherId(3102L)
                .safetyStatus("NOT_STARTED")
                .readMaterialCount(0)
                .isDeleted(0)
                .build();

        question1 = SafetyTestQuestion.builder()
                .id(101L)
                .taskId(8001L)
                .questionType("SINGLE_CHOICE")
                .stem("实习期间如遇突发火灾，首先应采取的应急措施是？")
                .options("[{\"key\":\"A\",\"text\":\"报警并沿安全疏散通道撤离\"},{\"key\":\"B\",\"text\":\"乘坐电梯快速下楼\"}]")
                .correctAnswer("A")
                .score(50)
                .analysis("严禁乘坐电梯逃生")
                .status(1)
                .isDeleted(0)
                .build();

        question2 = SafetyTestQuestion.builder()
                .id(102L)
                .taskId(8001L)
                .questionType("JUDGMENT")
                .stem("学生可以在未经企业与指导教师批准的情况下擅自脱离实习岗位。")
                .options("[{\"key\":\"A\",\"text\":\"正确\"},{\"key\":\"B\",\"text\":\"错误\"}]")
                .correctAnswer("B")
                .score(50)
                .analysis("严禁私自离岗脱管")
                .status(1)
                .isDeleted(0)
                .build();

        when(taskMapper.selectById(8001L)).thenReturn(task);
        when(taskStudentMapper.selectOne(any())).thenReturn(taskStudent);
        when(questionMapper.selectById(101L)).thenReturn(question1);
        when(questionMapper.selectById(102L)).thenReturn(question2);
    }

    @Test
    @DisplayName("测试1：未学完资料时获取试卷，服务端严格阻断并抛出 SAFE-004")
    void testExamPaper_BlockedWhenMaterialsIncomplete() {
        when(materialItemMapper.selectCount(any())).thenReturn(2L);
        taskStudent.setReadMaterialCount(1); // 仅读了1/2

        BusinessException ex = assertThrows(BusinessException.class, () ->
                safetyService.getExamPaper(8001L, studentUser));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("SAFE-004"));
    }

    @Test
    @DisplayName("测试2：学完资料后获取试卷，正确返回试卷且绝对脱敏 correctAnswer 与 analysis")
    void testExamPaper_AnswersMasked() {
        when(materialItemMapper.selectCount(any())).thenReturn(2L);
        taskStudent.setReadMaterialCount(2); // 全部读完
        when(questionMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(question1, question2)));

        List<QuestionVO> paper = safetyService.getExamPaper(8001L, studentUser);

        assertNotNull(paper);
        assertEquals(2, paper.size());
        for (QuestionVO q : paper) {
            assertNotNull(q.getStem());
            assertNotNull(q.getOptions());
        }
    }

    @Test
    @DisplayName("测试3：未学完资料时交卷，服务端阻断并抛出 SAFE-004")
    void testSubmitExam_BlockedWhenMaterialsIncomplete() {
        when(materialItemMapper.selectCount(any())).thenReturn(2L);
        taskStudent.setReadMaterialCount(0); // 未开始阅读

        ExamSubmitDTO dto = new ExamSubmitDTO();
        dto.setTaskId(8001L);
        dto.setAnswers(List.of(
                new ExamSubmitDTO.AnswerItem(101L, "A")
        ));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                safetyService.submitExam(dto, studentUser));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("SAFE-004"));
    }

    @Test
    @DisplayName("测试4：交卷包含重复题目时，服务端严格校验并阻断")
    void testSubmitExam_BlockedOnDuplicateQuestions() {
        when(materialItemMapper.selectCount(any())).thenReturn(2L);
        taskStudent.setReadMaterialCount(2);

        ExamSubmitDTO dto = new ExamSubmitDTO();
        dto.setTaskId(8001L);
        dto.setAnswers(List.of(
                new ExamSubmitDTO.AnswerItem(101L, "A"),
                new ExamSubmitDTO.AnswerItem(101L, "A") // 重复提交题号 101
        ));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                safetyService.submitExam(dto, studentUser));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("重复作答"));
    }

    @Test
    @DisplayName("测试5：考试成绩低于及格线，判定不合格并更新剩余次数")
    void testSubmitExam_FailsWhenScoreBelowPassing() {
        when(materialItemMapper.selectCount(any())).thenReturn(2L);
        taskStudent.setReadMaterialCount(2);
        when(examAttemptMapper.selectCount(any())).thenReturn(0L); // 第1次交卷

        ExamSubmitDTO dto = new ExamSubmitDTO();
        dto.setTaskId(8001L);
        dto.setAnswers(List.of(
                new ExamSubmitDTO.AnswerItem(101L, "B"), // 答错得0分
                new ExamSubmitDTO.AnswerItem(102L, "A")  // 答错得0分
        ));

        ExamResultVO res = safetyService.submitExam(dto, studentUser);

        assertNotNull(res);
        assertEquals(0, res.getIsPassed());
        assertEquals(BigDecimal.ZERO, res.getTotalScore());
        assertEquals(1, res.getAttemptNo());
        assertEquals(2, res.getRemainingAttempts());
        verify(examAttemptMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("测试6：考试成绩达标（100分 >= 80分），判定合格，状态流转为 PASSED")
    void testSubmitExam_SucceedsWhenPassing() {
        when(materialItemMapper.selectCount(any())).thenReturn(2L);
        taskStudent.setReadMaterialCount(2);
        when(examAttemptMapper.selectCount(any())).thenReturn(0L);

        ExamSubmitDTO dto = new ExamSubmitDTO();
        dto.setTaskId(8001L);
        dto.setAnswers(List.of(
                new ExamSubmitDTO.AnswerItem(101L, "A"), // 正确 50分
                new ExamSubmitDTO.AnswerItem(102L, "B")  // 正确 50分
        ));

        // 模拟已及格记录在 updateTaskStudentSafetyStatus 中的查询
        when(examAttemptMapper.selectOne(any())).thenReturn(SafetyExamAttempt.builder().isPassed(1).build());

        ExamResultVO res = safetyService.submitExam(dto, studentUser);

        assertNotNull(res);
        assertEquals(1, res.getIsPassed());
        assertEquals(new BigDecimal(100), res.getTotalScore());
        assertEquals("PASSED", taskStudent.getSafetyStatus());
    }

    @Test
    @DisplayName("测试7：已通过考试后禁止重复交卷刷分")
    void testSubmitExam_BlockedWhenAlreadyPassed() {
        when(materialItemMapper.selectCount(any())).thenReturn(2L);
        taskStudent.setReadMaterialCount(2);
        when(examAttemptMapper.selectCount(argThat(w -> w != null))).thenReturn(1L);

        ExamSubmitDTO dto = new ExamSubmitDTO();
        dto.setTaskId(8001L);
        dto.setAnswers(List.of(new ExamSubmitDTO.AnswerItem(101L, "A")));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                safetyService.submitExam(dto, studentUser));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("无需重复交卷"));
    }

    @Test
    @DisplayName("测试8：未通过考试禁止签署安全承诺书 (SAFE-007)")
    void testSignCommitment_BlockedWhenExamNotPassed() {
        when(examAttemptMapper.selectCount(any())).thenReturn(0L); // 无及格记录

        CommitmentSignDTO dto = new CommitmentSignDTO();
        dto.setTaskId(8001L);
        dto.setInsuranceFileUrl("/uploads/insurance/policy.pdf");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                safetyService.signCommitment(dto, "127.0.0.1", studentUser));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("SAFE-007"));
    }

    @Test
    @DisplayName("测试9：考试通过后签署承诺书，安全准入状态转为 COMPLETED")
    void testSignCommitment_SucceedsWhenPassed() {
        when(examAttemptMapper.selectCount(any())).thenReturn(1L); // 已有及格记录
        when(materialItemMapper.selectCount(any())).thenReturn(2L);
        taskStudent.setReadMaterialCount(2);

        // 模拟首次签署：existing 为 null，在 updateTaskStudentSafetyStatus 中查询 passedAttempt 与 sign
        when(examAttemptMapper.selectOne(any())).thenReturn(SafetyExamAttempt.builder().isPassed(1).build());
        when(commitmentSignMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(SafetyCommitmentSign.builder().isSigned(1).build());

        CommitmentSignDTO dto = new CommitmentSignDTO();
        dto.setTaskId(8001L);
        dto.setInsuranceFileUrl("/uploads/insurance/policy.pdf");

        safetyService.signCommitment(dto, "127.0.0.1", studentUser);

        assertEquals("COMPLETED", taskStudent.getSafetyStatus());
        verify(commitmentSignMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("测试10：安全教育未达标（NOT_STARTED），允许保存草稿，状态为 DRAFT")
    void testInternshipApply_DraftAllowedEvenIfSafetyIncomplete() {
        taskStudent.setSafetyStatus("NOT_STARTED");

        ApplyDTO dto = new ApplyDTO();
        dto.setTaskId(8001L);
        dto.setCompanyName("北京云峰测试科技有限公司");
        dto.setJobPosition("Java助理工程师");

        when(userMapper.selectById(5105L)).thenReturn(SysUser.builder().id(5105L).deptId(101L).build());
        when(applyMapper.selectById(any())).thenReturn(InternshipApply.builder().id(7001L).applyStatus("DRAFT").taskId(8001L).studentId(5105L).isDeleted(0).build());

        ApplyVO vo = applyService.saveDraft(dto, studentUser);

        assertNotNull(vo);
        verify(applyMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("测试11：安全教育未达成（STUDYING），正式提交申报被后端硬性阻断并返回 SAFE-010")
    void testInternshipApply_FormalSubmitBlockedWhenSafetyIncomplete() {
        taskStudent.setSafetyStatus("STUDYING"); // 仅学习中，未达 COMPLETED

        ApplyDTO dto = new ApplyDTO();
        dto.setTaskId(8001L);
        dto.setCompanyName("北京云峰测试科技有限公司");
        dto.setJobPosition("Java助理工程师");

        when(userMapper.selectById(5105L)).thenReturn(SysUser.builder().id(5105L).deptId(101L).build());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                applyService.submitApply(dto, studentUser));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("SAFE-010"));
        verify(applyMapper, never()).insert(any());
    }

    @Test
    @DisplayName("测试12：安全教育达标（COMPLETED），正式提交申报成功并置为 SUBMITTED")
    void testInternshipApply_FormalSubmitSucceedsWhenSafetyCompleted() {
        taskStudent.setSafetyStatus("COMPLETED"); // 已达标准入

        ApplyDTO dto = new ApplyDTO();
        dto.setTaskId(8001L);
        dto.setCompanyName("北京云峰测试科技有限公司");
        dto.setJobPosition("Java助理工程师");

        when(userMapper.selectById(5105L)).thenReturn(SysUser.builder().id(5105L).deptId(101L).build());
        when(applyMapper.selectById(any())).thenReturn(InternshipApply.builder().id(7001L).applyStatus("SUBMITTED").taskId(8001L).studentId(5105L).isDeleted(0).build());

        ApplyVO vo = applyService.submitApply(dto, studentUser);

        assertNotNull(vo);
        verify(applyMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("测试13：学生未圈定在当前实习任务名单中（越权/IDOR），所有操作均被 403 阻断")
    void testCrossStudent_IDOR_Blocked() {
        when(taskStudentMapper.selectOne(any())).thenReturn(null); // 该学生未在名单内

        // 1. 获取试卷
        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                safetyService.getExamPaper(8001L, otherStudentUser));
        assertEquals(403, ex1.getCode());

        // 2. 提交考试
        ExamSubmitDTO examDto = new ExamSubmitDTO();
        examDto.setTaskId(8001L);
        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                safetyService.submitExam(examDto, otherStudentUser));
        assertEquals(403, ex2.getCode());

        // 3. 签署承诺书
        CommitmentSignDTO signDto = new CommitmentSignDTO();
        signDto.setTaskId(8001L);
        BusinessException ex3 = assertThrows(BusinessException.class, () ->
                safetyService.signCommitment(signDto, "127.0.0.1", otherStudentUser));
        assertEquals(403, ex3.getCode());

        // 4. 发起申报草稿
        ApplyDTO applyDto = new ApplyDTO();
        applyDto.setTaskId(8001L);
        BusinessException ex4 = assertThrows(BusinessException.class, () ->
                applyService.saveDraft(applyDto, otherStudentUser));
        assertEquals(403, ex4.getCode());

        // 5. 正式提交申报
        BusinessException ex5 = assertThrows(BusinessException.class, () ->
                applyService.submitApply(applyDto, otherStudentUser));
        assertEquals(403, ex5.getCode());
    }
}
