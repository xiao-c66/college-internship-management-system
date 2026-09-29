package com.college.internship.service.impl;

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
import com.college.internship.service.IInternshipApplyChangeService;
import com.college.internship.vo.ApplyChangeHistoryVO;
import com.college.internship.vo.ApplyChangeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 实习重大信息变更申请与双级审批服务实现类
 * 闭环承接 APPLY-009 规则下的重大信息合规流转
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternshipApplyChangeServiceImpl implements IInternshipApplyChangeService {

    private final InternshipApplyMapper applyMapper;
    private final InternshipApplyChangeMapper changeMapper;
    private final InternshipApplyChangeHistoryMapper changeHistoryMapper;
    private final ApplyAuditHistoryMapper applyAuditHistoryMapper;
    private final InternshipTaskStudentMapper taskStudentMapper;
    private final SysUserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplyChangeVO submitChange(ApplyChangeDTO dto, LoginUser loginUser) {
        log.info("【实习重大变更】学生发起变更申请, applyId={}, userId={}", dto.getApplyId(), loginUser.getUserId());

        // 1. 权限校验：仅限学生角色 (或超管)
        if (!"STUDENT".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅学生本人可发起实习重大信息变更申请");
        }

        // 2. 查验原实习申报记录
        InternshipApply originalApply = applyMapper.selectById(dto.getApplyId());
        if (originalApply == null || originalApply.getIsDeleted() == 1) {
            throw new BusinessException(404, "原实习申报记录不存在");
        }

        // 数据归属权校验：非本人不能提交变更
        if ("STUDENT".equals(loginUser.getUserType()) && !originalApply.getStudentId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "只能为本人的实习申报申请变更");
        }

        // 3. APPLY-009 状态前置强核验：只有 APPROVED 且 isLocked == 1 的主数据才能发起变更
        if (!"APPROVED".equals(originalApply.getApplyStatus()) || originalApply.getIsLocked() != 1) {
            throw new BusinessException(400, "原实习申报尚未终审通过并锁定(APPROVED)，可直接在草稿或退回状态下修改，无需发起重大变更申请");
        }

        // 4. 并发与重复提交防护：同一原申请只能存在一个处理中的变更单 (PENDING_TEACHER / PENDING_DEPT)
        Long pendingCount = changeMapper.selectCount(new LambdaQueryWrapper<InternshipApplyChange>()
                .eq(InternshipApplyChange::getApplyId, originalApply.getId())
                .in(InternshipApplyChange::getChangeStatus, List.of("PENDING_TEACHER", "PENDING_DEPT"))
                .eq(InternshipApplyChange::getIsDeleted, 0));
        if (pendingCount != null && pendingCount > 0) {
            throw new BusinessException(400, "该实习申报已有正在审批中的重大变更申请，禁止重复提交！");
        }

        // 5. 获取负责该学生的指导教师信息
        Long teacherId = null;
        String teacherName = null;
        InternshipTaskStudent taskStudent = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                .eq(InternshipTaskStudent::getTaskId, originalApply.getTaskId())
                .eq(InternshipTaskStudent::getStudentId, originalApply.getStudentId())
                .eq(InternshipTaskStudent::getIsDeleted, 0));
        if (taskStudent != null && taskStudent.getTeacherId() != null) {
            teacherId = taskStudent.getTeacherId();
            SysUser teacherUser = userMapper.selectById(teacherId);
            if (teacherUser != null) {
                teacherName = teacherUser.getRealName();
            }
        }

        // 6. 服务端只读快照原数据并组装新变更单
        InternshipApplyChange change = InternshipApplyChange.builder()
                .applyId(originalApply.getId())
                .taskId(originalApply.getTaskId())
                .studentId(originalApply.getStudentId())
                .studentNumber(originalApply.getStudentNumber())
                .studentName(originalApply.getStudentName())
                .deptId(originalApply.getDeptId())
                .teacherId(teacherId)
                .teacherName(teacherName)
                // 原信息快照
                .origCompanyName(originalApply.getCompanyName())
                .origJobPosition(originalApply.getJobPosition())
                .origJobAddress(originalApply.getJobAddress())
                .origContactPerson(originalApply.getCompanyContactPerson())
                .origContactPhone(originalApply.getCompanyContactPhone())
                .origContactEmail(originalApply.getCompanyContactEmail())
                .origStartDate(originalApply.getStartDate())
                .origEndDate(originalApply.getEndDate())
                .origInternshipMode(originalApply.getInternshipMode())
                .origJobDuties(originalApply.getJobDuties())
                .origAgreementFileUrl(originalApply.getAgreementFileUrl())
                // 提交的新信息
                .newCompanyName(dto.getNewCompanyName().trim())
                .newJobPosition(dto.getNewJobPosition().trim())
                .newJobAddress(dto.getNewJobAddress().trim())
                .newContactPerson(dto.getNewContactPerson().trim())
                .newContactPhone(dto.getNewContactPhone().trim())
                .newContactEmail(StringUtils.hasText(dto.getNewContactEmail()) ? dto.getNewContactEmail().trim() : null)
                .newStartDate(dto.getNewStartDate())
                .newEndDate(dto.getNewEndDate())
                .newInternshipMode(dto.getNewInternshipMode())
                .newJobDuties(dto.getNewJobDuties())
                .newAgreementFileUrl(dto.getNewAgreementFileUrl())
                // 变更事由与佐证
                .changeReason(dto.getChangeReason().trim())
                .proofFileUrl(dto.getProofFileUrl())
                // 初始状态
                .changeStatus("PENDING_TEACHER")
                .currentStep("TEACHER_INITIAL")
                .isDeleted(0)
                .build();

        changeMapper.insert(change);

        // 7. 写入初次提交历史轨迹
        InternshipApplyChangeHistory history = InternshipApplyChangeHistory.builder()
                .changeId(change.getId())
                .nodeName("SUBMIT")
                .operatorId(loginUser.getUserId())
                .operatorName(loginUser.getRealName())
                .operatorRole("STUDENT")
                .auditAction("SUBMIT")
                .auditOpinion("学生提交实习重大信息变更申请（事由：" + dto.getChangeReason().trim() + "）")
                .snapshotStatus("PENDING_TEACHER")
                .isDeleted(0)
                .build();
        changeHistoryMapper.insert(history);

        log.info("【实习重大变更】变更申请已持久化入库, changeId={}, studentId={}", change.getId(), loginUser.getUserId());
        return convertToVO(change, Collections.singletonList(history));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplyChangeVO teacherInitialAudit(Long changeId, ApplyChangeAuditDTO dto, LoginUser loginUser) {
        log.info("【实习重大变更】指导教师初审, changeId={}, auditorId={}, action={}", changeId, loginUser.getUserId(), dto.getAuditAction());

        // 1. 角色权限拦截
        if (!"TEACHER".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅指导教师或系统管理员有权执行初审");
        }

        // 2. 查验变更单
        InternshipApplyChange change = changeMapper.selectById(changeId);
        if (change == null || change.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习变更申请不存在");
        }

        // 3. 状态校验：必须处于 PENDING_TEACHER
        if (!"PENDING_TEACHER".equals(change.getChangeStatus())) {
            throw new BusinessException(400, "当前变更申请状态不处于待教师初审阶段 (当前状态: " + change.getChangeStatus() + ")");
        }

        // 4. 数据管辖权校验：教师只能初审当前负责的学生
        if ("TEACHER".equals(loginUser.getUserType())) {
            if (change.getTeacherId() != null && !change.getTeacherId().equals(loginUser.getUserId())) {
                throw new BusinessException(403, "您无权审核非本人负责学生的实习变更申请");
            }
        }

        // 5. 状态机驱动流转
        String nextStatus;
        String nextStep;
        if ("APPROVE".equals(dto.getAuditAction())) {
            nextStatus = "PENDING_DEPT";
            nextStep = "DEPT_FINAL";
        } else if ("REJECT".equals(dto.getAuditAction())) {
            nextStatus = "REJECTED";
            nextStep = "FINISHED";
        } else {
            throw new BusinessException(400, "不支持的审核动作: " + dto.getAuditAction());
        }

        change.setChangeStatus(nextStatus);
        change.setCurrentStep(nextStep);
        changeMapper.updateById(change);

        // 6. 固化历史审计轨迹
        InternshipApplyChangeHistory history = InternshipApplyChangeHistory.builder()
                .changeId(change.getId())
                .nodeName("TEACHER_INITIAL_AUDIT")
                .operatorId(loginUser.getUserId())
                .operatorName(loginUser.getRealName())
                .operatorRole("TEACHER")
                .auditAction(dto.getAuditAction())
                .auditOpinion(dto.getAuditOpinion().trim())
                .snapshotStatus(nextStatus)
                .isDeleted(0)
                .build();
        changeHistoryMapper.insert(history);

        log.info("【实习重大变更】教师初审完成, changeId={}, nextStatus={}", change.getId(), nextStatus);
        return getChangeDetail(change.getId(), loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplyChangeVO deptFinalAudit(Long changeId, ApplyChangeAuditDTO dto, LoginUser loginUser) {
        log.info("【实习重大变更】院系管理员终审, changeId={}, auditorId={}, action={}", changeId, loginUser.getUserId(), dto.getAuditAction());

        // 1. 角色权限拦截
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅二级院系管理员或系统管理员有权执行终审");
        }

        // 2. 查验变更单
        InternshipApplyChange change = changeMapper.selectById(changeId);
        if (change == null || change.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习变更申请不存在");
        }

        // 3. 状态校验：防止越级审批与重复审批
        if ("PENDING_TEACHER".equals(change.getChangeStatus())) {
            throw new BusinessException(400, "该变更申请尚未通过指导教师初审，禁止越级终审！");
        }
        if (!"PENDING_DEPT".equals(change.getChangeStatus())) {
            throw new BusinessException(400, "当前变更申请不处于待院系终审阶段 (当前状态: " + change.getChangeStatus() + ")");
        }

        // 4. 院系数据隔离校验
        if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            if (loginUser.getDeptId() != null && !loginUser.getDeptId().equals(change.getDeptId())) {
                throw new BusinessException(403, "您无权审核跨院系学生的实习变更申请");
            }
        }

        // 5. 状态机流转与终审生效原子更新
        String nextStatus;
        if ("APPROVE".equals(dto.getAuditAction())) {
            nextStatus = "APPROVED";
            change.setChangeStatus(nextStatus);
            change.setCurrentStep("FINISHED");
            changeMapper.updateById(change);

            // 核心事务保障：原子同步主申请表 internship_apply
            InternshipApply origApply = applyMapper.selectById(change.getApplyId());
            if (origApply == null || origApply.getIsDeleted() == 1) {
                throw new BusinessException(404, "关联的原实习申报记录不存在或已被删除");
            }

            origApply.setCompanyName(change.getNewCompanyName());
            origApply.setJobPosition(change.getNewJobPosition());
            origApply.setJobAddress(change.getNewJobAddress());
            origApply.setCompanyContactPerson(change.getNewContactPerson());
            origApply.setCompanyContactPhone(change.getNewContactPhone());
            origApply.setCompanyContactEmail(change.getNewContactEmail());
            origApply.setStartDate(change.getNewStartDate());
            origApply.setEndDate(change.getNewEndDate());
            origApply.setInternshipMode(change.getNewInternshipMode());
            if (StringUtils.hasText(change.getNewJobDuties())) {
                origApply.setJobDuties(change.getNewJobDuties());
            }
            if (StringUtils.hasText(change.getNewAgreementFileUrl())) {
                origApply.setAgreementFileUrl(change.getNewAgreementFileUrl());
            }
            // 确保主表仍然维持 APPROVED 和 isLocked==1 锁定保护
            origApply.setApplyStatus("APPROVED");
            origApply.setIsLocked(1);
            origApply.setUpdateTime(LocalDateTime.now());
            applyMapper.updateById(origApply);

            // 记录主表审计轨迹，形成不可磨灭的变更历史闭环
            ApplyAuditHistory mainAudit = ApplyAuditHistory.builder()
                    .applyId(origApply.getId())
                    .nodeName("MAJOR_CHANGE_APPLIED")
                    .auditorId(loginUser.getUserId())
                    .auditorName(loginUser.getRealName())
                    .auditorRole(loginUser.getUserType())
                    .auditAction("APPROVED")
                    .auditOpinion("实习重大信息变更终审通过并生效（单号: " + change.getId() + "，终审意见: " + dto.getAuditOpinion().trim() + "）")
                    .isDeleted(0)
                    .build();
            applyAuditHistoryMapper.insert(mainAudit);

            log.info("【实习重大变更】终审批准通过，原申请主表数据已原子同步更新, applyId={}, changeId={}", origApply.getId(), change.getId());
        } else if ("REJECT".equals(dto.getAuditAction())) {
            nextStatus = "REJECTED";
            change.setChangeStatus(nextStatus);
            change.setCurrentStep("FINISHED");
            changeMapper.updateById(change);
            log.info("【实习重大变更】终审驳回，原申请主表数据保持原状不变, changeId={}", change.getId());
        } else {
            throw new BusinessException(400, "不支持的审核动作: " + dto.getAuditAction());
        }

        // 6. 固化历史审计轨迹
        InternshipApplyChangeHistory history = InternshipApplyChangeHistory.builder()
                .changeId(change.getId())
                .nodeName("DEPT_FINAL_AUDIT")
                .operatorId(loginUser.getUserId())
                .operatorName(loginUser.getRealName())
                .operatorRole("DEPT_ADMIN")
                .auditAction(dto.getAuditAction())
                .auditOpinion(dto.getAuditOpinion().trim())
                .snapshotStatus(nextStatus)
                .isDeleted(0)
                .build();
        changeHistoryMapper.insert(history);

        return getChangeDetail(change.getId(), loginUser);
    }

    @Override
    public ApplyChangeVO getChangeDetail(Long changeId, LoginUser loginUser) {
        InternshipApplyChange change = changeMapper.selectById(changeId);
        if (change == null || change.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习变更申请不存在");
        }

        // RBAC 权限检查
        checkViewPermission(change, loginUser);

        // 查询对应的所有审批轨迹
        List<InternshipApplyChangeHistory> histories = changeHistoryMapper.selectList(
                new LambdaQueryWrapper<InternshipApplyChangeHistory>()
                        .eq(InternshipApplyChangeHistory::getChangeId, change.getId())
                        .eq(InternshipApplyChangeHistory::getIsDeleted, 0)
                        .orderByAsc(InternshipApplyChangeHistory::getCreateTime));

        return convertToVO(change, histories);
    }

    @Override
    public List<ApplyChangeVO> listChanges(Long applyId, Long taskId, String status, LoginUser loginUser) {
        LambdaQueryWrapper<InternshipApplyChange> wrapper = new LambdaQueryWrapper<InternshipApplyChange>()
                .eq(InternshipApplyChange::getIsDeleted, 0);

        if (applyId != null) {
            wrapper.eq(InternshipApplyChange::getApplyId, applyId);
        }
        if (taskId != null) {
            wrapper.eq(InternshipApplyChange::getTaskId, taskId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(InternshipApplyChange::getChangeStatus, status.trim());
        }

        // RBAC 范围隔离
        String userType = loginUser.getUserType();
        if ("STUDENT".equals(userType)) {
            wrapper.eq(InternshipApplyChange::getStudentId, loginUser.getUserId());
        } else if ("TEACHER".equals(userType)) {
            // 教师查看自己作为指导教师的变更单
            wrapper.eq(InternshipApplyChange::getTeacherId, loginUser.getUserId());
        } else if ("DEPT_ADMIN".equals(userType)) {
            // 院管查看本院系的变更单
            if (loginUser.getDeptId() != null) {
                wrapper.eq(InternshipApplyChange::getDeptId, loginUser.getDeptId());
            }
        } // SYS_ADMIN 可以查看全校

        wrapper.orderByDesc(InternshipApplyChange::getCreateTime);
        List<InternshipApplyChange> list = changeMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        return list.stream().map(c -> convertToVO(c, Collections.emptyList())).collect(Collectors.toList());
    }

    @Override
    public ApplyChangeVO getActiveChangeByApplyId(Long applyId, LoginUser loginUser) {
        InternshipApply originalApply = applyMapper.selectById(applyId);
        if (originalApply == null || originalApply.getIsDeleted() == 1) {
            return null;
        }

        // 学生只能查询自己的
        if ("STUDENT".equals(loginUser.getUserType()) && !originalApply.getStudentId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权查看其他学生的变更记录");
        }

        // 优先获取处理中的变更单 (PENDING_TEACHER / PENDING_DEPT)
        InternshipApplyChange activeChange = changeMapper.selectOne(new LambdaQueryWrapper<InternshipApplyChange>()
                .eq(InternshipApplyChange::getApplyId, applyId)
                .in(InternshipApplyChange::getChangeStatus, List.of("PENDING_TEACHER", "PENDING_DEPT"))
                .eq(InternshipApplyChange::getIsDeleted, 0)
                .orderByDesc(InternshipApplyChange::getCreateTime)
                .last("LIMIT 1"));

        // 若无处理中，则查询最近一条已归档的历史变更单
        if (activeChange == null) {
            activeChange = changeMapper.selectOne(new LambdaQueryWrapper<InternshipApplyChange>()
                    .eq(InternshipApplyChange::getApplyId, applyId)
                    .eq(InternshipApplyChange::getIsDeleted, 0)
                    .orderByDesc(InternshipApplyChange::getCreateTime)
                    .last("LIMIT 1"));
        }

        if (activeChange == null) {
            return null;
        }

        List<InternshipApplyChangeHistory> histories = changeHistoryMapper.selectList(
                new LambdaQueryWrapper<InternshipApplyChangeHistory>()
                        .eq(InternshipApplyChangeHistory::getChangeId, activeChange.getId())
                        .eq(InternshipApplyChangeHistory::getIsDeleted, 0)
                        .orderByAsc(InternshipApplyChangeHistory::getCreateTime));

        return convertToVO(activeChange, histories);
    }

    private void checkViewPermission(InternshipApplyChange change, LoginUser loginUser) {
        String role = loginUser.getUserType();
        if ("SYS_ADMIN".equals(role)) {
            return;
        }
        if ("STUDENT".equals(role)) {
            if (!change.getStudentId().equals(loginUser.getUserId())) {
                throw new BusinessException(403, "无权查看其他学生的实习变更详情");
            }
        } else if ("TEACHER".equals(role)) {
            if (change.getTeacherId() != null && !change.getTeacherId().equals(loginUser.getUserId())) {
                throw new BusinessException(403, "无权查看非指导学生的实习变更详情");
            }
        } else if ("DEPT_ADMIN".equals(role)) {
            if (loginUser.getDeptId() != null && !loginUser.getDeptId().equals(change.getDeptId())) {
                throw new BusinessException(403, "无权查看跨院系学生的实习变更详情");
            }
        }
    }

    private ApplyChangeVO convertToVO(InternshipApplyChange c, List<InternshipApplyChangeHistory> histories) {
        List<ApplyChangeHistoryVO> historyVOs = new ArrayList<>();
        if (histories != null && !histories.isEmpty()) {
            historyVOs = histories.stream().map(h -> ApplyChangeHistoryVO.builder()
                    .id(h.getId())
                    .changeId(h.getChangeId())
                    .nodeName(h.getNodeName())
                    .operatorId(h.getOperatorId())
                    .operatorName(h.getOperatorName())
                    .operatorRole(h.getOperatorRole())
                    .auditAction(h.getAuditAction())
                    .auditOpinion(h.getAuditOpinion())
                    .snapshotStatus(h.getSnapshotStatus())
                    .createTime(h.getCreateTime())
                    .build()).collect(Collectors.toList());
        }

        return ApplyChangeVO.builder()
                .id(c.getId())
                .applyId(c.getApplyId())
                .taskId(c.getTaskId())
                .studentId(c.getStudentId())
                .studentNumber(c.getStudentNumber())
                .studentName(c.getStudentName())
                .deptId(c.getDeptId())
                .teacherId(c.getTeacherId())
                .teacherName(c.getTeacherName())
                .origCompanyName(c.getOrigCompanyName())
                .origJobPosition(c.getOrigJobPosition())
                .origJobAddress(c.getOrigJobAddress())
                .origContactPerson(c.getOrigContactPerson())
                .origContactPhone(c.getOrigContactPhone())
                .origContactEmail(c.getOrigContactEmail())
                .origStartDate(c.getOrigStartDate())
                .origEndDate(c.getOrigEndDate())
                .origInternshipMode(c.getOrigInternshipMode())
                .origJobDuties(c.getOrigJobDuties())
                .origAgreementFileUrl(c.getOrigAgreementFileUrl())
                .newCompanyName(c.getNewCompanyName())
                .newJobPosition(c.getNewJobPosition())
                .newJobAddress(c.getNewJobAddress())
                .newContactPerson(c.getNewContactPerson())
                .newContactPhone(c.getNewContactPhone())
                .newContactEmail(c.getNewContactEmail())
                .newStartDate(c.getNewStartDate())
                .newEndDate(c.getNewEndDate())
                .newInternshipMode(c.getNewInternshipMode())
                .newJobDuties(c.getNewJobDuties())
                .newAgreementFileUrl(c.getNewAgreementFileUrl())
                .changeReason(c.getChangeReason())
                .proofFileUrl(c.getProofFileUrl())
                .changeStatus(c.getChangeStatus())
                .currentStep(c.getCurrentStep())
                .createTime(c.getCreateTime())
                .updateTime(c.getUpdateTime())
                .histories(historyVOs)
                .build();
    }
}
