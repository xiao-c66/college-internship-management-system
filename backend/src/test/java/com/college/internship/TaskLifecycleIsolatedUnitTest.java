package com.college.internship;

import com.college.internship.common.BusinessException;
import com.college.internship.dto.LifecycleDTO;
import com.college.internship.dto.StudentAdjustDTO;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.entity.SysUser;
import com.college.internship.entity.TaskLifecycleLog;
import com.college.internship.entity.TaskStudentAuditLog;
import com.college.internship.mapper.*;
import com.college.internship.security.LoginUser;
import com.college.internship.service.impl.InternshipTaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TaskLifecycleIsolatedUnitTest {

    @Mock private InternshipTaskMapper internshipTaskMapper;
    @Mock private TaskLifecycleLogMapper lifecycleLogMapper;
    @Mock private TaskStudentAuditLogMapper studentAuditLogMapper;
    @Mock private InternshipApplyMapper applyMapper;
    @Mock private InternshipWeeklyReportMapper weeklyMapper;
    @Mock private InternshipTaskMajorMapper taskMajorMapper;
    @Mock private InternshipTaskClassMapper taskClassMapper;
    @Mock private InternshipTaskStudentMapper taskStudentMapper;
    @Mock private BaseDepartmentMapper departmentMapper;
    @Mock private BaseMajorMapper majorMapper;
    @Mock private BaseClassMapper classMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysOperationLogMapper operationLogMapper;

    @InjectMocks
    private InternshipTaskServiceImpl taskService;

    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        loginUser = new LoginUser();
        loginUser.setUserId(1L);
        loginUser.setRealName("Test Admin");
        loginUser.setUserType("SYS_ADMIN");
        loginUser.setDeptId(100L);
    }


    @Test
    void testDeleteDraftWithNoData_Success() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setStatus("DRAFT");
        task.setIsDeleted(0);

        when(internshipTaskMapper.selectById(1L)).thenReturn(task);
        when(applyMapper.selectCount(any())).thenReturn(0L);
        when(weeklyMapper.selectCount(any())).thenReturn(0L);

        assertDoesNotThrow(() -> taskService.deleteTask(1L, loginUser));

        verify(taskStudentMapper).delete(any());
        verify(internshipTaskMapper).deleteById(1L);
    }


    @Test
    void testDeleteDraftWithApplyData_ThrowsException() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setStatus("DRAFT");
        task.setIsDeleted(0);

        when(internshipTaskMapper.selectById(1L)).thenReturn(task);
        when(applyMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> taskService.deleteTask(1L, loginUser));
        assertTrue(ex.getMessage().contains("产生申请或周报业务数据"));
        assertEquals(400, ex.getCode());
    }


    @Test
    void testDeletePublishedTask_ThrowsException() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setStatus("PUBLISHED");
        task.setIsDeleted(0);

        when(internshipTaskMapper.selectById(1L)).thenReturn(task);

        BusinessException ex = assertThrows(BusinessException.class, () -> taskService.deleteTask(1L, loginUser));
        assertTrue(ex.getMessage().contains("仅允许删除未正式发布的草稿任务"));
        assertEquals(400, ex.getCode());
    }


    @Test
    void testChangeLifecycleRevokeOnDraft_ThrowsException() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setStatus("DRAFT");
        task.setIsDeleted(0);

        when(internshipTaskMapper.selectById(1L)).thenReturn(task);

        LifecycleDTO dto = new LifecycleDTO();
        dto.setAction("REVOKE");
        dto.setReason("Test");

        BusinessException ex = assertThrows(BusinessException.class, () -> taskService.changeLifecycle(1L, dto, loginUser));
        assertEquals(400, ex.getCode());
    }


    @Test
    void testChangeLifecycleDisableOnPublished_Success() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setStatus("PUBLISHED");
        task.setIsDeleted(0);

        when(internshipTaskMapper.selectById(1L)).thenReturn(task);

        LifecycleDTO dto = new LifecycleDTO();
        dto.setAction("DISABLE");
        dto.setReason("Test");

        taskService.changeLifecycle(1L, dto, loginUser);

        assertEquals("DISABLED", task.getStatus());
        verify(internshipTaskMapper).updateById(task);
        verify(lifecycleLogMapper).insert(any(TaskLifecycleLog.class));
    }


    @Test
    void testAdjustStudentsOrgMode_ThrowsException() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setAssignMode("ORG");
        task.setIsDeleted(0);

        when(internshipTaskMapper.selectById(1L)).thenReturn(task);

        StudentAdjustDTO dto = new StudentAdjustDTO();
        dto.setAction("ADD");
        dto.setStudentIds(List.of(2L));
        dto.setReason("test reason");

        BusinessException ex = assertThrows(BusinessException.class, () -> taskService.adjustStudents(1L, dto, loginUser));
        assertEquals(400, ex.getCode());
    }


    @Test
    void testAdjustStudentsRemoveWithApplyData_ThrowsException() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setAssignMode("STUDENT");
        task.setIsDeleted(0);

        when(internshipTaskMapper.selectById(1L)).thenReturn(task);
        when(taskStudentMapper.selectCount(any())).thenReturn(1L);
        when(applyMapper.selectCount(any())).thenReturn(1L);

        StudentAdjustDTO dto = new StudentAdjustDTO();
        dto.setAction("REMOVE");
        dto.setStudentIds(List.of(2L));
        dto.setReason("test reason");

        BusinessException ex = assertThrows(BusinessException.class, () -> taskService.adjustStudents(1L, dto, loginUser));
        assertEquals(400, ex.getCode());
    }


    @Test
    void testAdjustStudentsAddStudent_Success() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setAssignMode("STUDENT");
        task.setIsDeleted(0);

        SysUser student = new SysUser();
        student.setId(2L);
        student.setUserType("STUDENT");
        student.setDeptId(100L);
        student.setUserNumber("S001");
        student.setRealName("Student 1");
        student.setClassId(10L);
        student.setIsDeleted(0);

        when(internshipTaskMapper.selectById(1L)).thenReturn(task);
        when(userMapper.selectById(2L)).thenReturn(student);
        when(taskStudentMapper.selectCount(any())).thenReturn(0L);

        StudentAdjustDTO dto = new StudentAdjustDTO();
        dto.setAction("ADD");
        dto.setStudentIds(List.of(2L));
        dto.setReason("test reason");

        taskService.adjustStudents(1L, dto, loginUser);

        verify(taskStudentMapper).insert(any(InternshipTaskStudent.class));
        verify(studentAuditLogMapper).insert(any(TaskStudentAuditLog.class));
    }

    @Test
    void testUpdateTaskDraftStudentMode_DuplicateKeyHandling() {
        // Mock task 8037
        InternshipTask task = new InternshipTask();
        task.setId(8037L);
        task.setDeptId(1L);
        task.setStatus("DRAFT");
        task.setAssignMode("STUDENT");
        task.setIsDeleted(0);
        
        when(internshipTaskMapper.selectById(8037L)).thenReturn(task);
        
        // Mock login user
        LoginUser loginUser = new LoginUser();
        loginUser.setUserType("DEPT_ADMIN");
        loginUser.setDeptId(1L);
        
        // DTO
        com.college.internship.dto.TaskUpdateDTO dto = new com.college.internship.dto.TaskUpdateDTO();
        dto.setStudentIds(List.of(3003L, 3004L));
        dto.setWeightEnterprise(new java.math.BigDecimal("20"));
        dto.setWeightTeacherProcess(new java.math.BigDecimal("20"));
        dto.setWeightWeeklyReport(new java.math.BigDecimal("20"));
        dto.setWeightStageMaterial(new java.math.BigDecimal("20"));
        dto.setWeightSummary(new java.math.BigDecimal("20"));
        dto.setAssignMode("STUDENT");
        
        // Mock existing student 3003
        InternshipTaskStudent existing = new InternshipTaskStudent();
        existing.setId(500L);
        existing.setTaskId(8037L);
        existing.setStudentId(3003L);
        
        // Mock selectList for existing students
        // Note: updateTask handles existing class/majors, we just mock them empty to avoid NPE
        lenient().when(taskMajorMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        lenient().when(taskClassMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(taskStudentMapper.selectList(any())).thenReturn(List.of(existing));
        
        SysUser mockStudent = new SysUser();
        mockStudent.setId(3004L);
        mockStudent.setUserType("STUDENT");
        mockStudent.setDeptId(1L);
        mockStudent.setIsDeleted(0);
        when(userMapper.selectById(3004L)).thenReturn(mockStudent);
        
        when(taskStudentMapper.selectCount(any())).thenReturn(0L);
        when(taskStudentMapper.insert(any())).thenThrow(new org.springframework.dao.DuplicateKeyException("Duplicate entry for uk_task_student"));
        
        when(taskStudentMapper.restoreDeletedStudent(any())).thenReturn(1);
        
        assertDoesNotThrow(() -> {
            taskService.updateTask(8037L, dto, loginUser);
        });
        
        verify(taskStudentMapper, times(1)).insert(any());
        verify(taskStudentMapper, times(1)).restoreDeletedStudent(any());
        verify(taskStudentMapper, never()).deleteById(500L);
    }

    @Test
    void testUpdateTaskDraftStudentMode_DuplicateKeyHandling_FailRestore() {
        InternshipTask task = new InternshipTask();
        task.setId(8037L);
        task.setDeptId(1L);
        task.setStatus("DRAFT");
        task.setAssignMode("STUDENT");
        task.setIsDeleted(0);
        
        when(internshipTaskMapper.selectById(8037L)).thenReturn(task);
        
        LoginUser loginUser = new LoginUser();
        loginUser.setUserType("DEPT_ADMIN");
        loginUser.setDeptId(1L);
        
        com.college.internship.dto.TaskUpdateDTO dto = new com.college.internship.dto.TaskUpdateDTO();
        dto.setStudentIds(List.of(3004L));
        dto.setWeightEnterprise(new java.math.BigDecimal("20"));
        dto.setWeightTeacherProcess(new java.math.BigDecimal("20"));
        dto.setWeightWeeklyReport(new java.math.BigDecimal("20"));
        dto.setWeightStageMaterial(new java.math.BigDecimal("20"));
        dto.setWeightSummary(new java.math.BigDecimal("20"));
        dto.setAssignMode("STUDENT");
        
        lenient().when(taskMajorMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        lenient().when(taskClassMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(taskStudentMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        
        SysUser mockStudent = new SysUser();
        mockStudent.setId(3004L);
        mockStudent.setUserType("STUDENT");
        mockStudent.setDeptId(1L);
        mockStudent.setIsDeleted(0);
        when(userMapper.selectById(3004L)).thenReturn(mockStudent);
        
        when(taskStudentMapper.selectCount(any())).thenReturn(0L);
        when(taskStudentMapper.insert(any())).thenThrow(new org.springframework.dao.DuplicateKeyException("Duplicate entry for uk_task_student"));
        
        // Return 0 to trigger error
        when(taskStudentMapper.restoreDeletedStudent(any())).thenReturn(0);
        
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            taskService.updateTask(8037L, dto, loginUser);
        });
        
        assertTrue(ex.getMessage().contains("恢复历史学生名单失败(影响行数不为1)"));
        verify(taskStudentMapper, times(1)).insert(any());
        verify(taskStudentMapper, times(1)).restoreDeletedStudent(any());
    }

    @Test
    void testChangeLifecycleArchiveFromEnded_Success() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setStatus("ENDED");
        task.setIsDeleted(0);
        when(internshipTaskMapper.selectById(1L)).thenReturn(task);

        LifecycleDTO dto = new LifecycleDTO();
        dto.setAction("ARCHIVE");
        dto.setReason("Archive ended task");

        taskService.changeLifecycle(1L, dto, loginUser);

        assertEquals("ARCHIVED", task.getStatus());
        verify(internshipTaskMapper).updateById(task);
        verify(lifecycleLogMapper).insert(any(TaskLifecycleLog.class));
    }

    @Test
    void testChangeLifecycleArchiveFromDisabled_Success() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setStatus("DISABLED");
        task.setIsDeleted(0);
        when(internshipTaskMapper.selectById(1L)).thenReturn(task);

        LifecycleDTO dto = new LifecycleDTO();
        dto.setAction("ARCHIVE");
        dto.setReason("Archive disabled task");

        taskService.changeLifecycle(1L, dto, loginUser);

        assertEquals("ARCHIVED", task.getStatus());
        verify(internshipTaskMapper, times(1)).updateById(task);
        verify(lifecycleLogMapper, times(1)).insert(any(TaskLifecycleLog.class));
    }
    
    @Test
    void testChangeLifecycleArchiveFromRevoked_Success() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setStatus("REVOKED");
        task.setIsDeleted(0);
        when(internshipTaskMapper.selectById(1L)).thenReturn(task);

        LifecycleDTO dto = new LifecycleDTO();
        dto.setAction("ARCHIVE");
        dto.setReason("Archive revoked task");

        taskService.changeLifecycle(1L, dto, loginUser);

        assertEquals("ARCHIVED", task.getStatus());
    }

    @Test
    void testChangeLifecycleArchiveFromPublished_ThrowsException() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setStatus("PUBLISHED");
        task.setIsDeleted(0);
        when(internshipTaskMapper.selectById(1L)).thenReturn(task);

        LifecycleDTO dto = new LifecycleDTO();
        dto.setAction("ARCHIVE");
        dto.setReason("Try archive published");

        BusinessException ex = assertThrows(BusinessException.class, () -> taskService.changeLifecycle(1L, dto, loginUser));
        assertTrue(ex.getMessage().contains("仅结束或停用、撤销的任务可以归档"));
    }

    @Test
    void testChangeLifecycleFromArchived_ThrowsException() {
        InternshipTask task = new InternshipTask();
        task.setId(1L);
        task.setDeptId(100L);
        task.setStatus("ARCHIVED");
        task.setIsDeleted(0);
        when(internshipTaskMapper.selectById(1L)).thenReturn(task);

        LifecycleDTO dto = new LifecycleDTO();
        dto.setAction("ARCHIVE"); // try archive again
        dto.setReason("Double archive");

        BusinessException ex = assertThrows(BusinessException.class, () -> taskService.changeLifecycle(1L, dto, loginUser));
        assertTrue(ex.getMessage().contains("任务已处于终态，禁止继续变更生命周期"));
    }

}