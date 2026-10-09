package com.college.internship;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.Phase7Properties;
import com.college.internship.dto.MaterialAuditDTO;
import com.college.internship.dto.MaterialSubmitDTO;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.entity.MaterialVersionHistory;
import com.college.internship.entity.StudentMaterialItem;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.InternshipTaskStudentMapper;
import com.college.internship.mapper.MaterialVersionHistoryMapper;
import com.college.internship.mapper.StudentMaterialItemMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IArchiveService;
import com.college.internship.service.impl.MaterialServiceImpl;
import com.college.internship.vo.MaterialUploadVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 阶段7 实习材料全流程纯内存隔离单元测试 (零连接数据库)
 * 覆盖：材料提报v1、重提v2版本自增、已通过防重提拦截、字数校验、导师通过/打分、导师驳回必填意见、魔数检测、SHA-256计算、越权下载与查验防御
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaterialIsolatedUnitTest {

    @Mock
    private StudentMaterialItemMapper materialItemMapper;
    @Mock
    private MaterialVersionHistoryMapper versionHistoryMapper;
    @Mock
    private InternshipTaskMapper taskMapper;
    @Mock
    private InternshipTaskStudentMapper taskStudentMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private IArchiveService archiveService;
    @Spy
    private Phase7Properties phase7Properties = new Phase7Properties();
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MaterialServiceImpl materialService;

    @TempDir
    Path tempStorageDir;

    private LoginUser studentUser;
    private LoginUser otherStudentUser;
    private LoginUser teacherUser;
    private LoginUser otherTeacherUser;
    private InternshipTask sampleTask;
    private InternshipTaskStudent sampleTaskStudent;

    @BeforeEach
    void setUp() {
        phase7Properties.getMaterial().setStorageDir(tempStorageDir.toString());

        studentUser = LoginUser.builder()
                .userId(5102L)
                .username("student_sim_02")
                .realName("李在途")
                .userType("STUDENT")
                .deptId(1L)
                .permissions(List.of("ROLE_STUDENT"))
                .build();

        otherStudentUser = LoginUser.builder()
                .userId(5199L)
                .username("student_other")
                .realName("其他同学")
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
                .userId(3199L)
                .username("teacher_other")
                .realName("非带教导师")
                .userType("TEACHER")
                .deptId(1L)
                .permissions(List.of("ROLE_TEACHER"))
                .build();

        sampleTask = InternshipTask.builder()
                .id(8001L)
                .taskName("2026届计算机毕业实习")
                .deptId(1L)
                .materialChecklist("[{\"materialCode\":\"TRIPARTITE_AGREEMENT\",\"materialName\":\"三方协议书/接收函盖章件\",\"materialType\":\"VOUCHER_FILE\",\"required\":true},{\"materialCode\":\"SUMMARY_REPORT\",\"materialName\":\"毕业实习总结报告\",\"materialType\":\"REPORT_TEXT\",\"required\":true}]")
                .build();

        sampleTaskStudent = InternshipTaskStudent.builder()
                .id(1L)
                .taskId(8001L)
                .studentId(5102L)
                .teacherId(3101L)
                .build();

        when(taskMapper.selectById(8001L)).thenReturn(sampleTask);
    }

    @Test
    @DisplayName("用例1: 学生首次提报材料v1 -> 成功创建记录、版本为1、SHA-256哈希保存、版本快照写入")
    void testSubmitMaterial_Success_NewItemV1() {
        MaterialSubmitDTO dto = new MaterialSubmitDTO();
        dto.setTaskId(8001L);
        dto.setMaterialCode("TRIPARTITE_AGREEMENT");
        dto.setContentText("三方协议书已完成用人单位签章并扫描。");
        dto.setFileName("tripartite_agreement.pdf");
        dto.setFileSize(204800L);
        dto.setFileHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        dto.setAttachmentUrl("/api/v1/internship/materials/files/8001/5102/test.pdf");

        when(materialItemMapper.selectOne(any())).thenReturn(null);
        when(materialItemMapper.insert(any(StudentMaterialItem.class))).thenAnswer(invocation -> {
            StudentMaterialItem item = invocation.getArgument(0);
            item.setId(1001L);
            return 1;
        });

        Long resultId = materialService.submitMaterial(dto, studentUser);

        assertNotNull(resultId);
        ArgumentCaptor<StudentMaterialItem> itemCaptor = ArgumentCaptor.forClass(StudentMaterialItem.class);
        verify(materialItemMapper).insert(itemCaptor.capture());
        StudentMaterialItem inserted = itemCaptor.getValue();
        assertEquals(1, inserted.getVersion());
        assertEquals("SUBMITTED", inserted.getStatus());
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", inserted.getFileHash());

        ArgumentCaptor<MaterialVersionHistory> historyCaptor = ArgumentCaptor.forClass(MaterialVersionHistory.class);
        verify(versionHistoryMapper).insert(historyCaptor.capture());
        MaterialVersionHistory history = historyCaptor.getValue();
        assertEquals(1, history.getVersion());
        assertEquals("SUBMITTED", history.getStatus());
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", history.getFileHash());
    }

    @Test
    @DisplayName("用例2: 学生重提被退回材料v2 -> 版本自增为2、状态重置为SUBMITTED、快照记录v2")
    void testSubmitMaterial_Success_ResubmitV2() {
        StudentMaterialItem existingItem = StudentMaterialItem.builder()
                .taskId(8001L)
                .studentId(5102L)
                .materialCode("TRIPARTITE_AGREEMENT")
                .materialName("三方协议书/接收函盖章件")
                .materialType("VOUCHER_FILE")
                .version(1)
                .status("RETURNED")
                .auditTeacherId(3101L)
                .auditComment("公章不清晰，请重新扫描")
                .build();
        existingItem.setId(1001L);

        when(materialItemMapper.selectOne(any())).thenReturn(existingItem);

        MaterialSubmitDTO dto = new MaterialSubmitDTO();
        dto.setTaskId(8001L);
        dto.setMaterialCode("TRIPARTITE_AGREEMENT");
        dto.setContentText("已重新高清扫描用人单位公章。");
        dto.setFileName("tripartite_agreement_v2.pdf");
        dto.setFileSize(250000L);
        dto.setFileHash("a1b2c3d4e5f678901234567890abcdef1234567890abcdef1234567890abcdef");
        dto.setAttachmentUrl("/api/v1/internship/materials/files/8001/5102/test_v2.pdf");

        Long resultId = materialService.submitMaterial(dto, studentUser);

        assertEquals(1001L, resultId);
        assertEquals(2, existingItem.getVersion());
        assertEquals("SUBMITTED", existingItem.getStatus());
        assertNull(existingItem.getAuditTeacherId());
        assertNull(existingItem.getAuditComment());
        assertEquals("a1b2c3d4e5f678901234567890abcdef1234567890abcdef1234567890abcdef", existingItem.getFileHash());
        verify(materialItemMapper).updateById(existingItem);

        ArgumentCaptor<MaterialVersionHistory> historyCaptor = ArgumentCaptor.forClass(MaterialVersionHistory.class);
        verify(versionHistoryMapper).insert(historyCaptor.capture());
        assertEquals(2, historyCaptor.getValue().getVersion());
        assertEquals("a1b2c3d4e5f678901234567890abcdef1234567890abcdef1234567890abcdef", historyCaptor.getValue().getFileHash());
    }

    @Test
    @DisplayName("用例3: 已查验通过(APPROVED)的材料禁止再次提交重提 -> 阻断并抛出异常")
    void testSubmitMaterial_BlockedIfApproved() {
        StudentMaterialItem approvedItem = StudentMaterialItem.builder()
                .taskId(8001L)
                .studentId(5102L)
                .materialCode("TRIPARTITE_AGREEMENT")
                .version(1)
                .status("APPROVED")
                .auditTeacherId(3101L)
                .auditScore(new BigDecimal("95.00"))
                .build();
        approvedItem.setId(1001L);

        when(materialItemMapper.selectOne(any())).thenReturn(approvedItem);

        MaterialSubmitDTO dto = new MaterialSubmitDTO();
        dto.setTaskId(8001L);
        dto.setMaterialCode("TRIPARTITE_AGREEMENT");
        dto.setContentText("尝试篡改已通过材料");

        BusinessException ex = assertThrows(BusinessException.class, () -> materialService.submitMaterial(dto, studentUser));
        assertTrue(ex.getMessage().contains("已查验通过的材料禁止重复提交修改"));
    }

    @Test
    @DisplayName("用例4: 非学生角色提报材料 -> 权限拦截抛出403")
    void testSubmitMaterial_NonStudent_Forbidden() {
        MaterialSubmitDTO dto = new MaterialSubmitDTO();
        dto.setTaskId(8001L);
        dto.setMaterialCode("TRIPARTITE_AGREEMENT");

        BusinessException ex = assertThrows(BusinessException.class, () -> materialService.submitMaterial(dto, teacherUser));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("用例5: 总结报告正文字数不足 -> 抛出400校验异常")
    void testSubmitMaterial_SummaryReport_TooShort() {
        MaterialSubmitDTO dto = new MaterialSubmitDTO();
        dto.setTaskId(8001L);
        dto.setMaterialCode("SUMMARY_REPORT");
        dto.setContentText("字数过短的测试");

        BusinessException ex = assertThrows(BusinessException.class, () -> materialService.submitMaterial(dto, studentUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("字数不足"));
    }

    @Test
    @DisplayName("用例6: 导师查验通过并打分 -> 状态流转为APPROVED、分数保存、快照同步更新")
    void testAuditMaterial_TeacherApproved_Success() {
        StudentMaterialItem item = StudentMaterialItem.builder()
                .taskId(8001L)
                .studentId(5102L)
                .version(1)
                .status("SUBMITTED")
                .build();
        item.setId(1001L);

        when(materialItemMapper.selectById(1001L)).thenReturn(item);
        when(taskStudentMapper.selectOne(any())).thenReturn(sampleTaskStudent);

        MaterialVersionHistory history = MaterialVersionHistory.builder()
                .materialId(1001L)
                .version(1)
                .status("SUBMITTED")
                .build();
        when(versionHistoryMapper.selectOne(any())).thenReturn(history);

        MaterialAuditDTO auditDTO = new MaterialAuditDTO();
        auditDTO.setAction("APPROVED");
        auditDTO.setAuditScore(new BigDecimal("92.50"));
        auditDTO.setAuditComment("材料真实合规，准予通过");

        materialService.auditMaterial(1001L, auditDTO, teacherUser);

        assertEquals("APPROVED", item.getStatus());
        assertEquals(new BigDecimal("92.50"), item.getAuditScore());
        assertEquals("材料真实合规，准予通过", item.getAuditComment());
        assertEquals(3101L, item.getAuditTeacherId());
        assertNotNull(item.getAuditTime());
        verify(materialItemMapper).updateById(item);

        assertEquals("APPROVED", history.getStatus());
        assertEquals(new BigDecimal("92.50"), history.getAuditScore());
        verify(versionHistoryMapper).updateById(history);
    }

    @Test
    @DisplayName("用例7: 导师退回未填写查验意见 -> 抛出400明确阻断")
    void testAuditMaterial_TeacherReturned_RequiresComment() {
        StudentMaterialItem item = StudentMaterialItem.builder()
                .taskId(8001L)
                .studentId(5102L)
                .version(1)
                .status("SUBMITTED")
                .build();
        item.setId(1001L);

        when(materialItemMapper.selectById(1001L)).thenReturn(item);
        when(taskStudentMapper.selectOne(any())).thenReturn(sampleTaskStudent);

        MaterialAuditDTO auditDTO = new MaterialAuditDTO();
        auditDTO.setAction("RETURNED");
        auditDTO.setAuditComment("   ");

        BusinessException ex = assertThrows(BusinessException.class, () -> materialService.auditMaterial(1001L, auditDTO, teacherUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("退回必须填写查验意见"));
    }

    @Test
    @DisplayName("用例8: 非负责带教导师查验学生材料 -> 抛出403行级隔离拦截")
    void testAuditMaterial_Teacher_UnauthorizedStudent_Forbidden() {
        StudentMaterialItem item = StudentMaterialItem.builder()
                .taskId(8001L)
                .studentId(5102L)
                .version(1)
                .status("SUBMITTED")
                .build();
        item.setId(1001L);

        when(materialItemMapper.selectById(1001L)).thenReturn(item);
        // otherTeacherUser 不是该学生的指导教师
        when(taskStudentMapper.selectOne(any())).thenReturn(null);

        MaterialAuditDTO auditDTO = new MaterialAuditDTO();
        auditDTO.setAction("APPROVED");
        auditDTO.setAuditScore(new BigDecimal("90.00"));

        BusinessException ex = assertThrows(BusinessException.class, () -> materialService.auditMaterial(1001L, auditDTO, otherTeacherUser));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("无权查验非负责学生"));
    }

    @Test
    @DisplayName("用例9: 上传有效PDF文件 -> 通过魔数特征检测、计算准确SHA-256、返回哈希与文件信息")
    void testUploadMaterialFile_Success_ValidPdf() {
        // %PDF-1.4 格式魔数
        byte[] pdfContent = "%PDF-1.4\n1 0 obj\n<<\n>>\nendobj\ntrailer\n<<\n>>\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "tripartite_voucher.pdf", "application/pdf", pdfContent);

        MaterialUploadVO uploadVO = materialService.uploadMaterialFile(file, 8001L, "TRIPARTITE_AGREEMENT", studentUser);

        assertNotNull(uploadVO);
        assertEquals("tripartite_voucher.pdf", uploadVO.getFileName());
        assertEquals(pdfContent.length, uploadVO.getFileSize());
        assertNotNull(uploadVO.getFileHash());
        assertEquals(64, uploadVO.getFileHash().length());
        assertTrue(uploadVO.getAttachmentUrl().startsWith("/api/v1/internship/materials/files/8001/5102/"));
    }

    @Test
    @DisplayName("用例10: 上传伪造扩展名文件(魔数不匹配) -> 阻断并抛出魔数特征校验异常")
    void testUploadMaterialFile_ForgedFile_Rejected() {
        // 扩展名为 pdf 但内容为纯文本普通字符串
        byte[] fakeContent = "This is not a pdf file but fake text content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "malicious_script.pdf", "application/pdf", fakeContent);

        BusinessException ex = assertThrows(BusinessException.class, () -> materialService.uploadMaterialFile(file, 8001L, "TRIPARTITE_AGREEMENT", studentUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("特征不匹配"));
    }

    @Test
    @DisplayName("用例11: 上传危险文件类型(如.exe) -> 阻断并抛出扩展名不支持异常")
    void testUploadMaterialFile_DisallowedExtension_Rejected() {
        byte[] content = "MZ executable payload".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/octet-stream", content);

        BusinessException ex = assertThrows(BusinessException.class, () -> materialService.uploadMaterialFile(file, 8001L, "TRIPARTITE_AGREEMENT", studentUser));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("仅支持上传"));
    }

    @Test
    @DisplayName("用例12: 学生越权下载他人材料附件(IDOR防御) -> 抛出403无权下载异常")
    void testDownloadMaterialFile_StudentIdor_Forbidden() {
        StudentMaterialItem item = StudentMaterialItem.builder()
                .taskId(8001L)
                .studentId(5102L)
                .attachmentUrl("/api/v1/internship/materials/files/8001/5102/secret.pdf")
                .fileName("secret.pdf")
                .build();
        item.setId(2001L);

        when(materialItemMapper.selectById(2001L)).thenReturn(item);

        BusinessException ex = assertThrows(BusinessException.class, () -> materialService.downloadMaterialFile(2001L, null, otherStudentUser));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("学生无权下载其他同学"));
    }
}
