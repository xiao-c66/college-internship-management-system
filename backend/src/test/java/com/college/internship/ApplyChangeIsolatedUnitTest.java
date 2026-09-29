package com.college.internship;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.dto.ApplyChangeAuditDTO;
import com.college.internship.dto.ApplyChangeDTO;
import com.college.internship.entity.ApplyAuditHistory;
import com.college.internship.entity.InternshipApply;
import com.college.internship.entity.InternshipApplyChange;
import com.college.internship.entity.InternshipApplyChangeHistory;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.ApplyAuditHistoryMapper;
import com.college.internship.mapper.InternshipApplyChangeHistoryMapper;
import com.college.internship.mapper.InternshipApplyChangeMapper;
import com.college.internship.mapper.InternshipApplyMapper;
import com.college.internship.mapper.InternshipTaskStudentMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.impl.InternshipApplyChangeServiceImpl;
import com.college.internship.vo.ApplyChangeVO;
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

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 实习重大信息变更申请与双级审批纯内存隔离单元测试 (零连接数据库)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplyChangeIsolatedUnitTest {

    @Mock
    private InternshipApplyMapper applyMapper;
    @Mock
    private InternshipApplyChangeMapper changeMapper;
    @Mock
    private InternshipApplyChangeHistoryMapper changeHistoryMapper;
    @Mock
    private ApplyAuditHistoryMapper applyAuditHistoryMapper;
    @Mock
    private InternshipTaskStudentMapper taskStudentMapper;
    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private InternshipApplyChangeServiceImpl changeService;

    private LoginUser studentUser;
    private LoginUser teacherUser;
    private LoginUser deptAdminUser;
    private LoginUser otherTeacherUser;
    private LoginUser otherDeptAdminUser;

    private InternshipApply lockedApprovedApply;
    private InternshipApplyChange pendingTeacherChange;
    private InternshipApplyChange pendingDeptChange;

    @BeforeEach
    void setUp() {
        studentUser = LoginUser.builder()
                .userId(101L)
                .username("test_student")
                .realName("测试学生")
                .userType("STUDENT")
                .deptId(1L)
                .permissions(List.of("ROLE_STUDENT"))
                .build();

        teacherUser = LoginUser.builder()
                .userId(201L)
                .username("test_teacher")
                .realName("张指导教师")
                .userType("TEACHER")
                .deptId(1L)
                .permissions(List.of("ROLE_TEACHER"))
                .build();

        otherTeacherUser = LoginUser.builder()
                .userId(202L)
                .username("other_teacher")
                .realName("李其他教师")
                .userType("TEACHER")
                .deptId(1L)
                .permissions(List.of("ROLE_TEACHER"))
                .build();

        deptAdminUser = LoginUser.builder()
                .userId(301L)
                .username("test_deptadmin")
                .realName("计算机系主任")
                .userType("DEPT_ADMIN")
                .deptId(1L)
                .permissions(List.of("ROLE_DEPT_ADMIN"))
                .build();

        otherDeptAdminUser = LoginUser.builder()
                .userId(302L)
                .username("other_deptadmin")
                .realName("外国语系主任")
                .userType("DEPT_ADMIN")
                .deptId(2L)
                .permissions(List.of("ROLE_DEPT_ADMIN"))
                .build();

        lockedApprovedApply = InternshipApply.builder()
                .id(1001L)
                .taskId(501L)
                .studentId(101L)
                .studentNumber("STU101")
                .studentName("测试学生")
                .deptId(1L)
                .companyName("原科技有限责任公司")
                .jobPosition("测试专员")
                .jobAddress("北京市海淀区中关村南大街1号")
                .companyContactPerson("李主管")
                .companyContactPhone("13800000001")
                .companyContactEmail("li@orig.com")
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 9, 30))
                .internshipMode("DISTRIBUTED")
                .jobDuties("原工作职责陈述")
                .agreementFileUrl("/files/orig_agreement.pdf")
                .applyStatus("APPROVED")
                .isLocked(1)
                .isDeleted(0)
                .build();

        pendingTeacherChange = InternshipApplyChange.builder()
                .id(8001L)
                .applyId(1001L)
                .taskId(501L)
                .studentId(101L)
                .studentNumber("STU101")
                .studentName("测试学生")
                .deptId(1L)
                .teacherId(201L)
                .teacherName("张指导教师")
                .origCompanyName("原科技有限责任公司")
                .newCompanyName("新智能科技有限公司")
                .newJobPosition("全栈开发工程师")
                .newJobAddress("深圳市南山区高新南道")
                .newContactPerson("王总监")
                .newContactPhone("13900000002")
                .newStartDate(LocalDate.of(2026, 7, 10))
                .newEndDate(LocalDate.of(2026, 10, 15))
                .newInternshipMode("DISTRIBUTED")
                .changeReason("原实习单位因业务架构重组不再接收实习生，转投专业对口的新单位实习")
                .changeStatus("PENDING_TEACHER")
                .currentStep("TEACHER_INITIAL")
                .isDeleted(0)
                .build();

        pendingDeptChange = InternshipApplyChange.builder()
                .id(8002L)
                .applyId(1001L)
                .taskId(501L)
                .studentId(101L)
                .studentNumber("STU101")
                .studentName("测试学生")
                .deptId(1L)
                .teacherId(201L)
                .teacherName("张指导教师")
                .origCompanyName("原科技有限责任公司")
                .newCompanyName("新智能科技有限公司")
                .newJobPosition("全栈开发工程师")
                .newJobAddress("深圳市南山区高新南道")
                .newContactPerson("王总监")
                .newContactPhone("13900000002")
                .newStartDate(LocalDate.of(2026, 7, 10))
                .newEndDate(LocalDate.of(2026, 10, 15))
                .newInternshipMode("DISTRIBUTED")
                .changeReason("原实习单位因业务架构重组不再接收实习生，转投专业对口的新单位实习")
                .changeStatus("PENDING_DEPT")
                .currentStep("DEPT_FINAL")
                .isDeleted(0)
                .build();
    }

    @Test
    @DisplayName("测试1：非 APPROVED 或未锁定状态原申请禁止发起重大变更申请")
    void testSubmitChange_NotApprovedOrNotLocked_Throws400() {
        lockedApprovedApply.setApplyStatus("SUBMITTED");
        lockedApprovedApply.setIsLocked(0);
        when(applyMapper.selectById(1001L)).thenReturn(lockedApprovedApply);

        ApplyChangeDTO dto = ApplyChangeDTO.builder()
                .applyId(1001L)
                .newCompanyName("新智能科技有限公司")
                .newJobPosition("Java后端开发")
                .newJobAddress("深圳市南山区高新道")
                .newContactPerson("王总监")
                .newContactPhone("13900000002")
                .newStartDate(LocalDate.of(2026, 7, 10))
                .newEndDate(LocalDate.of(2026, 10, 15))
                .newInternshipMode("DISTRIBUTED")
                .changeReason("原单位无法提供足够项目支撑，申请变更")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> changeService.submitChange(dto, studentUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("原实习申报尚未终审通过并锁定"));
    }

    @Test
    @DisplayName("测试2：存在待处理变更申请时禁止并发重复提交")
    void testSubmitChange_DuplicatePending_Throws400() {
        when(applyMapper.selectById(1001L)).thenReturn(lockedApprovedApply);
        when(changeMapper.selectCount(any())).thenReturn(1L);

        ApplyChangeDTO dto = ApplyChangeDTO.builder()
                .applyId(1001L)
                .newCompanyName("新智能科技有限公司")
                .newJobPosition("Java后端开发")
                .newJobAddress("深圳市南山区高新道")
                .newContactPerson("王总监")
                .newContactPhone("13900000002")
                .newStartDate(LocalDate.of(2026, 7, 10))
                .newEndDate(LocalDate.of(2026, 10, 15))
                .newInternshipMode("DISTRIBUTED")
                .changeReason("原单位无法提供足够项目支撑，申请变更")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> changeService.submitChange(dto, studentUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("已有正在审批中的重大变更申请"));
    }

    @Test
    @DisplayName("测试3：正常提交变更，正确服务端快照原信息并初始化为待教师初审")
    void testSubmitChange_Success_SnapshotOriginalAndPendingTeacher() {
        when(applyMapper.selectById(1001L)).thenReturn(lockedApprovedApply);
        when(changeMapper.selectCount(any())).thenReturn(0L);
        when(taskStudentMapper.selectOne(any())).thenReturn(InternshipTaskStudent.builder()
                .taskId(501L)
                .studentId(101L)
                .teacherId(201L)
                .build());
        when(userMapper.selectById(201L)).thenReturn(SysUser.builder().id(201L).realName("张指导教师").build());

        ApplyChangeDTO dto = ApplyChangeDTO.builder()
                .applyId(1001L)
                .newCompanyName("新智能科技有限公司")
                .newJobPosition("Java后端开发")
                .newJobAddress("深圳市南山区高新道")
                .newContactPerson("王总监")
                .newContactPhone("13900000002")
                .newStartDate(LocalDate.of(2026, 7, 10))
                .newEndDate(LocalDate.of(2026, 10, 15))
                .newInternshipMode("DISTRIBUTED")
                .changeReason("原单位无法提供足够项目支撑，申请变更")
                .build();

        ApplyChangeVO vo = changeService.submitChange(dto, studentUser);
        assertNotNull(vo);
        assertEquals("PENDING_TEACHER", vo.getChangeStatus());
        assertEquals("TEACHER_INITIAL", vo.getCurrentStep());
        assertEquals("原科技有限责任公司", vo.getOrigCompanyName());
        assertEquals("新智能科技有限公司", vo.getNewCompanyName());
        assertEquals(201L, vo.getTeacherId());

        verify(changeMapper, times(1)).insert(any(InternshipApplyChange.class));
        verify(changeHistoryMapper, times(1)).insert(any(InternshipApplyChangeHistory.class));
    }

    @Test
    @DisplayName("测试4：非指导教师初审返回 403 权限拒绝")
    void testTeacherInitialAudit_OtherTeacher_Throws403() {
        when(changeMapper.selectById(8001L)).thenReturn(pendingTeacherChange);

        ApplyChangeAuditDTO dto = ApplyChangeAuditDTO.builder()
                .auditAction("APPROVE")
                .auditOpinion("同意变更")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                changeService.teacherInitialAudit(8001L, dto, otherTeacherUser));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权审核非本人负责学生"));
    }

    @Test
    @DisplayName("测试5：指导教师初审驳回，状态流转为 REJECTED，原主申请绝对不被修改")
    void testTeacherInitialAudit_Reject_StatusRejected_OriginalUntouched() {
        when(changeMapper.selectById(8001L)).thenReturn(pendingTeacherChange);

        ApplyChangeAuditDTO dto = ApplyChangeAuditDTO.builder()
                .auditAction("REJECT")
                .auditOpinion("新岗位与本专业培养方案脱节过大，请重新联系对口单位")
                .build();

        ApplyChangeVO vo = changeService.teacherInitialAudit(8001L, dto, teacherUser);
        assertNotNull(vo);
        assertEquals("REJECTED", vo.getChangeStatus());
        assertEquals("FINISHED", vo.getCurrentStep());

        // 原申请绝不触发更新
        verify(applyMapper, never()).updateById(any());
        // 轨迹表留痕
        verify(changeHistoryMapper, times(1)).insert(any(InternshipApplyChangeHistory.class));
    }

    @Test
    @DisplayName("测试6：指导教师初审通过，流转为 PENDING_DEPT 待院系终审")
    void testTeacherInitialAudit_Approve_StatusPendingDept() {
        when(changeMapper.selectById(8001L)).thenReturn(pendingTeacherChange);

        ApplyChangeAuditDTO dto = ApplyChangeAuditDTO.builder()
                .auditAction("APPROVE")
                .auditOpinion("情况属实且新单位对口，同意流转至院系审批")
                .build();

        ApplyChangeVO vo = changeService.teacherInitialAudit(8001L, dto, teacherUser);
        assertNotNull(vo);
        assertEquals("PENDING_DEPT", vo.getChangeStatus());
        assertEquals("DEPT_FINAL", vo.getCurrentStep());
        verify(applyMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("测试7：未通过教师初审时，院系管理员越级终审直接抛出 400 阻断")
    void testDeptFinalAudit_SkipTeacherAudit_Throws400() {
        when(changeMapper.selectById(8001L)).thenReturn(pendingTeacherChange); // Still PENDING_TEACHER

        ApplyChangeAuditDTO dto = ApplyChangeAuditDTO.builder()
                .auditAction("APPROVE")
                .auditOpinion("院系同意")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                changeService.deptFinalAudit(8001L, dto, deptAdminUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("尚未通过指导教师初审，禁止越级终审"));
    }

    @Test
    @DisplayName("测试8：跨院系管理员终审抛出 403 权限拒绝")
    void testDeptFinalAudit_CrossDept_Throws403() {
        when(changeMapper.selectById(8002L)).thenReturn(pendingDeptChange);

        ApplyChangeAuditDTO dto = ApplyChangeAuditDTO.builder()
                .auditAction("APPROVE")
                .auditOpinion("院系同意")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                changeService.deptFinalAudit(8002L, dto, otherDeptAdminUser));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权审核跨院系学生"));
    }

    @Test
    @DisplayName("测试9：院系终审批准，原子同步更新原主申请表并维持锁定，留存双重审计轨迹")
    void testDeptFinalAudit_Approve_AtomicSyncOriginalApply() {
        when(changeMapper.selectById(8002L)).thenReturn(pendingDeptChange);
        when(applyMapper.selectById(1001L)).thenReturn(lockedApprovedApply);

        ApplyChangeAuditDTO dto = ApplyChangeAuditDTO.builder()
                .auditAction("APPROVE")
                .auditOpinion("经学院教学督导会审，批准重大信息变更生效")
                .build();

        ApplyChangeVO vo = changeService.deptFinalAudit(8002L, dto, deptAdminUser);
        assertNotNull(vo);
        assertEquals("APPROVED", vo.getChangeStatus());
        assertEquals("FINISHED", vo.getCurrentStep());

        // 验证主表被原子同步更新
        ArgumentCaptor<InternshipApply> applyCaptor = ArgumentCaptor.forClass(InternshipApply.class);
        verify(applyMapper, times(1)).updateById(applyCaptor.capture());
        InternshipApply updatedApply = applyCaptor.getValue();
        assertEquals("新智能科技有限公司", updatedApply.getCompanyName());
        assertEquals("全栈开发工程师", updatedApply.getJobPosition());
        assertEquals("深圳市南山区高新南道", updatedApply.getJobAddress());
        assertEquals("王总监", updatedApply.getCompanyContactPerson());
        assertEquals("13900000002", updatedApply.getCompanyContactPhone());
        assertEquals("APPROVED", updatedApply.getApplyStatus());
        assertEquals(1, updatedApply.getIsLocked()); // 必须维持锁定！

        // 验证变更历史轨迹与主表历史轨迹双重入库
        verify(changeHistoryMapper, times(1)).insert(any(InternshipApplyChangeHistory.class));
        verify(applyAuditHistoryMapper, times(1)).insert(any(ApplyAuditHistory.class));
    }
}
