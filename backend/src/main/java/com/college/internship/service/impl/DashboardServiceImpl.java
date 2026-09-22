package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.entity.BaseClass;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.BaseMajor;
import com.college.internship.entity.InternshipApply;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.SafetyCommitmentSign;
import com.college.internship.entity.SafetyExamAttempt;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.BaseMajorMapper;
import com.college.internship.mapper.InternshipApplyMapper;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.SafetyCommitmentSignMapper;
import com.college.internship.mapper.SafetyExamAttemptMapper;
import com.college.internship.mapper.SysOperationLogMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IDashboardService;
import com.college.internship.vo.DashboardSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 角色工作台动态指标统计服务实现类 (阶段5丰富业务指标)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements IDashboardService {

    private final SysUserMapper sysUserMapper;
    private final BaseDepartmentMapper baseDepartmentMapper;
    private final BaseMajorMapper baseMajorMapper;
    private final BaseClassMapper baseClassMapper;
    private final SysOperationLogMapper sysOperationLogMapper;
    private final InternshipTaskMapper taskMapper;
    private final InternshipApplyMapper applyMapper;
    private final SafetyExamAttemptMapper examAttemptMapper;
    private final SafetyCommitmentSignMapper commitmentSignMapper;

    @Override
    public DashboardSummaryVO getSummary(LoginUser loginUser) {
        if (loginUser == null) {
            return DashboardSummaryVO.builder().build();
        }

        String userType = loginUser.getUserType();
        String primaryRole = (loginUser.getPermissions() != null && !loginUser.getPermissions().isEmpty())
                ? loginUser.getPermissions().get(0)
                : "ROLE_" + userType;

        String deptName = "";
        if (loginUser.getDeptId() != null) {
            BaseDepartment dept = baseDepartmentMapper.selectById(loginUser.getDeptId());
            if (dept != null) {
                deptName = dept.getDeptName();
            }
        }

        Map<String, Object> metrics = new LinkedHashMap<>();

        switch (userType) {
            case "STUDENT" -> {
                SysUser user = sysUserMapper.selectById(loginUser.getUserId());
                String majorName = "";
                String className = "";
                if (user != null) {
                    if (user.getMajorId() != null) {
                        BaseMajor major = baseMajorMapper.selectById(user.getMajorId());
                        if (major != null) majorName = major.getMajorName();
                    }
                    if (user.getClassId() != null) {
                        BaseClass clz = baseClassMapper.selectById(user.getClassId());
                        if (clz != null) className = clz.getClassName();
                    }
                }

                // 查询真实申报状态
                InternshipApply apply = applyMapper.selectOne(new LambdaQueryWrapper<InternshipApply>()
                        .eq(InternshipApply::getStudentId, loginUser.getUserId())
                        .eq(InternshipApply::getIsDeleted, 0)
                        .orderByDesc(InternshipApply::getId)
                        .last("LIMIT 1"));

                String internshipStatus = (apply != null) ? apply.getApplyStatus() : "待申报";

                // 查询真实五阶段安全教育状态
                Long passedCount = examAttemptMapper.selectCount(new LambdaQueryWrapper<SafetyExamAttempt>()
                        .eq(SafetyExamAttempt::getStudentId, loginUser.getUserId())
                        .eq(SafetyExamAttempt::getIsPassed, 1)
                        .eq(SafetyExamAttempt::getIsDeleted, 0));

                SafetyCommitmentSign sign = commitmentSignMapper.selectOne(new LambdaQueryWrapper<SafetyCommitmentSign>()
                        .eq(SafetyCommitmentSign::getStudentId, loginUser.getUserId())
                        .eq(SafetyCommitmentSign::getIsSigned, 1)
                        .eq(SafetyCommitmentSign::getIsDeleted, 0));

                String safetyEduStatus = "NOT_STARTED";
                if (passedCount != null && passedCount > 0 && sign != null) {
                    safetyEduStatus = "COMPLETED";
                } else if (passedCount != null && passedCount > 0) {
                    safetyEduStatus = "PASSED";
                } else if (sign != null) {
                    safetyEduStatus = "PENDING_TEST";
                }

                metrics.put("internshipStatus", internshipStatus);
                metrics.put("safetyEduStatus", safetyEduStatus);
                metrics.put("pendingTasksCount", "COMPLETED".equals(safetyEduStatus) ? 0 : 1);
                metrics.put("warningCount", 0);
                metrics.put("className", className);
                metrics.put("majorName", majorName);
                metrics.put("userNumber", user != null ? user.getUserNumber() : "");
            }
            case "TEACHER" -> {
                Long studentCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUserType, "STUDENT")
                        .eq(loginUser.getDeptId() != null, SysUser::getDeptId, loginUser.getDeptId())
                        .eq(SysUser::getIsDeleted, 0));

                // 查询待初审申报单据数量
                Long pendingReviewCount = applyMapper.selectCount(new LambdaQueryWrapper<InternshipApply>()
                        .eq(loginUser.getDeptId() != null, InternshipApply::getDeptId, loginUser.getDeptId())
                        .eq(InternshipApply::getApplyStatus, "SUBMITTED")
                        .eq(InternshipApply::getIsDeleted, 0));

                // 统计达标率
                Long passedExamCount = examAttemptMapper.selectCount(new LambdaQueryWrapper<SafetyExamAttempt>()
                        .eq(SafetyExamAttempt::getIsPassed, 1)
                        .eq(SafetyExamAttempt::getIsDeleted, 0));

                String rate = (studentCount != null && studentCount > 0 && passedExamCount != null)
                        ? String.format("%.0f%%", (passedExamCount * 100.0 / studentCount))
                        : "0%";

                metrics.put("assignedStudentCount", studentCount != null ? studentCount : 0);
                metrics.put("pendingReviewCount", pendingReviewCount != null ? pendingReviewCount : 0);
                metrics.put("safetyCompletedRate", rate);
                metrics.put("activeWarningCount", 0);
            }
            case "DEPT_ADMIN" -> {
                Long deptStudents = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUserType, "STUDENT")
                        .eq(loginUser.getDeptId() != null, SysUser::getDeptId, loginUser.getDeptId())
                        .eq(SysUser::getIsDeleted, 0));
                Long deptTeachers = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUserType, "TEACHER")
                        .eq(loginUser.getDeptId() != null, SysUser::getDeptId, loginUser.getDeptId())
                        .eq(SysUser::getIsDeleted, 0));
                Long deptClasses = baseClassMapper.selectCount(new LambdaQueryWrapper<BaseClass>()
                        .eq(loginUser.getDeptId() != null, BaseClass::getDeptId, loginUser.getDeptId())
                        .eq(BaseClass::getIsDeleted, 0));

                // 待终审单据数 (TEACHER_APPROVED)
                Long pendingAuditCount = applyMapper.selectCount(new LambdaQueryWrapper<InternshipApply>()
                        .eq(loginUser.getDeptId() != null, InternshipApply::getDeptId, loginUser.getDeptId())
                        .eq(InternshipApply::getApplyStatus, "TEACHER_APPROVED")
                        .eq(InternshipApply::getIsDeleted, 0));

                metrics.put("totalStudents", deptStudents != null ? deptStudents : 0);
                metrics.put("totalTeachers", deptTeachers != null ? deptTeachers : 0);
                metrics.put("totalClasses", deptClasses != null ? deptClasses : 0);
                metrics.put("pendingAuditCount", pendingAuditCount != null ? pendingAuditCount : 0);
            }
            case "SYS_ADMIN" -> {
                Long totalUsers = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getIsDeleted, 0));
                Long totalDepts = baseDepartmentMapper.selectCount(new LambdaQueryWrapper<BaseDepartment>()
                        .eq(BaseDepartment::getIsDeleted, 0));
                Long totalMajors = baseMajorMapper.selectCount(new LambdaQueryWrapper<BaseMajor>()
                        .eq(BaseMajor::getIsDeleted, 0));
                Long totalClasses = baseClassMapper.selectCount(new LambdaQueryWrapper<BaseClass>()
                        .eq(BaseClass::getIsDeleted, 0));
                Long totalTasks = taskMapper.selectCount(new LambdaQueryWrapper<InternshipTask>()
                        .eq(InternshipTask::getIsDeleted, 0));
                Long totalLogs = sysOperationLogMapper.selectCount(null);

                metrics.put("totalUsers", totalUsers != null ? totalUsers : 0);
                metrics.put("totalDepts", totalDepts != null ? totalDepts : 0);
                metrics.put("totalMajors", totalMajors != null ? totalMajors : 0);
                metrics.put("totalClasses", totalClasses != null ? totalClasses : 0);
                metrics.put("totalTasks", totalTasks != null ? totalTasks : 0);
                metrics.put("operationLogsCount", totalLogs != null ? totalLogs : 0);
            }
            default -> log.warn("未知用户类型工作台指标请求: {}", userType);
        }

        return DashboardSummaryVO.builder()
                .userType(userType)
                .roleCode(primaryRole)
                .realName(loginUser.getRealName())
                .deptName(deptName)
                .metrics(metrics)
                .build();
    }
}
