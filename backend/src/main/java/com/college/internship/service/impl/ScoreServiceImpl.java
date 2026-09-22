package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.Phase7Properties;
import com.college.internship.dto.ScoreAppealDTO;
import com.college.internship.dto.ScoreArbitrateDTO;
import com.college.internship.dto.ScoreSubmitDTO;
import com.college.internship.entity.BaseClass;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.BaseMajor;
import com.college.internship.entity.BasePhase7Entity;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.entity.ScoreAuditHistory;
import com.college.internship.entity.ScoreSummary;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.BaseMajorMapper;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.InternshipTaskStudentMapper;
import com.college.internship.mapper.ScoreAuditHistoryMapper;
import com.college.internship.mapper.ScoreSummaryMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IArchiveService;
import com.college.internship.service.IScoreService;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.vo.ScoreAuditHistoryVO;
import com.college.internship.vo.ScoreSummaryVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.college.internship.util.SafeUrlValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 实习成绩五维综合评定与异议申诉业务服务实现类 (API-091 ~ API-097)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreServiceImpl implements IScoreService {

    private final ScoreSummaryMapper scoreMapper;
    private final ScoreAuditHistoryMapper historyMapper;
    private final InternshipTaskMapper taskMapper;
    private final InternshipTaskStudentMapper taskStudentMapper;
    private final SysUserMapper userMapper;
    private final BaseDepartmentMapper departmentMapper;
    private final BaseMajorMapper majorMapper;
    private final BaseClassMapper classMapper;
    private final ISysOperationLogService logService;
    private final Phase7Properties phase7Properties;
    private final ObjectMapper objectMapper;
    @Lazy
    private final IArchiveService archiveService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitScore(ScoreSubmitDTO dto, LoginUser loginUser) {
        if (!"TEACHER".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅指导教师允许录入或汇算五维成绩");
        }

        // 1. 全局归档写保护拦截 (TEST-P7-21)
        archiveService.checkWriteProtection(dto.getTaskId(), dto.getStudentId());

        // 2. 指导教师管辖范围校验
        InternshipTaskStudent binding = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                .eq(InternshipTaskStudent::getTaskId, dto.getTaskId())
                .eq(InternshipTaskStudent::getStudentId, dto.getStudentId()));
        if (binding == null) {
            throw new BusinessException(400, "该学生未在该实习任务圈定名单中");
        }
        if ("TEACHER".equals(loginUser.getUserType()) && (binding.getTeacherId() == null || !binding.getTeacherId().equals(loginUser.getUserId()))) {
            throw new BusinessException(403, "无权为非负责管辖的学生录入成绩");
        }

        // 3. 任务存在性与五项权重 100% 校验 (TEST-P7-15)
        InternshipTask task = taskMapper.selectById(dto.getTaskId());
        if (task == null) {
            throw new BusinessException(400, "实习任务不存在");
        }
        BigDecimal totalWeight = (task.getWeightEnterprise() != null ? task.getWeightEnterprise() : BigDecimal.ZERO)
                .add(task.getWeightTeacherProcess() != null ? task.getWeightTeacherProcess() : BigDecimal.ZERO)
                .add(task.getWeightWeeklyReport() != null ? task.getWeightWeeklyReport() : BigDecimal.ZERO)
                .add(task.getWeightStageMaterial() != null ? task.getWeightStageMaterial() : BigDecimal.ZERO)
                .add(task.getWeightSummary() != null ? task.getWeightSummary() : BigDecimal.ZERO);
        if (totalWeight.compareTo(new BigDecimal("100.00")) != 0) {
            throw new BusinessException(400, "实习任务五项权重总和必须严格等于 100.00% (当前合计: " + totalWeight + "%)");
        }

        // 4. 杜绝0分掩盖与分项 NULL 校验 (TEST-P7-16)
        if (dto.getEnterpriseScore() == null || dto.getProcessScore() == null || dto.getWeeklyScore() == null
                || dto.getMaterialScore() == null || dto.getSummaryScore() == null) {
            throw new BusinessException(400, "五维成绩分项存在未录入项 (NULL)，禁止完成汇算或提交院系审核");
        }

        // 5. 严格五维加权折算数学公式 (SCORE-012)
        BigDecimal sumWeighted = task.getWeightEnterprise().multiply(dto.getEnterpriseScore())
                .add(task.getWeightTeacherProcess().multiply(dto.getProcessScore()))
                .add(task.getWeightWeeklyReport().multiply(dto.getWeeklyScore()))
                .add(task.getWeightStageMaterial().multiply(dto.getMaterialScore()))
                .add(task.getWeightSummary().multiply(dto.getSummaryScore()));
        BigDecimal finalScore = sumWeighted.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        // 6. SCORE-012 任务级规则优先级与快照固化 (TEST-P7-17)
        GradeRuleSet ruleSet = resolveGradeRules(task);
        String scoreLevel = calculateScoreLevel(finalScore, ruleSet);
        String snapshotJson = buildRuleSnapshotJson(task, ruleSet);

        // 7. 查询或创建 ScoreSummary
        ScoreSummary score = scoreMapper.selectOne(new LambdaQueryWrapper<ScoreSummary>()
                .eq(ScoreSummary::getTaskId, dto.getTaskId())
                .eq(ScoreSummary::getStudentId, dto.getStudentId())
                .eq(BasePhase7Entity::getIsDeleted, 0));

        boolean isNew = (score == null);
        if (isNew) {
            score = ScoreSummary.builder()
                    .taskId(dto.getTaskId())
                    .studentId(dto.getStudentId())
                    .teacherId(loginUser.getUserId())
                    .deptId(task.getDeptId())
                    .version(1)
                    .build();
        } else {
            // 已发布的成绩教师不可直接覆写
            if ("PUBLISHED".equals(score.getStatus())) {
                throw new BusinessException(400, "成绩已正式发布锁定，教师端禁止直接修改。如有异议请走调分审批流程。");
            }
        }

        if (StringUtils.hasText(dto.getEnterpriseEvaluationUrl())) {
            SafeUrlValidator.validateUrl(dto.getEnterpriseEvaluationUrl());
        }

        score.setEnterpriseScore(dto.getEnterpriseScore());
        score.setProcessScore(dto.getProcessScore());
        score.setWeeklyScore(dto.getWeeklyScore());
        score.setMaterialScore(dto.getMaterialScore());
        score.setSummaryScore(dto.getSummaryScore());
        score.setFinalScore(finalScore);
        score.setScoreLevel(scoreLevel);
        score.setGradeRuleSnapshotJson(snapshotJson);
        score.setEvaluationComment(dto.getEvaluationComment());
        score.setEnterpriseEvaluationUrl(dto.getEnterpriseEvaluationUrl());

        if (Boolean.TRUE.equals(dto.getSubmitToDept())) {
            score.setStatus("PENDING_AUDIT");
            score.setConfirmedTeacherTime(LocalDateTime.now());
        } else {
            score.setStatus("DRAFT");
        }

        if (isNew) {
            scoreMapper.insert(score);
        } else {
            scoreMapper.updateById(score);
        }

        return score.getId();
    }

    @Override
    public List<ScoreSummaryVO> getScoreList(Long taskId, Long deptId, Long majorId, Long classId, String scoreLevel, String status, LoginUser loginUser) {
        LambdaQueryWrapper<ScoreSummary> wrapper = new LambdaQueryWrapper<ScoreSummary>()
                .eq(BasePhase7Entity::getIsDeleted, 0)
                .orderByDesc(BasePhase7Entity::getCreatedAt);

        if (taskId != null) wrapper.eq(ScoreSummary::getTaskId, taskId);
        if (deptId != null) wrapper.eq(ScoreSummary::getDeptId, deptId);
        if (StringUtils.hasText(scoreLevel)) wrapper.eq(ScoreSummary::getScoreLevel, scoreLevel);
        if (StringUtils.hasText(status)) wrapper.eq(ScoreSummary::getStatus, status);

        if ("TEACHER".equals(loginUser.getUserType())) {
            wrapper.eq(ScoreSummary::getTeacherId, loginUser.getUserId());
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType()) && loginUser.getDeptId() != null) {
            wrapper.eq(ScoreSummary::getDeptId, loginUser.getDeptId());
        }

        List<ScoreSummary> list = scoreMapper.selectList(wrapper);
        return list.stream().map(this::convertToScoreVO).collect(Collectors.toList());
    }

    @Override
    public ScoreSummaryVO getScoreDetail(Long id, LoginUser loginUser) {
        ScoreSummary score = scoreMapper.selectById(id);
        if (score == null || score.getIsDeleted() == 1) {
            throw new BusinessException(400, "成绩记录不存在");
        }

        // 学生端访问隔离：非本人 403；未进入公示或发布期 403
        if ("STUDENT".equals(loginUser.getUserType())) {
            if (!score.getStudentId().equals(loginUser.getUserId())) {
                throw new BusinessException(403, "无权查看其他学生的成绩");
            }
            if (!"PUBLICITY".equals(score.getStatus()) && !"PUBLISHED".equals(score.getStatus())) {
                throw new BusinessException(403, "成绩尚未进入公示或正式发布阶段，学生端暂不可见");
            }
        }

        ScoreSummaryVO vo = convertToScoreVO(score);

        // 加载申诉与调分历史
        List<ScoreAuditHistory> histories = historyMapper.selectList(new LambdaQueryWrapper<ScoreAuditHistory>()
                .eq(ScoreAuditHistory::getScoreId, id)
                .orderByAsc(ScoreAuditHistory::getOperateTime));
        vo.setAuditHistory(histories.stream().map(h -> ScoreAuditHistoryVO.builder()
                .id(h.getId())
                .scoreId(h.getScoreId())
                .taskId(h.getTaskId())
                .studentId(h.getStudentId())
                .action(h.getAction())
                .appealReason(h.getAppealReason())
                .appealAttachmentUrl(h.getAppealAttachmentUrl())
                .oldScoreSnapshot(h.getOldScoreSnapshot())
                .newScoreSnapshot(h.getNewScoreSnapshot())
                .auditUserId(h.getAuditUserId())
                .auditUserName(h.getAuditUserName())
                .auditComment(h.getAuditComment())
                .approvalDocNo(h.getApprovalDocNo())
                .operateTime(h.getOperateTime())
                .build()).collect(Collectors.toList()));

        return vo;
    }

    @Override
    public ScoreSummaryVO getStudentScore(Long taskId, Long studentId, LoginUser loginUser) {
        ScoreSummary score = scoreMapper.selectOne(new LambdaQueryWrapper<ScoreSummary>()
                .eq(ScoreSummary::getTaskId, taskId)
                .eq(ScoreSummary::getStudentId, studentId)
                .eq(BasePhase7Entity::getIsDeleted, 0));
        if (score == null) {
            return null;
        }
        return getScoreDetail(score.getId(), loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditScore(Long id, LoginUser loginUser) {
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅院系负责人或管理端允许复核成绩");
        }

        ScoreSummary score = scoreMapper.selectById(id);
        if (score == null || score.getIsDeleted() == 1) {
            throw new BusinessException(400, "成绩记录不存在");
        }

        score.setStatus("PENDING_PUBLICITY");
        score.setAuditedDeptUserId(loginUser.getUserId());
        score.setAuditedDeptTime(LocalDateTime.now());
        scoreMapper.updateById(score);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishScores(Long taskId, LoginUser loginUser) {
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅院系负责人或管理端允许批量发布成绩公示");
        }

        List<ScoreSummary> scoreList = scoreMapper.selectList(new LambdaQueryWrapper<ScoreSummary>()
                .eq(ScoreSummary::getTaskId, taskId)
                .in(ScoreSummary::getStatus, List.of("PENDING_AUDIT", "PENDING_PUBLICITY", "DRAFT"))
                .eq(BasePhase7Entity::getIsDeleted, 0));

        LocalDateTime now = LocalDateTime.now();
        int publicityDays = phase7Properties.getScore().getDefaultPublicityDays();
        LocalDateTime endTime = now.plusDays(publicityDays);

        for (ScoreSummary score : scoreList) {
            score.setStatus("PUBLICITY");
            score.setPublicityStartTime(now);
            score.setPublicityEndTime(endTime);
            score.setAuditedDeptUserId(loginUser.getUserId());
            score.setAuditedDeptTime(now);
            scoreMapper.updateById(score);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitAppeal(ScoreAppealDTO dto, LoginUser loginUser) {
        if (!"STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅学生端允许提交成绩异议申诉");
        }

        ScoreSummary score = scoreMapper.selectById(dto.getScoreId());
        if (score == null || score.getIsDeleted() == 1) {
            throw new BusinessException(400, "申诉对应的成绩不存在");
        }

        if (!score.getStudentId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权申诉其他学生的成绩");
        }

        // 仅在公示期允许申诉 (TEST-P7-19)
        if (!"PUBLICITY".equals(score.getStatus())) {
            throw new BusinessException(400, "当前成绩不在公示期内，禁止提交申诉 (当前状态: " + score.getStatus() + ")");
        }

        int minReasonLen = phase7Properties.getScore().getMinAppealReasonLength();
        if (!StringUtils.hasText(dto.getAppealReason()) || dto.getAppealReason().trim().length() < minReasonLen) {
            throw new BusinessException(400, "申诉理由字数不足，最低要求: " + minReasonLen + " 字");
        }

        if (StringUtils.hasText(dto.getAppealAttachmentUrl())) {
            SafeUrlValidator.validateUrl(dto.getAppealAttachmentUrl());
        }

        String oldSnapshot = toJson(score);
        ScoreAuditHistory history = ScoreAuditHistory.builder()
                .scoreId(score.getId())
                .taskId(score.getTaskId())
                .studentId(score.getStudentId())
                .action("APPEAL_APPLY")
                .appealReason(dto.getAppealReason())
                .appealAttachmentUrl(dto.getAppealAttachmentUrl())
                .oldScoreSnapshot(oldSnapshot)
                .auditUserId(loginUser.getUserId())
                .auditUserName(loginUser.getRealName())
                .auditComment("学生在公示期提交异议申诉")
                .operateTime(LocalDateTime.now())
                .build();
        historyMapper.insert(history);

        return history.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void arbitrateAppeal(Long id, ScoreArbitrateDTO dto, LoginUser loginUser) {
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅院系负责人或管理端允许裁决成绩申诉");
        }

        ScoreAuditHistory appealHistory = historyMapper.selectById(id);
        if (appealHistory == null) {
            throw new BusinessException(400, "申诉记录不存在");
        }

        ScoreSummary score = scoreMapper.selectById(appealHistory.getScoreId());
        if (score == null || score.getIsDeleted() == 1) {
            throw new BusinessException(400, "成绩主记录不存在");
        }

        boolean isPass = "PASS".equalsIgnoreCase(dto.getAction());
        if (isPass) {
            // 调分必须填写线下红头批文备案号 (TEST-P7-19)
            if (!StringUtils.hasText(dto.getApprovalDocNo())) {
                throw new BusinessException(400, "线下调分必须填写红头批文备案号");
            }
            if (!StringUtils.hasText(dto.getAuditComment())) {
                throw new BusinessException(400, "必须填写调分依据说明");
            }

            InternshipTask task = taskMapper.selectById(score.getTaskId());

            // 若提供了新分项，则更新分项；未提供则沿用原分
            if (dto.getEnterpriseScore() != null) score.setEnterpriseScore(dto.getEnterpriseScore());
            if (dto.getProcessScore() != null) score.setProcessScore(dto.getProcessScore());
            if (dto.getWeeklyScore() != null) score.setWeeklyScore(dto.getWeeklyScore());
            if (dto.getMaterialScore() != null) score.setMaterialScore(dto.getMaterialScore());
            if (dto.getSummaryScore() != null) score.setSummaryScore(dto.getSummaryScore());

            // 依据已固化的规则快照重新折算最终成绩与等第，防止规则漂移 (TEST-P7-18)
            BigDecimal sumWeighted = task.getWeightEnterprise().multiply(score.getEnterpriseScore())
                    .add(task.getWeightTeacherProcess().multiply(score.getProcessScore()))
                    .add(task.getWeightWeeklyReport().multiply(score.getWeeklyScore()))
                    .add(task.getWeightStageMaterial().multiply(score.getMaterialScore()))
                    .add(task.getWeightSummary().multiply(score.getSummaryScore()));
            BigDecimal finalScore = sumWeighted.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            score.setFinalScore(finalScore);

            GradeRuleSet ruleSet = parseSnapshotRules(score.getGradeRuleSnapshotJson());
            score.setScoreLevel(calculateScoreLevel(finalScore, ruleSet));
            scoreMapper.updateById(score);

            String newSnapshot = toJson(score);
            ScoreAuditHistory passHist = ScoreAuditHistory.builder()
                    .scoreId(score.getId())
                    .taskId(score.getTaskId())
                    .studentId(score.getStudentId())
                    .action("APPEAL_PASS")
                    .oldScoreSnapshot(appealHistory.getOldScoreSnapshot())
                    .newScoreSnapshot(newSnapshot)
                    .auditUserId(loginUser.getUserId())
                    .auditUserName(loginUser.getRealName())
                    .auditComment(dto.getAuditComment())
                    .approvalDocNo(dto.getApprovalDocNo())
                    .operateTime(LocalDateTime.now())
                    .build();
            historyMapper.insert(passHist);

            // 写入 sys_operation_log 审计留痕 (TEST-P7-19)
            logService.logOperation("成绩异议申诉裁决调分", "UPDATE", "arbitrateAppeal", "POST",
                    loginUser.getUserId(), loginUser.getRealName(), "/api/v1/score/appeals/" + id + "/arbitrate",
                    "127.0.0.1", "docNo=" + dto.getApprovalDocNo() + ", scoreId=" + score.getId(),
                    newSnapshot, 1, null);
        } else {
            // 驳回申诉
            ScoreAuditHistory rejectHist = ScoreAuditHistory.builder()
                    .scoreId(score.getId())
                    .taskId(score.getTaskId())
                    .studentId(score.getStudentId())
                    .action("APPEAL_REJECT")
                    .oldScoreSnapshot(appealHistory.getOldScoreSnapshot())
                    .auditUserId(loginUser.getUserId())
                    .auditUserName(loginUser.getRealName())
                    .auditComment(StringUtils.hasText(dto.getAuditComment()) ? dto.getAuditComment() : "经核查维持原评定成绩")
                    .operateTime(LocalDateTime.now())
                    .build();
            historyMapper.insert(rejectHist);

            logService.logOperation("成绩异议申诉驳回", "UPDATE", "arbitrateAppeal", "POST",
                    loginUser.getUserId(), loginUser.getRealName(), "/api/v1/score/appeals/" + id + "/arbitrate",
                    "127.0.0.1", "scoreId=" + score.getId(),
                    "REJECTED", 1, null);
        }
    }

    private GradeRuleSet resolveGradeRules(InternshipTask task) {
        if (StringUtils.hasText(task.getGradeRulesJson())) {
            try {
                JsonNode node = objectMapper.readTree(task.getGradeRulesJson());
                return new GradeRuleSet(
                        new BigDecimal(node.path("excellentMin").asText("90.00")),
                        new BigDecimal(node.path("goodMin").asText("80.00")),
                        new BigDecimal(node.path("mediumMin").asText("70.00")),
                        new BigDecimal(node.path("passMin").asText("60.00"))
                );
            } catch (Exception e) {
                log.warn("Failed to parse task grade_rules_json, fallback to global: {}", e.getMessage());
            }
        }
        var g = phase7Properties.getScore().getGradeRules();
        return new GradeRuleSet(g.getExcellentMin(), g.getGoodMin(), g.getMediumMin(), g.getPassMin());
    }

    private String formatRule(BigDecimal val) {
        if (val == null) return "0.00";
        return val.setScale(2, RoundingMode.HALF_UP).toString();
    }

    private String buildRuleSnapshotJson(InternshipTask task, GradeRuleSet ruleSet) {
        boolean isTaskCustom = StringUtils.hasText(task.getGradeRulesJson());
        return "{\"source\":\"" + (isTaskCustom ? "TASK_CUSTOM" : "GLOBAL_DEFAULT") + "\",\"rules\":{"
                + "\"excellentMin\":" + formatRule(ruleSet.excellentMin) + ","
                + "\"goodMin\":" + formatRule(ruleSet.goodMin) + ","
                + "\"mediumMin\":" + formatRule(ruleSet.mediumMin) + ","
                + "\"passMin\":" + formatRule(ruleSet.passMin) + "}}";
    }

    private GradeRuleSet parseSnapshotRules(String snapshotJson) {
        if (StringUtils.hasText(snapshotJson)) {
            try {
                JsonNode node = objectMapper.readTree(snapshotJson).path("rules");
                return new GradeRuleSet(
                        new BigDecimal(node.path("excellentMin").asText("90.00")),
                        new BigDecimal(node.path("goodMin").asText("80.00")),
                        new BigDecimal(node.path("mediumMin").asText("70.00")),
                        new BigDecimal(node.path("passMin").asText("60.00"))
                );
            } catch (Exception e) {
                log.warn("Failed to parse snapshot rules, fallback to global: {}", e.getMessage());
            }
        }
        var g = phase7Properties.getScore().getGradeRules();
        return new GradeRuleSet(g.getExcellentMin(), g.getGoodMin(), g.getMediumMin(), g.getPassMin());
    }

    private String calculateScoreLevel(BigDecimal score, GradeRuleSet rules) {
        if (score == null) return null;
        if (score.compareTo(rules.excellentMin) >= 0) return "EXCELLENT";
        if (score.compareTo(rules.goodMin) >= 0) return "GOOD";
        if (score.compareTo(rules.mediumMin) >= 0) return "MEDIUM";
        if (score.compareTo(rules.passMin) >= 0) return "PASS";
        return "FAIL";
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private ScoreSummaryVO convertToScoreVO(ScoreSummary s) {
        InternshipTask task = taskMapper.selectById(s.getTaskId());
        SysUser student = userMapper.selectById(s.getStudentId());
        SysUser teacher = s.getTeacherId() != null ? userMapper.selectById(s.getTeacherId()) : null;
        BaseDepartment dept = departmentMapper.selectById(s.getDeptId());
        SysUser auditUser = s.getAuditedDeptUserId() != null ? userMapper.selectById(s.getAuditedDeptUserId()) : null;

        return ScoreSummaryVO.builder()
                .id(s.getId())
                .taskId(s.getTaskId())
                .taskName(task != null ? task.getTaskName() : "")
                .studentId(s.getStudentId())
                .studentName(student != null ? student.getRealName() : "")
                .studentNo(student != null ? student.getUsername() : "")
                .teacherId(s.getTeacherId())
                .teacherName(teacher != null ? teacher.getRealName() : "")
                .deptId(s.getDeptId())
                .deptName(dept != null ? dept.getDeptName() : "")
                .enterpriseScore(s.getEnterpriseScore())
                .processScore(s.getProcessScore())
                .weeklyScore(s.getWeeklyScore())
                .materialScore(s.getMaterialScore())
                .summaryScore(s.getSummaryScore())
                .finalScore(s.getFinalScore())
                .scoreLevel(s.getScoreLevel())
                .gradeRuleSnapshotJson(s.getGradeRuleSnapshotJson())
                .evaluationComment(s.getEvaluationComment())
                .enterpriseEvaluationUrl(s.getEnterpriseEvaluationUrl())
                .status(s.getStatus())
                .publicityStartTime(s.getPublicityStartTime())
                .publicityEndTime(s.getPublicityEndTime())
                .confirmedTeacherTime(s.getConfirmedTeacherTime())
                .auditedDeptUserId(s.getAuditedDeptUserId())
                .auditedDeptUserName(auditUser != null ? auditUser.getRealName() : "")
                .auditedDeptTime(s.getAuditedDeptTime())
                .version(s.getVersion())
                .createdAt(s.getCreatedAt())
                .build();
    }

    private static record GradeRuleSet(BigDecimal excellentMin, BigDecimal goodMin, BigDecimal mediumMin, BigDecimal passMin) {}
}
