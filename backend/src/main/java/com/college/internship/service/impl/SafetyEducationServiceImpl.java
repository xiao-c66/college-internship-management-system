package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.dto.CommitmentSignDTO;
import com.college.internship.dto.ExamSubmitDTO;
import com.college.internship.dto.SafetyMaterialDTO;
import com.college.internship.dto.SafetyQuestionDTO;
import com.college.internship.entity.BaseClass;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.entity.SafetyCommitmentSign;
import com.college.internship.entity.SafetyExamAnswerDetail;
import com.college.internship.entity.SafetyExamAttempt;
import com.college.internship.entity.SafetyMaterialItem;
import com.college.internship.entity.SafetyTestQuestion;
import com.college.internship.entity.SysOperationLog;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.InternshipTaskStudentMapper;
import com.college.internship.mapper.SafetyCommitmentSignMapper;
import com.college.internship.mapper.SafetyExamAnswerDetailMapper;
import com.college.internship.mapper.SafetyExamAttemptMapper;
import com.college.internship.mapper.SafetyMaterialItemMapper;
import com.college.internship.mapper.SafetyTestQuestionMapper;
import com.college.internship.mapper.SysOperationLogMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISafetyEducationService;
import com.college.internship.vo.ExamResultVO;
import com.college.internship.vo.QuestionVO;
import com.college.internship.vo.RemindResultVO;
import com.college.internship.vo.SafetyStatusVO;
import com.college.internship.vo.StudentSafetyProgressVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全教育准入与考试核心业务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SafetyEducationServiceImpl implements ISafetyEducationService {

    private final SafetyMaterialItemMapper materialItemMapper;
    private final SafetyTestQuestionMapper questionMapper;
    private final SafetyExamAttemptMapper examAttemptMapper;
    private final SafetyExamAnswerDetailMapper answerDetailMapper;
    private final SafetyCommitmentSignMapper commitmentSignMapper;
    private final InternshipTaskMapper taskMapper;
    private final InternshipTaskStudentMapper taskStudentMapper;
    private final SysUserMapper userMapper;
    private final BaseClassMapper classMapper;
    private final BaseDepartmentMapper departmentMapper;
    private final SysOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SafetyMaterialItem saveMaterial(SafetyMaterialDTO dto, LoginUser loginUser) {
        if (dto.getTaskId() == null) {
            // SAFE-001 全校通用资料必须由学校管理员维护
            if (!"SYS_ADMIN".equals(loginUser.getUserType())) {
                throw new BusinessException(403, "全校通用安全资料与范本文档仅限学校管理员维护 (SAFE-001)");
            }
        } else {
            // SAFE-002 任务专属安全资料
            if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
                throw new BusinessException(403, "仅院系负责人或管理员具备任务安全资料配置权限 (SAFE-002)");
            }
        }

        SafetyMaterialItem item = SafetyMaterialItem.builder()
                .taskId(dto.getTaskId())
                .title(dto.getTitle())
                .contentType(dto.getContentType() != null ? dto.getContentType() : "TEXT")
                .contentBody(dto.getContentBody())
                .fileUrl(dto.getFileUrl())
                .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0)
                .status(dto.getStatus() != null ? dto.getStatus() : 1)
                .build();

        materialItemMapper.insert(item);
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterial(Long id, LoginUser loginUser) {
        SafetyMaterialItem item = materialItemMapper.selectById(id);
        if (item == null || item.getIsDeleted() == 1) {
            throw new BusinessException(404, "安全资料不存在");
        }
        if (item.getTaskId() == null && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "无权删除全校通用安全资料");
        }
        materialItemMapper.deleteById(id);
    }

    @Override
    public List<SafetyMaterialItem> listMaterials(Long taskId, LoginUser loginUser) {
        LambdaQueryWrapper<SafetyMaterialItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SafetyMaterialItem::getIsDeleted, 0)
                .eq(SafetyMaterialItem::getStatus, 1);

        if (taskId != null) {
            wrapper.and(w -> w.eq(SafetyMaterialItem::getTaskId, taskId).or().isNull(SafetyMaterialItem::getTaskId));
        } else {
            wrapper.isNull(SafetyMaterialItem::getTaskId);
        }

        wrapper.orderByAsc(SafetyMaterialItem::getSortOrder).orderByAsc(SafetyMaterialItem::getId);
        return materialItemMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveQuestion(SafetyQuestionDTO dto, LoginUser loginUser) {
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅院系负责人或管理员可以配置安全测试题库 (SAFE-003)");
        }

        SafetyTestQuestion question = SafetyTestQuestion.builder()
                .taskId(dto.getTaskId())
                .questionType(dto.getQuestionType())
                .stem(dto.getStem())
                .options(dto.getOptions())
                .correctAnswer(dto.getCorrectAnswer().trim().toUpperCase())
                .score(dto.getScore())
                .analysis(dto.getAnalysis())
                .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0)
                .status(dto.getStatus() != null ? dto.getStatus() : 1)
                .build();

        questionMapper.insert(question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(Long id, LoginUser loginUser) {
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅院系负责人或管理员可以删除试题");
        }
        questionMapper.deleteById(id);
    }

    @Override
    public List<QuestionVO> getExamPaper(Long taskId, LoginUser loginUser) {
        if (loginUser != null && "STUDENT".equals(loginUser.getUserType())) {
            InternshipTaskStudent ts = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, taskId)
                    .eq(InternshipTaskStudent::getStudentId, loginUser.getUserId())
                    .eq(InternshipTaskStudent::getIsDeleted, 0));
            if (ts == null) {
                throw new BusinessException(403, "您未圈定在当前实习任务学生名单中，无权获取试卷 (TASK-003)");
            }

            // 核心规则：阻止未完成资料学习的学生直接获取试卷，严格校验学习完成度与准考资格 (SAFE-004)
            Long totalMat = materialItemMapper.selectCount(new LambdaQueryWrapper<SafetyMaterialItem>()
                    .eq(SafetyMaterialItem::getIsDeleted, 0)
                    .and(w -> w.eq(SafetyMaterialItem::getTaskId, taskId).or().isNull(SafetyMaterialItem::getTaskId)));
            int total = (totalMat != null) ? totalMat.intValue() : 0;
            int readCount = (ts.getReadMaterialCount() != null) ? ts.getReadMaterialCount() : 0;

            if (total > 0 && readCount < total) {
                throw new BusinessException(400, "尚未完成全部必学安全教育规程资料 (" + readCount + "/" + total + ")，暂不具备准入测试资格，请先完成学习 (SAFE-004)");
            }
        }

        LambdaQueryWrapper<SafetyTestQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SafetyTestQuestion::getIsDeleted, 0)
                .eq(SafetyTestQuestion::getStatus, 1);

        if (taskId != null) {
            wrapper.and(w -> w.eq(SafetyTestQuestion::getTaskId, taskId).or().isNull(SafetyTestQuestion::getTaskId));
        }

        wrapper.orderByAsc(SafetyTestQuestion::getSortOrder).orderByAsc(SafetyTestQuestion::getId);
        List<SafetyTestQuestion> list = questionMapper.selectList(wrapper);
        // 组卷规则确认 (SAFE-004)：
        // 当前阶段安全准入测试采用【任务专属题库与全校通用题库之全部启用试题全量抽取 + 题目随机乱序洗牌】机制；
        // 暂不执行按题量、题型比例部分抽题规则（因当前阶段任务表及题库无题型配比配置项）。
        java.util.Collections.shuffle(list);

        // 脱敏安全转换：绝对隐藏 correctAnswer 与 analysis
        return list.stream().map(q -> QuestionVO.builder()
                .id(q.getId())
                .taskId(q.getTaskId())
                .questionType(q.getQuestionType())
                .stem(q.getStem())
                .options(q.getOptions())
                .score(q.getScore())
                .sortOrder(q.getSortOrder())
                .build()
        ).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamResultVO submitExam(ExamSubmitDTO dto, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (!"STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅学生可参加安全教育准入测试");
        }
        Long studentId = loginUser.getUserId();

        // 强校验：学生必须被圈定在当前实习任务名单中 (TASK-003)
        InternshipTaskStudent ts = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                .eq(InternshipTaskStudent::getTaskId, dto.getTaskId())
                .eq(InternshipTaskStudent::getStudentId, studentId)
                .eq(InternshipTaskStudent::getIsDeleted, 0));
        if (ts == null) {
            throw new BusinessException(403, "您未圈定在当前实习任务学生名单中，无权交卷 (TASK-003)");
        }

        InternshipTask task = taskMapper.selectById(dto.getTaskId());
        if (task == null || task.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习任务不存在");
        }
        if (task.getSafetyPassingScore() == null || task.getSafetyMaxAttempts() == null) {
            throw new BusinessException(400, "实习任务尚未配置安全考试及格分或最大尝试次数，无法开考");
        }
        int passingScore = task.getSafetyPassingScore();
        int maxAttempts = task.getSafetyMaxAttempts();

        // 核心规则：阻止未完成资料学习的学生直接交卷，严格校验学习完成度与准考资格 (SAFE-004)
        Long totalMat = materialItemMapper.selectCount(new LambdaQueryWrapper<SafetyMaterialItem>()
                .eq(SafetyMaterialItem::getIsDeleted, 0)
                .and(w -> w.eq(SafetyMaterialItem::getTaskId, dto.getTaskId()).or().isNull(SafetyMaterialItem::getTaskId)));
        int total = (totalMat != null) ? totalMat.intValue() : 0;
        int readCount = (ts.getReadMaterialCount() != null) ? ts.getReadMaterialCount() : 0;

        if (total > 0 && readCount < total) {
            throw new BusinessException(400, "尚未完成全部必学安全教育规程资料 (" + readCount + "/" + total + ")，无法提交测试，请先完成规程学习 (SAFE-004)");
        }

        // 检查已有作答次数
        Long count = examAttemptMapper.selectCount(new LambdaQueryWrapper<SafetyExamAttempt>()
                .eq(SafetyExamAttempt::getTaskId, dto.getTaskId())
                .eq(SafetyExamAttempt::getStudentId, studentId)
                .eq(SafetyExamAttempt::getIsDeleted, 0));
        int currentAttempt = (count != null ? count.intValue() : 0) + 1;

        // 若已达标不可再测试
        Long passedCount = examAttemptMapper.selectCount(new LambdaQueryWrapper<SafetyExamAttempt>()
                .eq(SafetyExamAttempt::getTaskId, dto.getTaskId())
                .eq(SafetyExamAttempt::getStudentId, studentId)
                .eq(SafetyExamAttempt::getIsPassed, 1)
                .eq(SafetyExamAttempt::getIsDeleted, 0));
        if (passedCount != null && passedCount > 0) {
            throw new BusinessException(400, "您已通过本次实习安全测试，无需重复交卷");
        }

        if (currentAttempt > maxAttempts) {
            throw new BusinessException(400, "已达到本任务最大允许测试次数 (" + maxAttempts + " 次)，请联系指导教师线下辅导");
        }

        // 题目合法性强校验：交卷必须包含题目，且每个 questionId 必须属于当前任务或全局题库并处于启用状态
        if (dto.getAnswers() == null || dto.getAnswers().isEmpty()) {
            throw new BusinessException(400, "交卷失败：试卷未包含任何作答试题");
        }

        // 客观题系统自动逐题核对与判分
        BigDecimal totalScore = BigDecimal.ZERO;
        List<SafetyExamAnswerDetail> details = new ArrayList<>();

        for (ExamSubmitDTO.AnswerItem item : dto.getAnswers()) {
            if (item.getQuestionId() == null) {
                throw new BusinessException(400, "交卷失败：存在无效的试题ID");
            }
            SafetyTestQuestion question = questionMapper.selectById(item.getQuestionId());
            if (question == null || question.getIsDeleted() == 1 || question.getStatus() != 1) {
                throw new BusinessException(400, "交卷失败：试题 [" + item.getQuestionId() + "] 不存在或已被停用");
            }
            if (question.getTaskId() != null && !question.getTaskId().equals(dto.getTaskId())) {
                throw new BusinessException(400, "交卷失败：试题 [" + item.getQuestionId() + "] 不属于当前实习任务题库，禁止跨任务作答");
            }

            boolean isCorrect = false;
            BigDecimal scoreObtained = BigDecimal.ZERO;
            String stdAns = question.getCorrectAnswer().trim().toUpperCase();
            String userAns = (item.getStudentAnswer() != null) ? item.getStudentAnswer().trim().toUpperCase() : "";
            if (stdAns.equalsIgnoreCase(userAns)) {
                isCorrect = true;
                scoreObtained = new BigDecimal(question.getScore());
                totalScore = totalScore.add(scoreObtained);
            }

            SafetyExamAnswerDetail detail = SafetyExamAnswerDetail.builder()
                    .questionId(item.getQuestionId())
                    .studentAnswer(item.getStudentAnswer())
                    .isCorrect(isCorrect ? 1 : 0)
                    .scoreObtained(scoreObtained)
                    .createTime(LocalDateTime.now())
                    .build();
            details.add(detail);
        }

        boolean isPassed = totalScore.compareTo(new BigDecimal(passingScore)) >= 0;

        SafetyExamAttempt attempt = SafetyExamAttempt.builder()
                .taskId(dto.getTaskId())
                .studentId(studentId)
                .attemptNo(currentAttempt)
                .totalScore(totalScore)
                .isPassed(isPassed ? 1 : 0)
                .startTime(LocalDateTime.now().minusMinutes(10))
                .submitTime(LocalDateTime.now())
                .build();

        examAttemptMapper.insert(attempt);

        // 绑定 attemptId 并保存逐题明细
        for (SafetyExamAnswerDetail d : details) {
            d.setAttemptId(attempt.getId());
            answerDetailMapper.insert(d);
        }

        // 更新学生安全教育状态与任务圈定记录
        updateTaskStudentSafetyStatus(ts, dto.getTaskId(), studentId, total);
        taskStudentMapper.updateById(ts);

        return ExamResultVO.builder()
                .attemptId(attempt.getId())
                .attemptNo(currentAttempt)
                .totalScore(totalScore)
                .passingScore(passingScore)
                .isPassed(isPassed ? 1 : 0)
                .resultDesc(isPassed ? "恭喜您顺利通过安全教育准入测试！" : "测试成绩未达及格线 (" + passingScore + "分)，请重新复习安全资料后重测")
                .submitTime(attempt.getSubmitTime())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signCommitment(CommitmentSignDTO dto, String clientIp, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (!"STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅学生可签署安全责任承诺书");
        }
        Long studentId = loginUser.getUserId();

        // 强校验：学生必须被圈定在当前实习任务名单中 (TASK-003)
        InternshipTaskStudent ts = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                .eq(InternshipTaskStudent::getTaskId, dto.getTaskId())
                .eq(InternshipTaskStudent::getStudentId, studentId)
                .eq(InternshipTaskStudent::getIsDeleted, 0));
        if (ts == null) {
            throw new BusinessException(403, "您未圈定在当前实习任务学生名单中，无权签署安全责任承诺书 (TASK-003)");
        }

        SafetyCommitmentSign existing = commitmentSignMapper.selectOne(new LambdaQueryWrapper<SafetyCommitmentSign>()
                .eq(SafetyCommitmentSign::getTaskId, dto.getTaskId())
                .eq(SafetyCommitmentSign::getStudentId, studentId)
                .eq(SafetyCommitmentSign::getIsDeleted, 0));

        String standardText = "本人已认真阅读并严格知晓高校实习安全纪律与责任要求，在实习期间严格遵守安全操作规程，防范人身及财产风险，如遇突发异常及时向校内导师与学院报告。";

        if (existing != null) {
            existing.setIsSigned(1);
            existing.setSignIp(clientIp);
            existing.setSignTime(LocalDateTime.now());
            if (dto.getInsuranceFileUrl() != null) {
                existing.setInsuranceFileUrl(dto.getInsuranceFileUrl());
            }
            commitmentSignMapper.updateById(existing);
        } else {
            SafetyCommitmentSign sign = SafetyCommitmentSign.builder()
                    .taskId(dto.getTaskId())
                    .studentId(studentId)
                    .commitmentText(standardText)
                    .isSigned(1)
                    .signIp(clientIp)
                    .signTime(LocalDateTime.now())
                    .insuranceFileUrl(dto.getInsuranceFileUrl())
                    .build();
            commitmentSignMapper.insert(sign);
        }

        // 同步更新学生任务表五阶段状态与圈定数据
        Long totalMat = materialItemMapper.selectCount(new LambdaQueryWrapper<SafetyMaterialItem>()
                .eq(SafetyMaterialItem::getIsDeleted, 0)
                .and(w -> w.eq(SafetyMaterialItem::getTaskId, dto.getTaskId()).or().isNull(SafetyMaterialItem::getTaskId)));
        updateTaskStudentSafetyStatus(ts, dto.getTaskId(), studentId, totalMat != null ? totalMat.intValue() : 0);
        taskStudentMapper.updateById(ts);

        log.info("学生 [{}] 成功签署实习任务 [{}] 安全承诺书，当前状态: {}", studentId, dto.getTaskId(), ts.getSafetyStatus());
    }

    @Override
    public SafetyStatusVO getSafetyStatus(Long taskId, Long studentId, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "请先登录");
        }

        Long targetStudentId;
        if ("STUDENT".equals(loginUser.getUserType())) {
            if (studentId != null && !studentId.equals(loginUser.getUserId())) {
                throw new BusinessException(403, "学生无权查询他人安全教育准入状态 (SAFE-008)");
            }
            targetStudentId = loginUser.getUserId();
        } else {
            if (studentId == null) {
                throw new BusinessException(400, "查询学生ID不能为空");
            }
            targetStudentId = studentId;
        }

        InternshipTaskStudent ts = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                .eq(InternshipTaskStudent::getTaskId, taskId)
                .eq(InternshipTaskStudent::getStudentId, targetStudentId)
                .eq(InternshipTaskStudent::getIsDeleted, 0));
        if (ts == null) {
            if ("STUDENT".equals(loginUser.getUserType())) {
                throw new BusinessException(403, "您未圈定在当前实习任务学生名单中 (TASK-003)");
            } else {
                throw new BusinessException(404, "该学生未圈定在当前实习任务名单中");
            }
        }

        if ("TEACHER".equals(loginUser.getUserType())) {
            if (ts.getTeacherId() == null || !ts.getTeacherId().equals(loginUser.getUserId())) {
                throw new BusinessException(403, "指导教师仅能查看本人负责分配学生的安全教育状态 (SAFE-008)");
            }
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            SysUser stuUser = userMapper.selectById(targetStudentId);
            if (stuUser == null || !loginUser.getDeptId().equals(stuUser.getDeptId())) {
                throw new BusinessException(403, "院系负责人仅能查看本院系学生安全教育状态 (SAFE-009)");
            }
        }

        InternshipTask task = taskMapper.selectById(taskId);
        Integer passingScore = (task != null) ? task.getSafetyPassingScore() : null;
        Integer maxAttempts = (task != null) ? task.getSafetyMaxAttempts() : null;

        // 1. 资料统计
        Long matCount = materialItemMapper.selectCount(new LambdaQueryWrapper<SafetyMaterialItem>()
                .eq(SafetyMaterialItem::getIsDeleted, 0)
                .and(w -> w.eq(SafetyMaterialItem::getTaskId, taskId).or().isNull(SafetyMaterialItem::getTaskId)));
        int total = matCount != null ? matCount.intValue() : 0;

        // 2. 考试记录统计
        List<SafetyExamAttempt> attempts = examAttemptMapper.selectList(new LambdaQueryWrapper<SafetyExamAttempt>()
                .eq(SafetyExamAttempt::getTaskId, taskId)
                .eq(SafetyExamAttempt::getStudentId, targetStudentId)
                .eq(SafetyExamAttempt::getIsDeleted, 0)
                .orderByDesc(SafetyExamAttempt::getTotalScore));

        BigDecimal highestScore = BigDecimal.ZERO;
        boolean hasPassed = false;
        if (!attempts.isEmpty()) {
            highestScore = attempts.get(0).getTotalScore();
            hasPassed = attempts.stream().anyMatch(a -> a.getIsPassed() == 1);
        }

        // 3. 承诺书签署状态
        SafetyCommitmentSign sign = commitmentSignMapper.selectOne(new LambdaQueryWrapper<SafetyCommitmentSign>()
                .eq(SafetyCommitmentSign::getTaskId, taskId)
                .eq(SafetyCommitmentSign::getStudentId, targetStudentId)
                .eq(SafetyCommitmentSign::getIsDeleted, 0));
        boolean isSigned = (sign != null && sign.getIsSigned() == 1);

        // 4. 同步至 internship_task_student 并确保五阶段状态一致
        updateTaskStudentSafetyStatus(ts, taskId, targetStudentId, total);
        taskStudentMapper.updateById(ts);

        String statusCode = ts.getSafetyStatus();
        String statusDesc;
        switch (statusCode) {
            case "COMPLETED":
                statusDesc = "已完成全部安全教育要求";
                break;
            case "PASSED":
                statusDesc = "已通过测试 (待签署承诺书)";
                break;
            case "PENDING_TEST":
                statusDesc = "必读规程已学完，具备测试资格";
                break;
            case "STUDYING":
                statusDesc = "规程学习与重测中";
                break;
            default:
                statusDesc = "未开始";
                break;
        }

        return SafetyStatusVO.builder()
                .taskId(taskId)
                .studentId(targetStudentId)
                .statusCode(statusCode)
                .statusDesc(statusDesc)
                .materialsTotal(total)
                .materialsRead(ts.getReadMaterialCount() != null ? ts.getReadMaterialCount() : 0)
                .readMaterialIds(ts.getReadMaterialIds())
                .studyStartTime(ts.getStudyStartTime())
                .studyCompleteTime(ts.getStudyCompleteTime())
                .examAttempts(attempts.size())
                .maxAttempts(maxAttempts)
                .highestScore(highestScore)
                .passingScore(passingScore)
                .isPassed(hasPassed ? 1 : 0)
                .isCommitmentSigned(isSigned ? 1 : 0)
                .signTime(sign != null ? sign.getSignTime() : null)
                .insuranceFileUrl(sign != null ? sign.getInsuranceFileUrl() : null)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markMaterialRead(Long taskId, Long materialId, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (!"STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅学生可标记安全资料阅读");
        }
        Long studentId = loginUser.getUserId();

        SafetyMaterialItem item = materialItemMapper.selectById(materialId);
        if (item == null || item.getIsDeleted() == 1) {
            throw new BusinessException(404, "指定安全资料不存在或已删除");
        }
        if (item.getTaskId() != null && !item.getTaskId().equals(taskId)) {
            throw new BusinessException(400, "该安全资料不属于当前实习任务");
        }

        // 强校验：学生必须被圈定在当前实习任务名单中 (TASK-003)
        InternshipTaskStudent ts = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                .eq(InternshipTaskStudent::getTaskId, taskId)
                .eq(InternshipTaskStudent::getStudentId, studentId)
                .eq(InternshipTaskStudent::getIsDeleted, 0));
        if (ts == null) {
            throw new BusinessException(403, "您未圈定在当前实习任务学生名单中，无权操作 (TASK-003)");
        }

        List<Long> readIds = new ArrayList<>();
        if (StringUtils.hasText(ts.getReadMaterialIds())) {
            try {
                readIds = objectMapper.readValue(ts.getReadMaterialIds(), new TypeReference<List<Long>>() {});
            } catch (Exception e) {
                log.warn("解析已读资料集合失败，重新初始化: {}", e.getMessage());
                readIds = new ArrayList<>();
            }
        }

        if (!readIds.contains(materialId)) {
            readIds.add(materialId);
            try {
                ts.setReadMaterialIds(objectMapper.writeValueAsString(readIds));
            } catch (Exception e) {
                ts.setReadMaterialIds("[" + materialId + "]");
            }
            ts.setReadMaterialCount(readIds.size());
            if (ts.getStudyStartTime() == null) {
                ts.setStudyStartTime(LocalDateTime.now());
            }
        }

        Long totalCount = materialItemMapper.selectCount(new LambdaQueryWrapper<SafetyMaterialItem>()
                .eq(SafetyMaterialItem::getIsDeleted, 0)
                .and(w -> w.eq(SafetyMaterialItem::getTaskId, taskId).or().isNull(SafetyMaterialItem::getTaskId)));
        int total = (totalCount != null && totalCount > 0) ? totalCount.intValue() : 0;

        if (total > 0 && ts.getReadMaterialCount() >= total && ts.getStudyCompleteTime() == null) {
            ts.setStudyCompleteTime(LocalDateTime.now());
        }

        updateTaskStudentSafetyStatus(ts, taskId, studentId, total);
        taskStudentMapper.updateById(ts);
        log.info("学生 [{}] 阅读安全资料 [{}] 成功，当前已读 {}/{} 篇，五阶段状态流转为: {}",
                studentId, materialId, ts.getReadMaterialCount(), total, ts.getSafetyStatus());
    }

    @Override
    public List<StudentSafetyProgressVO> getStudentsSafetyProgress(Long taskId, String status, String keyword, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "请先登录");
        }
        if ("STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "学生无权查看安全教育监控名单 (SAFE-008)");
        }

        Long queryTaskId = taskId;
        if (queryTaskId == null) {
            InternshipTask latest = taskMapper.selectOne(new LambdaQueryWrapper<InternshipTask>()
                    .eq(InternshipTask::getIsDeleted, 0)
                    .orderByDesc(InternshipTask::getId)
                    .last("LIMIT 1"));
            if (latest != null) {
                queryTaskId = latest.getId();
            } else {
                return new ArrayList<>();
            }
        }
        final Long finalTaskId = queryTaskId;

        Long totalCount = materialItemMapper.selectCount(new LambdaQueryWrapper<SafetyMaterialItem>()
                .eq(SafetyMaterialItem::getIsDeleted, 0)
                .and(w -> w.eq(SafetyMaterialItem::getTaskId, finalTaskId).or().isNull(SafetyMaterialItem::getTaskId)));
        int total = (totalCount != null && totalCount > 0) ? totalCount.intValue() : 0;

        LambdaQueryWrapper<InternshipTaskStudent> wrapper = new LambdaQueryWrapper<InternshipTaskStudent>()
                .eq(InternshipTaskStudent::getTaskId, finalTaskId)
                .eq(InternshipTaskStudent::getIsDeleted, 0);

        // 核心规则：指导教师仅能监控本人实际负责分配的学生 (SAFE-008)
        if ("TEACHER".equals(loginUser.getUserType())) {
            wrapper.eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId());
        }

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(InternshipTaskStudent::getStudentName, keyword)
                    .or().like(InternshipTaskStudent::getStudentNumber, keyword));
        }

        List<InternshipTaskStudent> taskStudents = taskStudentMapper.selectList(wrapper);
        List<StudentSafetyProgressVO> resultList = new ArrayList<>();

        for (InternshipTaskStudent ts : taskStudents) {
            SysUser studentUser = userMapper.selectById(ts.getStudentId());
            if (studentUser == null || studentUser.getIsDeleted() == 1) {
                continue;
            }

            // 院系数据隔离：院系负责人仅能监控本院系学生
            if ("DEPT_ADMIN".equals(loginUser.getUserType()) && loginUser.getDeptId() != null) {
                if (studentUser.getDeptId() != null && !studentUser.getDeptId().equals(loginUser.getDeptId())) {
                    continue;
                }
            }

            updateTaskStudentSafetyStatus(ts, finalTaskId, ts.getStudentId(), total);

            // 状态过滤 (ALL, INCOMPLETE, 或具体五阶段状态)
            if ("INCOMPLETE".equalsIgnoreCase(status)) {
                if ("COMPLETED".equals(ts.getSafetyStatus())) {
                    continue;
                }
            } else if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
                if (!status.equalsIgnoreCase(ts.getSafetyStatus())) {
                    continue;
                }
            }

            // 考试最高分
            List<SafetyExamAttempt> attempts = examAttemptMapper.selectList(new LambdaQueryWrapper<SafetyExamAttempt>()
                    .eq(SafetyExamAttempt::getTaskId, finalTaskId)
                    .eq(SafetyExamAttempt::getStudentId, ts.getStudentId())
                    .eq(SafetyExamAttempt::getIsDeleted, 0)
                    .orderByDesc(SafetyExamAttempt::getTotalScore));
            BigDecimal examScore = attempts.isEmpty() ? null : attempts.get(0).getTotalScore();
            boolean isPassed = attempts.stream().anyMatch(a -> a.getIsPassed() == 1);

            // 签署状态
            SafetyCommitmentSign sign = commitmentSignMapper.selectOne(new LambdaQueryWrapper<SafetyCommitmentSign>()
                    .eq(SafetyCommitmentSign::getTaskId, finalTaskId)
                    .eq(SafetyCommitmentSign::getStudentId, ts.getStudentId())
                    .eq(SafetyCommitmentSign::getIsDeleted, 0));

            String className = "未分班";
            if (studentUser.getClassId() != null) {
                BaseClass bc = classMapper.selectById(studentUser.getClassId());
                if (bc != null) {
                    className = bc.getClassName();
                }
            }
            String deptName = "未分配院系";
            if (studentUser.getDeptId() != null) {
                BaseDepartment bd = departmentMapper.selectById(studentUser.getDeptId());
                if (bd != null) {
                    deptName = bd.getDeptName();
                }
            }

            int readCount = (ts.getReadMaterialCount() != null) ? ts.getReadMaterialCount() : 0;
            String progressText = readCount + "/" + total + " (" + (total > 0 ? (readCount * 100 / total) : 0) + "%)";

            String statusText;
            String statusTag;
            switch (ts.getSafetyStatus() != null ? ts.getSafetyStatus() : "NOT_STARTED") {
                case "COMPLETED":
                    statusText = "已完成";
                    statusTag = "success";
                    break;
                case "PASSED":
                    statusText = "已通过测试 (待签署)";
                    statusTag = "success";
                    break;
                case "PENDING_TEST":
                    statusText = "待测试";
                    statusTag = "warning";
                    break;
                case "STUDYING":
                    statusText = "学习中";
                    statusTag = "primary";
                    break;
                default:
                    statusText = "未开始";
                    statusTag = "info";
                    break;
            }

            StudentSafetyProgressVO vo = StudentSafetyProgressVO.builder()
                    .studentId(ts.getStudentId())
                    .studentNumber(ts.getStudentNumber())
                    .studentName(ts.getStudentName())
                    .deptId(studentUser.getDeptId())
                    .deptName(deptName)
                    .classId(studentUser.getClassId())
                    .className(className)
                    .materialProgress(progressText)
                    .materialsRead(readCount)
                    .materialsTotal(total)
                    .studyStartTime(ts.getStudyStartTime())
                    .studyCompleteTime(ts.getStudyCompleteTime())
                    .examScore(examScore)
                    .examAttempts(attempts.size())
                    .isPassed(isPassed ? 1 : 0)
                    .isCommitmentSigned((sign != null && sign.getIsSigned() == 1) ? 1 : 0)
                    .signTime(sign != null ? sign.getSignTime() : null)
                    .insuranceFileUrl(sign != null ? sign.getInsuranceFileUrl() : null)
                    .statusCode(ts.getSafetyStatus())
                    .statusText(statusText)
                    .statusTag(statusTag)
                    .build();

            resultList.add(vo);
        }

        return resultList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RemindResultVO remindStudents(Long taskId, Long studentId, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "请先登录");
        }
        if ("STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "学生无权发起安全教育催办提醒 (SAFE-008)");
        }

        List<String> targetNames = new ArrayList<>();

        if (studentId != null) {
            InternshipTaskStudent ts = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, taskId)
                    .eq(InternshipTaskStudent::getStudentId, studentId)
                    .eq(InternshipTaskStudent::getIsDeleted, 0));
            if (ts == null) {
                throw new BusinessException(404, "该学生未圈定在当前实习任务名单中");
            }

            if ("TEACHER".equals(loginUser.getUserType())) {
                if (ts.getTeacherId() == null || !ts.getTeacherId().equals(loginUser.getUserId())) {
                    throw new BusinessException(403, "指导教师仅能催办本人实际负责分配的学生 (SAFE-008)");
                }
            } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
                SysUser stu = userMapper.selectById(studentId);
                if (stu == null || !loginUser.getDeptId().equals(stu.getDeptId())) {
                    throw new BusinessException(403, "院系负责人仅能催办本院系学生");
                }
            }
            targetNames.add(ts.getStudentName());
        } else {
            // 批量催办：按当前用户监控范围获取所有未完成学生
            List<StudentSafetyProgressVO> incompleteStudents = getStudentsSafetyProgress(taskId, "INCOMPLETE", null, loginUser);
            for (StudentSafetyProgressVO s : incompleteStudents) {
                targetNames.add(s.getStudentName());
            }
        }

        int count = targetNames.size();
        String noticeMsg = count > 0
                ? "已针对 " + String.join("、", targetNames) + " 等 " + count + " 名未完成安全准入的学生记录催办指令至系统安全审计日志 (SAFE-008)"
                : "当前任务负责学生均已完成安全教育准入，无需催办";

        try {
            SysOperationLog operLog = SysOperationLog.builder()
                    .title("安全教育一键催办 (SAFE-008 & API-123)")
                    .businessType("UPDATE")
                    .method("SafetyEducationServiceImpl.remindStudents")
                    .requestMethod("POST")
                    .operatorId(loginUser.getUserId())
                    .operatorName(loginUser.getRealName())
                    .operUrl("/api/v1/safety/remind")
                    .operIp("127.0.0.1")
                    .operParam("taskId=" + taskId + (studentId != null ? ", studentId=" + studentId : ""))
                    .jsonResult(noticeMsg)
                    .status(1)
                    .operTime(LocalDateTime.now())
                    .build();
            operationLogMapper.insert(operLog);
        } catch (Exception e) {
            log.warn("记录安全催办审计日志异常: {}", e.getMessage());
        }

        log.info("指导教师/负责人 [{}] 针对任务 [{}] 发起安全教育一键催办: {}",
                loginUser.getRealName(), taskId, noticeMsg);

        return RemindResultVO.builder()
                .taskId(taskId)
                .remindedCount(count)
                .studentNames(targetNames)
                .noticeMessage(noticeMsg)
                .build();
    }

    @Override
    public Map<String, Object> getDeptSafetyStatistics(Long taskId, Long deptId, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "请先登录");
        }
        if ("STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "学生无权查看安全教育统计数据 (SAFE-009)");
        }

        // 院系负责人仅能查看本院系统计 (SAFE-009)
        if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            if (deptId != null && !deptId.equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "院系负责人仅能查看本院系安全教育统计数据 (SAFE-009)");
            }
            deptId = loginUser.getDeptId();
        } else if ("TEACHER".equals(loginUser.getUserType())) {
            if (deptId != null && !deptId.equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "指导教师仅能查看本院系安全教育统计数据");
            }
            deptId = loginUser.getDeptId();
        }

        Long queryTaskId = taskId;
        if (queryTaskId == null) {
            InternshipTask latest = taskMapper.selectOne(new LambdaQueryWrapper<InternshipTask>()
                    .eq(InternshipTask::getIsDeleted, 0)
                    .orderByDesc(InternshipTask::getId)
                    .last("LIMIT 1"));
            if (latest != null) {
                queryTaskId = latest.getId();
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("taskId", queryTaskId);
        stats.put("deptId", deptId);

        if (queryTaskId == null) {
            stats.put("totalStudents", 0);
            stats.put("passedStudents", 0);
            stats.put("completedRate", "0.0%");
            return stats;
        }

        long totalStudents = 0L;
        long passedStudents = 0L;

        if (deptId != null) {
            List<SysUser> deptStudents = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getDeptId, deptId)
                    .eq(SysUser::getUserType, "STUDENT")
                    .eq(SysUser::getIsDeleted, 0));

            if (!deptStudents.isEmpty()) {
                List<Long> studentIds = deptStudents.stream().map(SysUser::getId).toList();

                Long totalCount = taskStudentMapper.selectCount(new LambdaQueryWrapper<InternshipTaskStudent>()
                        .eq(InternshipTaskStudent::getTaskId, queryTaskId)
                        .in(InternshipTaskStudent::getStudentId, studentIds)
                        .eq(InternshipTaskStudent::getIsDeleted, 0));
                totalStudents = (totalCount != null) ? totalCount : 0L;

                Long passedCount = taskStudentMapper.selectCount(new LambdaQueryWrapper<InternshipTaskStudent>()
                        .eq(InternshipTaskStudent::getTaskId, queryTaskId)
                        .in(InternshipTaskStudent::getStudentId, studentIds)
                        .in(InternshipTaskStudent::getSafetyStatus, "PASSED", "COMPLETED")
                        .eq(InternshipTaskStudent::getIsDeleted, 0));
                passedStudents = (passedCount != null) ? passedCount : 0L;
            }
        } else {
            Long totalCount = taskStudentMapper.selectCount(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, queryTaskId)
                    .eq(InternshipTaskStudent::getIsDeleted, 0));
            totalStudents = (totalCount != null) ? totalCount : 0L;

            Long passedCount = taskStudentMapper.selectCount(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, queryTaskId)
                    .in(InternshipTaskStudent::getSafetyStatus, "PASSED", "COMPLETED")
                    .eq(InternshipTaskStudent::getIsDeleted, 0));
            passedStudents = (passedCount != null) ? passedCount : 0L;
        }

        stats.put("totalStudents", totalStudents);
        stats.put("passedStudents", passedStudents);
        stats.put("completedRate", totalStudents > 0
                ? String.format("%.1f%%", (passedStudents * 100.0 / totalStudents))
                : "0.0%");
        return stats;
    }

    private void updateTaskStudentSafetyStatus(InternshipTaskStudent ts, Long taskId, Long studentId, int totalMaterials) {
        SafetyExamAttempt passedAttempt = examAttemptMapper.selectOne(new LambdaQueryWrapper<SafetyExamAttempt>()
                .eq(SafetyExamAttempt::getTaskId, taskId)
                .eq(SafetyExamAttempt::getStudentId, studentId)
                .eq(SafetyExamAttempt::getIsPassed, 1)
                .eq(SafetyExamAttempt::getIsDeleted, 0)
                .last("LIMIT 1"));

        SafetyCommitmentSign sign = commitmentSignMapper.selectOne(new LambdaQueryWrapper<SafetyCommitmentSign>()
                .eq(SafetyCommitmentSign::getTaskId, taskId)
                .eq(SafetyCommitmentSign::getStudentId, studentId)
                .eq(SafetyCommitmentSign::getIsSigned, 1)
                .eq(SafetyCommitmentSign::getIsDeleted, 0)
                .last("LIMIT 1"));

        int readCount = (ts.getReadMaterialCount() != null) ? ts.getReadMaterialCount() : 0;

        if (passedAttempt != null && sign != null) {
            ts.setSafetyStatus("COMPLETED");
        } else if (passedAttempt != null) {
            ts.setSafetyStatus("PASSED");
        } else if (readCount >= totalMaterials && totalMaterials > 0) {
            ts.setSafetyStatus("PENDING_TEST");
        } else if (readCount > 0) {
            ts.setSafetyStatus("STUDYING");
        } else {
            ts.setSafetyStatus("NOT_STARTED");
        }
    }
}
