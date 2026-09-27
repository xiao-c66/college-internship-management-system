package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.Phase7Properties;
import com.college.internship.dto.InspectPlanCreateDTO;
import com.college.internship.dto.InspectionSubmitDTO;
import com.college.internship.dto.RectifyCreateDTO;
import com.college.internship.dto.RectifyReviewDTO;
import com.college.internship.dto.RectifySubmitDTO;
import com.college.internship.entity.BaseClass;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.BasePhase7Entity;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.entity.MidtermInspection;
import com.college.internship.entity.MidtermInspectionPlan;
import com.college.internship.entity.MidtermRectification;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.InternshipTaskStudentMapper;
import com.college.internship.mapper.MidtermInspectionMapper;
import com.college.internship.mapper.MidtermInspectionPlanMapper;
import com.college.internship.mapper.MidtermRectificationMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IArchiveService;
import com.college.internship.service.IInspectService;
import com.college.internship.vo.InspectPlanVO;
import com.college.internship.vo.InspectionVO;
import com.college.internship.vo.RectifyVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.college.internship.util.SafeUrlValidator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 中期检查与限期整改业务服务实现类 (API-074 ~ API-081)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspectServiceImpl implements IInspectService {

    private final MidtermInspectionPlanMapper planMapper;
    private final MidtermInspectionMapper inspectionMapper;
    private final MidtermRectificationMapper rectificationMapper;
    private final InternshipTaskMapper taskMapper;
    private final InternshipTaskStudentMapper taskStudentMapper;
    private final SysUserMapper userMapper;
    private final BaseClassMapper classMapper;
    private final BaseDepartmentMapper departmentMapper;
    private final Phase7Properties phase7Properties;
    @Lazy
    private final IArchiveService archiveService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInspectPlan(InspectPlanCreateDTO dto, LoginUser loginUser) {
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅院系负责人或管理端允许编制中期检查方案");
        }

        // 抽样比例强校验 (TEST-P7-04)
        if (dto.getSamplingRatio() != null) {
            if (dto.getSamplingRatio().compareTo(new BigDecimal("1.00")) < 0 || dto.getSamplingRatio().compareTo(new BigDecimal("100.00")) > 0) {
                throw new BusinessException(400, "抽样比例必须在 1.00% 至 100.00% 之间");
            }
        }
        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new BusinessException(400, "检查启动日期不能晚于截止日期");
        }

        Long deptId = dto.getDeptId() != null ? dto.getDeptId() : loginUser.getDeptId();
        MidtermInspectionPlan plan = MidtermInspectionPlan.builder()
                .planName(dto.getPlanName())
                .taskId(dto.getTaskId())
                .deptId(deptId)
                .samplingMode(StringUtils.hasText(dto.getSamplingMode()) ? dto.getSamplingMode() : "RANDOM_RATIO")
                .samplingRatio(dto.getSamplingRatio() != null ? dto.getSamplingRatio() : new BigDecimal("20.00"))
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .expertGroup(dto.getExpertGroup())
                .remark(dto.getRemark())
                .status("PUBLISHED")
                .createdBy(loginUser.getUserId())
                .build();

        planMapper.insert(plan);
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer executeSampling(Long planId, LoginUser loginUser) {
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅院系负责人或管理端允许执行中期检查抽样");
        }

        MidtermInspectionPlan plan = planMapper.selectById(planId);
        if (plan == null || plan.getIsDeleted() == 1) {
            throw new BusinessException(400, "检查方案不存在");
        }

        if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            if (plan.getDeptId() != null && !plan.getDeptId().equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "院系负责人无权跨院系执行抽样");
            }
        }

        List<InternshipTaskStudent> studentBindings = taskStudentMapper.selectList(new LambdaQueryWrapper<InternshipTaskStudent>()
                .eq(InternshipTaskStudent::getTaskId, plan.getTaskId()));
        if (studentBindings.isEmpty()) {
            return 0;
        }

        // 随机打乱以支持按比例抽样
        Collections.shuffle(studentBindings);
        int total = studentBindings.size();
        BigDecimal ratio = plan.getSamplingRatio() != null ? plan.getSamplingRatio() : new BigDecimal("20.00");
        int targetCount = Math.max(1, ratio.multiply(new BigDecimal(total)).divide(new BigDecimal("100"), 0, java.math.RoundingMode.CEILING).intValue());
        if (targetCount > total) {
            targetCount = total;
        }

        String batchNo = "SB" + System.currentTimeMillis();
        int sampledCount = 0;
        for (int i = 0; i < targetCount; i++) {
            InternshipTaskStudent binding = studentBindings.get(i);
            MidtermInspection record = MidtermInspection.builder()
                    .planId(plan.getId())
                    .taskId(plan.getTaskId())
                    .studentId(binding.getStudentId())
                    .teacherId(binding.getTeacherId())
                    .inspectorId(loginUser.getUserId())
                    .samplingBatchNo(batchNo)
                    .inspectionType("ONSITE")
                    .inspectionDate(LocalDateTime.now())
                    .status("INSPECTED")
                    .hasProblem(0)
                    .build();
            try {
                // uk_plan_student 物理级拦截同一学生重复抽取 (TEST-P7-05)
                inspectionMapper.insert(record);
                sampledCount++;
            } catch (DuplicateKeyException ex) {
                log.info("Student {} already sampled in plan {}, skipping duplicate insertion.", binding.getStudentId(), plan.getId());
            }
        }
        return sampledCount;
    }

    @Override
    public List<InspectPlanVO> getInspectPlans(Long taskId, LoginUser loginUser) {
        LambdaQueryWrapper<MidtermInspectionPlan> wrapper = new LambdaQueryWrapper<MidtermInspectionPlan>()
                .eq(BasePhase7Entity::getIsDeleted, 0)
                .orderByDesc(BasePhase7Entity::getCreatedAt);
        if (taskId != null) {
            wrapper.eq(MidtermInspectionPlan::getTaskId, taskId);
        }
        if ("DEPT_ADMIN".equals(loginUser.getUserType()) && loginUser.getDeptId() != null) {
            wrapper.eq(MidtermInspectionPlan::getDeptId, loginUser.getDeptId());
        }

        List<MidtermInspectionPlan> list = planMapper.selectList(wrapper);
        return list.stream().map(p -> {
            InternshipTask t = taskMapper.selectById(p.getTaskId());
            BaseDepartment d = departmentMapper.selectById(p.getDeptId());
            SysUser creator = userMapper.selectById(p.getCreatedBy());
            Long sampledCnt = inspectionMapper.selectCount(new LambdaQueryWrapper<MidtermInspection>()
                    .eq(MidtermInspection::getPlanId, p.getId())
                    .eq(BasePhase7Entity::getIsDeleted, 0));
            Long inspectedCnt = inspectionMapper.selectCount(new LambdaQueryWrapper<MidtermInspection>()
                    .eq(MidtermInspection::getPlanId, p.getId())
                    .ne(MidtermInspection::getStatus, "PENDING")
                    .eq(BasePhase7Entity::getIsDeleted, 0));

            return InspectPlanVO.builder()
                    .id(p.getId())
                    .planName(p.getPlanName())
                    .taskId(p.getTaskId())
                    .taskName(t != null ? t.getTaskName() : "")
                    .deptId(p.getDeptId())
                    .deptName(d != null ? d.getDeptName() : "")
                    .samplingMode(p.getSamplingMode())
                    .samplingRatio(p.getSamplingRatio())
                    .startDate(p.getStartDate())
                    .endDate(p.getEndDate())
                    .expertGroup(p.getExpertGroup())
                    .remark(p.getRemark())
                    .status(p.getStatus())
                    .createdBy(p.getCreatedBy())
                    .createdByName(creator != null ? creator.getRealName() : "")
                    .sampledStudentCount(sampledCnt.intValue())
                    .inspectedCount(inspectedCnt.intValue())
                    .createdAt(p.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitInspection(InspectionSubmitDTO dto, LoginUser loginUser) {
        MidtermInspectionPlan plan = planMapper.selectById(dto.getPlanId());
        if (plan == null) {
            throw new BusinessException(400, "检查方案不存在");
        }

        // 越权校验：指导教师只能为本人带教学生录入检查记录 (TEST-P7-06)
        InternshipTaskStudent binding = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                .eq(InternshipTaskStudent::getTaskId, plan.getTaskId())
                .eq(InternshipTaskStudent::getStudentId, dto.getStudentId()));
        if (binding == null) {
            throw new BusinessException(400, "该学生未参与该实习任务");
        }

        if ("TEACHER".equals(loginUser.getUserType())) {
            if (binding.getTeacherId() == null || !binding.getTeacherId().equals(loginUser.getUserId())) {
                throw new BusinessException(403, "无权为非负责管辖的学生录入督导检查记录");
            }
        }

        MidtermInspection record = inspectionMapper.selectOne(new LambdaQueryWrapper<MidtermInspection>()
                .eq(MidtermInspection::getPlanId, dto.getPlanId())
                .eq(MidtermInspection::getStudentId, dto.getStudentId())
                .eq(BasePhase7Entity::getIsDeleted, 0));

        boolean isNew = (record == null);
        if (isNew) {
            record = MidtermInspection.builder()
                    .planId(dto.getPlanId())
                    .taskId(plan.getTaskId())
                    .studentId(dto.getStudentId())
                    .teacherId(binding.getTeacherId())
                    .inspectorId(loginUser.getUserId())
                    .samplingBatchNo("SB_MANUAL_" + System.currentTimeMillis())
                    .build();
        }

        record.setInspectionType(StringUtils.hasText(dto.getInspectionType()) ? dto.getInspectionType() : "ONSITE");
        record.setInspectionDate(dto.getInspectionDate() != null ? dto.getInspectionDate() : LocalDateTime.now());
        record.setCompanySituation(dto.getCompanySituation());
        record.setStudentPerformance(dto.getStudentPerformance());
        record.setGuidanceFulfillment(dto.getGuidanceFulfillment());
        if (StringUtils.hasText(dto.getAttachmentUrl())) {
            SafeUrlValidator.validateUrl(dto.getAttachmentUrl());
        }
        record.setAttachmentUrl(dto.getAttachmentUrl());
        record.setHasProblem(dto.getHasProblem() != null ? dto.getHasProblem() : 0);
        record.setProblemDesc(dto.getProblemDesc());

        if (record.getHasProblem() == 1) {
            if (!StringUtils.hasText(dto.getProblemDesc())) {
                throw new BusinessException(400, "存在突出问题时必须详细填写问题明细描述");
            }
            record.setStatus("PENDING_RECTIFY");
        } else {
            record.setStatus("INSPECTED");
        }

        if (isNew) {
            inspectionMapper.insert(record);
        } else {
            inspectionMapper.updateById(record);
        }

        // 存在突出问题时自动下达整改通知
        if (record.getHasProblem() == 1) {
            MidtermRectification existingRect = rectificationMapper.selectOne(new LambdaQueryWrapper<MidtermRectification>()
                    .eq(MidtermRectification::getInspectionId, record.getId())
                    .eq(BasePhase7Entity::getIsDeleted, 0));
            if (existingRect == null) {
                MidtermRectification rect = MidtermRectification.builder()
                        .inspectionId(record.getId())
                        .taskId(plan.getTaskId())
                        .studentId(dto.getStudentId())
                        .responsibleUserId(dto.getStudentId())
                        .rectifyRequirements(dto.getProblemDesc())
                        .deadlineDate(LocalDate.now().plusDays(14))
                        .status("PENDING_SUBMIT")
                        .build();
                rectificationMapper.insert(rect);
            }
        }

        return record.getId();
    }

    @Override
    public List<InspectionVO> getInspectionList(Long planId, Long taskId, String status, LoginUser loginUser) {
        LambdaQueryWrapper<MidtermInspection> wrapper = new LambdaQueryWrapper<MidtermInspection>()
                .eq(BasePhase7Entity::getIsDeleted, 0)
                .orderByDesc(BasePhase7Entity::getCreatedAt);

        if (planId != null) wrapper.eq(MidtermInspection::getPlanId, planId);
        if (taskId != null) wrapper.eq(MidtermInspection::getTaskId, taskId);
        if (StringUtils.hasText(status)) wrapper.eq(MidtermInspection::getStatus, status);

        if ("STUDENT".equals(loginUser.getUserType())) {
            wrapper.eq(MidtermInspection::getStudentId, loginUser.getUserId());
        } else if ("TEACHER".equals(loginUser.getUserType())) {
            wrapper.eq(MidtermInspection::getTeacherId, loginUser.getUserId());
        }

        List<MidtermInspection> list = inspectionMapper.selectList(wrapper);
        return list.stream().map(this::convertToInspectionVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRectification(RectifyCreateDTO dto, LoginUser loginUser) {
        if (!"TEACHER".equals(loginUser.getUserType()) && !"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "当前角色无权下达限期整改通知");
        }

        MidtermInspection inspection = inspectionMapper.selectById(dto.getInspectionId());
        if (inspection == null || inspection.getIsDeleted() == 1) {
            throw new BusinessException(400, "关联的检查记录不存在");
        }

        if ("TEACHER".equals(loginUser.getUserType())) {
            if (inspection.getTeacherId() == null || !inspection.getTeacherId().equals(loginUser.getUserId())) {
                throw new BusinessException(403, "无权为非本人负责管辖的学生下达限期整改通知");
            }
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            InternshipTask task = taskMapper.selectById(inspection.getTaskId());
            if (task != null && task.getDeptId() != null && !task.getDeptId().equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "院系负责人无权跨院系下达整改通知");
            }
        }

        MidtermRectification rect = MidtermRectification.builder()
                .inspectionId(inspection.getId())
                .taskId(inspection.getTaskId())
                .studentId(inspection.getStudentId())
                .responsibleUserId(inspection.getStudentId())
                .rectifyRequirements(dto.getRectifyRequirements())
                .deadlineDate(dto.getDeadlineDate())
                .status("PENDING_SUBMIT")
                .build();
        rectificationMapper.insert(rect);

        inspection.setStatus("PENDING_RECTIFY");
        inspection.setHasProblem(1);
        inspectionMapper.updateById(inspection);

        return rect.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitRectification(Long id, RectifySubmitDTO dto, LoginUser loginUser) {
        MidtermRectification rect = rectificationMapper.selectById(id);
        if (rect == null || rect.getIsDeleted() == 1) {
            throw new BusinessException(400, "整改记录不存在");
        }
        if ("STUDENT".equals(loginUser.getUserType()) && !rect.getStudentId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权提交其他学生的整改反馈");
        }

        // 归档写保护拦截 (TEST-P7-21)
        archiveService.checkWriteProtection(rect.getTaskId(), rect.getStudentId());

        int minLen = phase7Properties.getInspect().getMinRectifyLength();
        if (!StringUtils.hasText(dto.getStudentExplanation()) || dto.getStudentExplanation().trim().length() < minLen) {
            throw new BusinessException(400, "整改措施说明字数不足，最低要求: " + minLen + " 字");
        }

        if (StringUtils.hasText(dto.getEvidenceAttachmentUrl())) {
            SafeUrlValidator.validateUrl(dto.getEvidenceAttachmentUrl());
        }
        rect.setEvidenceAttachmentUrl(dto.getEvidenceAttachmentUrl());
        rect.setSubmitTime(LocalDateTime.now());
        rect.setStatus("PENDING_REVIEW");
        rectificationMapper.updateById(rect);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewRectification(Long id, RectifyReviewDTO dto, LoginUser loginUser) {
        MidtermRectification rect = rectificationMapper.selectById(id);
        if (rect == null || rect.getIsDeleted() == 1) {
            throw new BusinessException(400, "整改记录不存在");
        }

        if ("TEACHER".equals(loginUser.getUserType())) {
            InternshipTaskStudent binding = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, rect.getTaskId())
                    .eq(InternshipTaskStudent::getStudentId, rect.getStudentId())
                    .eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId()));
            if (binding == null) {
                throw new BusinessException(403, "无权复核非负责学生的整改单");
            }
        }

        int minCommentLen = phase7Properties.getInspect().getMinRectifyCommentLength();
        if (!StringUtils.hasText(dto.getReviewComment()) || dto.getReviewComment().trim().length() < minCommentLen) {
            throw new BusinessException(400, "复核评价意见字数不足，最低要求: " + minCommentLen + " 字");
        }

        boolean isPassed = "PASSED".equalsIgnoreCase(dto.getAction());
        rect.setStatus(isPassed ? "PENDING_CLOSE" : "REJECTED");
        rect.setReviewTeacherId(loginUser.getUserId());
        rect.setReviewComment(dto.getReviewComment());
        rect.setReviewTime(LocalDateTime.now());
        rectificationMapper.updateById(rect);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeRectification(Long id, LoginUser loginUser) {
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅院系负责人或管理端允许销号闭环整改单");
        }

        MidtermRectification rect = rectificationMapper.selectById(id);
        if (rect == null || rect.getIsDeleted() == 1) {
            throw new BusinessException(400, "整改记录不存在");
        }

        // 闭环防跳跃强校验 (TEST-P7-08)：整改单必须经指导教师复核合格 (PENDING_CLOSE) 方可销号
        if (!"PENDING_CLOSE".equals(rect.getStatus())) {
            throw new BusinessException(400, "整改单尚未经指导教师复核合格，禁止直接销号闭环 (当前状态: " + rect.getStatus() + ")");
        }

        rect.setStatus("CLOSED");
        rect.setCloseDeptUserId(loginUser.getUserId());
        rect.setCloseTime(LocalDateTime.now());
        rectificationMapper.updateById(rect);

        // 同步恢复父级检查记录状态为 RECTIFIED
        MidtermInspection inspection = inspectionMapper.selectById(rect.getInspectionId());
        if (inspection != null) {
            inspection.setStatus("RECTIFIED");
            inspectionMapper.updateById(inspection);
        }
    }

    @Override
    public List<RectifyVO> getRectificationList(Long taskId, String status, LoginUser loginUser) {
        LambdaQueryWrapper<MidtermRectification> wrapper = new LambdaQueryWrapper<MidtermRectification>()
                .eq(BasePhase7Entity::getIsDeleted, 0)
                .orderByDesc(BasePhase7Entity::getCreatedAt);

        if (taskId != null) wrapper.eq(MidtermRectification::getTaskId, taskId);
        if (StringUtils.hasText(status)) wrapper.eq(MidtermRectification::getStatus, status);

        if ("STUDENT".equals(loginUser.getUserType())) {
            wrapper.eq(MidtermRectification::getStudentId, loginUser.getUserId());
        } else if ("TEACHER".equals(loginUser.getUserType())) {
            // 教师仅查负责学生 (缺口 12)
            List<InternshipTaskStudent> myStudents = taskStudentMapper.selectList(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId())
                    .eq(taskId != null, InternshipTaskStudent::getTaskId, taskId)
                    .eq(InternshipTaskStudent::getIsDeleted, 0));
            List<Long> studentIds = myStudents.stream().map(InternshipTaskStudent::getStudentId).toList();
            if (studentIds.isEmpty()) {
                return Collections.emptyList();
            }
            wrapper.in(MidtermRectification::getStudentId, studentIds);
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            // 院系管理员：过滤本院系任务下的整改单 (缺口 12)
            List<InternshipTask> deptTasks = taskMapper.selectList(new LambdaQueryWrapper<InternshipTask>()
                    .eq(InternshipTask::getDeptId, loginUser.getDeptId())
                    .eq(InternshipTask::getIsDeleted, 0));
            List<Long> taskIds = deptTasks.stream().map(InternshipTask::getId).toList();
            if (taskIds.isEmpty()) {
                return Collections.emptyList();
            }
            if (taskId != null) {
                if (!taskIds.contains(taskId)) {
                    return Collections.emptyList();
                }
            } else {
                wrapper.in(MidtermRectification::getTaskId, taskIds);
            }
        }

        List<MidtermRectification> list = rectificationMapper.selectList(wrapper);
        return list.stream().map(r -> {
            SysUser student = userMapper.selectById(r.getStudentId());
            SysUser teacher = r.getReviewTeacherId() != null ? userMapper.selectById(r.getReviewTeacherId()) : null;
            SysUser closeUser = r.getCloseDeptUserId() != null ? userMapper.selectById(r.getCloseDeptUserId()) : null;

            return RectifyVO.builder()
                    .id(r.getId())
                    .inspectionId(r.getInspectionId())
                    .taskId(r.getTaskId())
                    .studentId(r.getStudentId())
                    .studentName(student != null ? student.getRealName() : "")
                    .studentNo(student != null ? student.getUsername() : "")
                    .responsibleUserId(r.getResponsibleUserId())
                    .responsibleUserName(student != null ? student.getRealName() : "")
                    .rectifyRequirements(r.getRectifyRequirements())
                    .deadlineDate(r.getDeadlineDate())
                    .studentExplanation(r.getStudentExplanation())
                    .evidenceAttachmentUrl(r.getEvidenceAttachmentUrl())
                    .submitTime(r.getSubmitTime())
                    .reviewTeacherId(r.getReviewTeacherId())
                    .reviewTeacherName(teacher != null ? teacher.getRealName() : "")
                    .reviewComment(r.getReviewComment())
                    .reviewTime(r.getReviewTime())
                    .closeDeptUserId(r.getCloseDeptUserId())
                    .closeDeptUserName(closeUser != null ? closeUser.getRealName() : "")
                    .closeTime(r.getCloseTime())
                    .status(r.getStatus())
                    .createdAt(r.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    private InspectionVO convertToInspectionVO(MidtermInspection ins) {
        MidtermInspectionPlan plan = planMapper.selectById(ins.getPlanId());
        InternshipTask task = taskMapper.selectById(ins.getTaskId());
        SysUser student = userMapper.selectById(ins.getStudentId());
        SysUser teacher = ins.getTeacherId() != null ? userMapper.selectById(ins.getTeacherId()) : null;
        SysUser inspector = ins.getInspectorId() != null ? userMapper.selectById(ins.getInspectorId()) : null;

        MidtermRectification rect = rectificationMapper.selectOne(new LambdaQueryWrapper<MidtermRectification>()
                .eq(MidtermRectification::getInspectionId, ins.getId())
                .eq(BasePhase7Entity::getIsDeleted, 0));

        return InspectionVO.builder()
                .id(ins.getId())
                .planId(ins.getPlanId())
                .planName(plan != null ? plan.getPlanName() : "")
                .taskId(ins.getTaskId())
                .taskName(task != null ? task.getTaskName() : "")
                .studentId(ins.getStudentId())
                .studentName(student != null ? student.getRealName() : "")
                .studentNo(student != null ? student.getUsername() : "")
                .teacherId(ins.getTeacherId())
                .teacherName(teacher != null ? teacher.getRealName() : "")
                .inspectorId(ins.getInspectorId())
                .inspectorName(inspector != null ? inspector.getRealName() : "")
                .samplingBatchNo(ins.getSamplingBatchNo())
                .inspectionType(ins.getInspectionType())
                .inspectionDate(ins.getInspectionDate())
                .companySituation(ins.getCompanySituation())
                .studentPerformance(ins.getStudentPerformance())
                .guidanceFulfillment(ins.getGuidanceFulfillment())
                .score(ins.getScore())
                .attachmentUrl(ins.getAttachmentUrl())
                .hasProblem(ins.getHasProblem())
                .problemDesc(ins.getProblemDesc())
                .status(ins.getStatus())
                .rectificationId(rect != null ? rect.getId() : null)
                .rectificationStatus(rect != null ? rect.getStatus() : null)
                .createdAt(ins.getCreatedAt())
                .build();
    }
}
