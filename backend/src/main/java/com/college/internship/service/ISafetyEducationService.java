package com.college.internship.service;

import com.college.internship.dto.CommitmentSignDTO;
import com.college.internship.dto.ExamSubmitDTO;
import com.college.internship.dto.SafetyMaterialDTO;
import com.college.internship.dto.SafetyQuestionDTO;
import com.college.internship.entity.SafetyMaterialItem;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.ExamResultVO;
import com.college.internship.vo.QuestionVO;
import com.college.internship.vo.SafetyStatusVO;

import java.util.List;
import java.util.Map;

/**
 * 安全教育准入与考试核心服务接口
 */
public interface ISafetyEducationService extends IBaseService {

    /**
     * 新增或编辑安全教育资料 (SAFE-001/002)
     */
    SafetyMaterialItem saveMaterial(SafetyMaterialDTO dto, LoginUser loginUser);

    /**
     * 删除安全教育资料
     */
    void deleteMaterial(Long id, LoginUser loginUser);

    /**
     * 获取某任务或通用安全教育资料列表
     */
    List<SafetyMaterialItem> listMaterials(Long taskId, LoginUser loginUser);

    /**
     * 新增或编辑测试试题 (SAFE-003)
     */
    void saveQuestion(SafetyQuestionDTO dto, LoginUser loginUser);

    /**
     * 删除试题
     */
    void deleteQuestion(Long id, LoginUser loginUser);

    /**
     * 学生获取在线考试试卷 (脱敏隐藏正确答案)
     */
    List<QuestionVO> getExamPaper(Long taskId, LoginUser loginUser);

    /**
     * 学生交卷与系统即时判分 (SAFE-005)
     */
    ExamResultVO submitExam(ExamSubmitDTO dto, LoginUser loginUser);

    /**
     * 学生签署安全承诺书 (SAFE-006 & SAFE-007)
     */
    void signCommitment(CommitmentSignDTO dto, String clientIp, LoginUser loginUser);

    /**
     * 获取完整五阶段安全教育状态 (NOT_STARTED, STUDYING, PENDING_TEST, PASSED, COMPLETED)
     */
    SafetyStatusVO getSafetyStatus(Long taskId, Long studentId, LoginUser loginUser);

    /**
     * 院系负责人安全教育达标统计监控 (SAFE-009，按任务和院系严格范围过滤)
     */
    Map<String, Object> getDeptSafetyStatistics(Long taskId, Long deptId, LoginUser loginUser);

    /**
     * 学生阅读安全资料记录与进度更新 (驱动 NOT_STARTED -> STUDYING -> PENDING_TEST)
     */
    void markMaterialRead(Long taskId, Long materialId, LoginUser loginUser);

    /**
     * 指导教师与院系负责人查询负责学生安全教育进度 (SAFE-008 & API-125)
     */
    List<com.college.internship.vo.StudentSafetyProgressVO> getStudentsSafetyProgress(Long taskId, String status, String keyword, LoginUser loginUser);

    /**
     * 一键催办未达标学生 (API-123 & SAFE-008)
     */
    com.college.internship.vo.RemindResultVO remindStudents(Long taskId, Long studentId, LoginUser loginUser);
}
