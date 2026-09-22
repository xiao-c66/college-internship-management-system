package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.dto.ApplyDTO;
import com.college.internship.dto.AuditDTO;
import com.college.internship.entity.ApplyAuditHistory;
import com.college.internship.entity.BaseClass;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.BaseMajor;
import com.college.internship.entity.InternshipApply;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.ApplyAuditHistoryMapper;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.BaseMajorMapper;
import com.college.internship.mapper.InternshipApplyMapper;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IInternshipApplyService;
import com.college.internship.vo.ApplyVO;
import com.college.internship.vo.AuditHistoryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 学生实习申报与双级审核核心服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternshipApplyServiceImpl implements IInternshipApplyService {

    private final InternshipApplyMapper applyMapper;
    private final ApplyAuditHistoryMapper auditHistoryMapper;
    private final InternshipTaskMapper taskMapper;
    private final BaseDepartmentMapper departmentMapper;
    private final BaseMajorMapper majorMapper;
    private final BaseClassMapper classMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplyVO saveDraft(ApplyDTO dto, LoginUser loginUser) {
        if (!"STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅学生账号允许发起实习申报");
        }
        Long studentId = loginUser.getUserId();

        InternshipApply apply = getOrInitStudentApply(dto.getTaskId(), studentId);

        // APPLY-009 校验：若已处于终审生效状态，绝对禁止普通修改
        checkApplyNotLocked(apply);

        populateApplyFields(apply, dto, loginUser);
        apply.setApplyStatus("DRAFT");
        apply.setIsLocked(0);

        if (apply.getId() == null) {
            applyMapper.insert(apply);
        } else {
            applyMapper.updateById(apply);
        }

        return getApplyById(apply.getId(), loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplyVO submitApply(ApplyDTO dto, LoginUser loginUser) {
        if (!"STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅学生账号允许提交实习申报");
        }
        Long studentId = loginUser.getUserId();

        InternshipApply apply = getOrInitStudentApply(dto.getTaskId(), studentId);

        // APPLY-009 校验：若已处于终审生效状态，绝对禁止普通修改
        checkApplyNotLocked(apply);

        populateApplyFields(apply, dto, loginUser);
        apply.setApplyStatus("SUBMITTED"); // 提交审核，进入教师初审队列
        apply.setIsLocked(0);

        if (apply.getId() == null) {
            applyMapper.insert(apply);
        } else {
            applyMapper.updateById(apply);
        }

        log.info("学生 [{}] 成功正式提交实习任务 [{}] 申报", studentId, dto.getTaskId());
        return getApplyById(apply.getId(), loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplyVO updateApply(Long id, ApplyDTO dto, LoginUser loginUser) {
        InternshipApply apply = applyMapper.selectById(id);
        if (apply == null || apply.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习申报记录不存在");
        }

        // 仅学生本人允许修改自己的申报
        if (!apply.getStudentId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权修改其他学生的实习申报");
        }

        // APPLY-009 刚性校验：已审核通过生效后禁止直接修改主数据，重大变动必须走变更审批流程
        checkApplyNotLocked(apply);

        populateApplyFields(apply, dto, loginUser);
        applyMapper.updateById(apply);

        return getApplyById(id, loginUser);
    }

    @Override
    public ApplyVO getMyApply(Long taskId, LoginUser loginUser) {
        Long studentId = loginUser.getUserId();
        InternshipApply apply = applyMapper.selectOne(new LambdaQueryWrapper<InternshipApply>()
                .eq(InternshipApply::getTaskId, taskId)
                .eq(InternshipApply::getStudentId, studentId)
                .eq(InternshipApply::getIsDeleted, 0));

        if (apply == null) {
            return null;
        }
        return convertToVO(apply);
    }

    @Override
    public ApplyVO getApplyById(Long id, LoginUser loginUser) {
        InternshipApply apply = applyMapper.selectById(id);
        if (apply == null || apply.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习申报记录不存在");
        }
        return convertToVO(apply);
    }

    @Override
    public List<ApplyVO> listAppliesForAudit(Long taskId, String status, LoginUser loginUser) {
        LambdaQueryWrapper<InternshipApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InternshipApply::getIsDeleted, 0);

        if (taskId != null) {
            wrapper.eq(InternshipApply::getTaskId, taskId);
        }

        if ("TEACHER".equals(loginUser.getUserType())) {
            // 指导教师：本院系申报
            wrapper.eq(InternshipApply::getDeptId, loginUser.getDeptId());
            if (StringUtils.hasText(status)) {
                wrapper.eq(InternshipApply::getApplyStatus, status);
            }
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            // 院系负责人：本院系申报
            wrapper.eq(InternshipApply::getDeptId, loginUser.getDeptId());
            if (StringUtils.hasText(status)) {
                wrapper.eq(InternshipApply::getApplyStatus, status);
            }
        } else if ("STUDENT".equals(loginUser.getUserType())) {
            wrapper.eq(InternshipApply::getStudentId, loginUser.getUserId());
        }

        wrapper.orderByDesc(InternshipApply::getId);
        List<InternshipApply> list = applyMapper.selectList(wrapper);
        return list.stream().map(this::convertToVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditApply(Long id, AuditDTO dto, LoginUser loginUser) {
        String userType = loginUser.getUserType();
        if (!"TEACHER".equals(userType) && !"DEPT_ADMIN".equals(userType) && !"SYS_ADMIN".equals(userType)) {
            throw new BusinessException(403, "当前角色无权执行实习申报审核");
        }

        InternshipApply apply = applyMapper.selectById(id);
        if (apply == null || apply.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习申报记录不存在");
        }

        String action = dto.getAction().trim().toUpperCase();

        // 校验审核动作
        if (!"APPROVED".equals(action) && !"REJECTED".equals(action)) {
            throw new BusinessException(400, "审核动作无效，必须为 APPROVED 或 REJECTED");
        }

        // 退回修改时，强制校验审核意见非空且trim后不少于5个字符
        if ("REJECTED".equals(action)) {
            if (!StringUtils.hasText(dto.getOpinion()) || dto.getOpinion().trim().length() < 5) {
                throw new BusinessException(400, "审核退回原因必须填写且不得少于5个字符！");
            }
        }

        String nodeName;
        String nextStatus;

        if ("TEACHER".equals(userType)) {
            // 导师初审 (REVIEW-001)
            nodeName = "TEACHER_AUDIT";
            if ("APPROVED".equals(action)) {
                nextStatus = "TEACHER_APPROVED";
            } else {
                nextStatus = "TEACHER_REJECTED";
            }
        } else if ("DEPT_ADMIN".equals(userType) || "SYS_ADMIN".equals(userType)) {
            // 院系终审复核 (REVIEW-002)
            checkDeptScope(apply.getDeptId(), loginUser);
            nodeName = "DEPT_AUDIT";
            if ("APPROVED".equals(action)) {
                nextStatus = "APPROVED"; // 严格统一使用 APPROVED 终审生效状态 (APPLY-009)
                apply.setIsLocked(1);     // 锁定标志置为1
            } else {
                nextStatus = "DEPT_REJECTED";
            }
        } else {
            throw new BusinessException(403, "当前角色无权执行实习申报审核");
        }

        apply.setApplyStatus(nextStatus);
        applyMapper.updateById(apply);

        // 保存审核流转历史轨迹快照 (REVIEW-006 & 严禁物理删除)
        String snapshotJson = "";
        try {
            snapshotJson = objectMapper.writeValueAsString(apply);
        } catch (Exception e) {
            log.warn("序列化申报快照异常", e);
        }

        ApplyAuditHistory history = ApplyAuditHistory.builder()
                .applyId(id)
                .nodeName(nodeName)
                .auditorId(loginUser.getUserId())
                .auditorName(loginUser.getRealName())
                .auditorRole(userType)
                .auditAction(action)
                .auditOpinion(dto.getOpinion() != null ? dto.getOpinion().trim() : "")
                .snapshotData(snapshotJson)
                .auditTime(LocalDateTime.now())
                .isDeleted(0)
                .build();

        auditHistoryMapper.insert(history);
        log.info("用户 [{}] 完成申报 [{}] 的 [{}] 审核，结果: [{}]",
                loginUser.getRealName(), id, nodeName, nextStatus);
    }

    @Override
    public List<AuditHistoryVO> getAuditHistories(Long applyId, LoginUser loginUser) {
        List<ApplyAuditHistory> list = auditHistoryMapper.selectList(new LambdaQueryWrapper<ApplyAuditHistory>()
                .eq(ApplyAuditHistory::getApplyId, applyId)
                .eq(ApplyAuditHistory::getIsDeleted, 0)
                .orderByAsc(ApplyAuditHistory::getAuditTime));

        return list.stream().map(h -> AuditHistoryVO.builder()
                .id(h.getId())
                .applyId(h.getApplyId())
                .nodeName(h.getNodeName())
                .nodeDesc("TEACHER_AUDIT".equals(h.getNodeName()) ? "指导教师初审" : "院系负责人终审")
                .auditorId(h.getAuditorId())
                .auditorName(h.getAuditorName())
                .auditorRole(h.getAuditorRole())
                .auditAction(h.getAuditAction())
                .auditOpinion(h.getAuditOpinion())
                .snapshotData(h.getSnapshotData())
                .auditTime(h.getAuditTime())
                .build()
        ).toList();
    }

    private void checkApplyNotLocked(InternshipApply apply) {
        // APPLY-009: 审核通过生效后禁止直接修改主数据，重大变动必须走变更审批流程
        if ("APPROVED".equals(apply.getApplyStatus()) || Integer.valueOf(1).equals(apply.getIsLocked())) {
            throw new BusinessException(400, "实习信息已经审核生效，禁止直接修改。单位、岗位、地址、联系人和起止时间变动必须走实习变更审批流程！(APPLY-009)");
        }
    }

    private InternshipApply getOrInitStudentApply(Long taskId, Long studentId) {
        InternshipApply apply = applyMapper.selectOne(new LambdaQueryWrapper<InternshipApply>()
                .eq(InternshipApply::getTaskId, taskId)
                .eq(InternshipApply::getStudentId, studentId)
                .eq(InternshipApply::getIsDeleted, 0));

        if (apply == null) {
            SysUser user = userMapper.selectById(studentId);
            Long deptId = (user != null && user.getDeptId() != null) ? user.getDeptId() : null;
            if (deptId == null) {
                InternshipTask task = taskMapper.selectById(taskId);
                if (task != null) {
                    deptId = task.getDeptId();
                }
            }
            if (deptId == null) {
                throw new BusinessException(400, "无法确定学生所属二级院系，禁止创建实习申报 (APPLY-001)");
            }
            apply = InternshipApply.builder()
                    .taskId(taskId)
                    .studentId(studentId)
                    .studentNumber(user != null ? user.getUserNumber() : "")
                    .studentName(user != null ? user.getRealName() : "")
                    .deptId(deptId)
                    .majorId(user != null ? user.getMajorId() : null)
                    .classId(user != null ? user.getClassId() : null)
                    .isLocked(0)
                    .build();
        }
        return apply;
    }

    private void populateApplyFields(InternshipApply apply, ApplyDTO dto, LoginUser loginUser) {
        apply.setCompanyName(dto.getCompanyName());
        apply.setJobPosition(dto.getJobPosition());
        apply.setJobAddress(dto.getJobAddress());
        apply.setCompanyContactPerson(dto.getCompanyContactPerson());
        apply.setCompanyContactPhone(dto.getCompanyContactPhone());
        apply.setCompanyContactEmail(dto.getCompanyContactEmail());
        apply.setStartDate(dto.getStartDate());
        apply.setEndDate(dto.getEndDate());
        apply.setInternshipMode(dto.getInternshipMode());
        apply.setJobDuties(dto.getJobDuties());
        apply.setAgreementFileUrl(dto.getAgreementFileUrl());
    }

    private ApplyVO convertToVO(InternshipApply apply) {
        String taskName = "";
        InternshipTask task = taskMapper.selectById(apply.getTaskId());
        if (task != null) taskName = task.getTaskName();

        String deptName = "";
        if (apply.getDeptId() != null) {
            BaseDepartment dept = departmentMapper.selectById(apply.getDeptId());
            if (dept != null) deptName = dept.getDeptName();
        }

        String majorName = "";
        if (apply.getMajorId() != null) {
            BaseMajor major = majorMapper.selectById(apply.getMajorId());
            if (major != null) majorName = major.getMajorName();
        }

        String className = "";
        if (apply.getClassId() != null) {
            BaseClass bc = classMapper.selectById(apply.getClassId());
            if (bc != null) className = bc.getClassName();
        }

        boolean isLocked = "APPROVED".equals(apply.getApplyStatus()) || Integer.valueOf(1).equals(apply.getIsLocked());
        boolean canEdit = !isLocked && !"SUBMITTED".equals(apply.getApplyStatus()) && !"TEACHER_APPROVED".equals(apply.getApplyStatus());
        boolean canSubmit = canEdit;

        String statusDesc;
        switch (apply.getApplyStatus()) {
            case "DRAFT": statusDesc = "草稿待提交"; break;
            case "SUBMITTED": statusDesc = "已提交待初审"; break;
            case "TEACHER_APPROVED": statusDesc = "导师初审通过待院系复审"; break;
            case "TEACHER_REJECTED": statusDesc = "导师初审退回"; break;
            case "APPROVED": statusDesc = "院系复审通过 (已生效锁定)"; break;
            case "DEPT_REJECTED": statusDesc = "院系复审退回"; break;
            default: statusDesc = apply.getApplyStatus();
        }

        List<AuditHistoryVO> histories = getAuditHistories(apply.getId(), null);

        return ApplyVO.builder()
                .id(apply.getId())
                .taskId(apply.getTaskId())
                .taskName(taskName)
                .studentId(apply.getStudentId())
                .studentNumber(apply.getStudentNumber())
                .studentName(apply.getStudentName())
                .deptId(apply.getDeptId())
                .deptName(deptName)
                .majorId(apply.getMajorId())
                .majorName(majorName)
                .classId(apply.getClassId())
                .className(className)
                .companyName(apply.getCompanyName())
                .jobPosition(apply.getJobPosition())
                .jobAddress(apply.getJobAddress())
                .companyContactPerson(apply.getCompanyContactPerson())
                .companyContactPhone(apply.getCompanyContactPhone())
                .companyContactEmail(apply.getCompanyContactEmail())
                .startDate(apply.getStartDate())
                .endDate(apply.getEndDate())
                .internshipMode(apply.getInternshipMode())
                .jobDuties(apply.getJobDuties())
                .agreementFileUrl(apply.getAgreementFileUrl())
                .applyStatus(apply.getApplyStatus())
                .statusDesc(statusDesc)
                .isLocked(isLocked ? 1 : 0)
                .canEdit(canEdit)
                .canSubmit(canSubmit)
                .createTime(apply.getCreateTime())
                .updateTime(apply.getUpdateTime())
                .auditHistories(histories)
                .build();
    }

    private void checkDeptScope(Long deptId, LoginUser loginUser) {
        if ("DEPT_ADMIN".equals(loginUser.getUserType()) && !deptId.equals(loginUser.getDeptId())) {
            throw new BusinessException(403, "不能越权审核其他院系的实习申报");
        }
    }
}
