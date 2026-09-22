package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.dto.TaskCreateDTO;
import com.college.internship.dto.TaskUpdateDTO;
import com.college.internship.dto.TeacherAssignDTO;
import com.college.internship.entity.BaseClass;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.BaseMajor;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.InternshipTaskClass;
import com.college.internship.entity.InternshipTaskMajor;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.entity.SysOperationLog;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.BaseMajorMapper;
import com.college.internship.mapper.InternshipTaskClassMapper;
import com.college.internship.mapper.InternshipTaskMajorMapper;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.InternshipTaskStudentMapper;
import com.college.internship.mapper.SysOperationLogMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IInternshipTaskService;
import com.college.internship.vo.TaskStudentVO;
import com.college.internship.vo.TaskVO;
import com.college.internship.vo.TeacherSimpleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 实习批次任务管理核心实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternshipTaskServiceImpl implements IInternshipTaskService {

    private final InternshipTaskMapper internshipTaskMapper;
    private final InternshipTaskMajorMapper taskMajorMapper;
    private final InternshipTaskClassMapper taskClassMapper;
    private final InternshipTaskStudentMapper taskStudentMapper;
    private final BaseDepartmentMapper departmentMapper;
    private final BaseMajorMapper majorMapper;
    private final BaseClassMapper classMapper;
    private final SysUserMapper userMapper;
    private final SysOperationLogMapper operationLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO createTask(TaskCreateDTO dto, LoginUser loginUser) {
        checkTaskManagePermission(loginUser);

        Long deptId = null;
        if ("SYS_ADMIN".equals(loginUser.getUserType())) {
            deptId = dto.getDeptId();
            if (deptId == null) {
                throw new BusinessException(400, "必须明确指定所属二级院系，禁止使用默认院系 (TASK-001)");
            }
        } else {
            deptId = loginUser.getDeptId();
            if (deptId == null) {
                throw new BusinessException(400, "当前院系负责人未关联所属二级院系，无法创建实习任务 (TASK-001)");
            }
        }

        // 校验五项成绩权重合计必须严格等于100.00%
        validateWeights(dto.getWeightEnterprise(), dto.getWeightTeacherProcess(),
                dto.getWeightWeeklyReport(), dto.getWeightStageMaterial(), dto.getWeightSummary());

        // 检查任务编码唯一性
        Long count = internshipTaskMapper.selectCount(new LambdaQueryWrapper<InternshipTask>()
                .eq(InternshipTask::getTaskCode, dto.getTaskCode())
                .eq(InternshipTask::getIsDeleted, 0));
        if (count != null && count > 0) {
            throw new BusinessException(400, "任务编码 [" + dto.getTaskCode() + "] 已存在，请更换");
        }

        InternshipTask task = InternshipTask.builder()
                .taskCode(dto.getTaskCode())
                .taskName(dto.getTaskName())
                .deptId(deptId)
                .academicYear(dto.getAcademicYear())
                .semester(dto.getSemester())
                .internshipMode(dto.getInternshipMode())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .weightEnterprise(dto.getWeightEnterprise())
                .weightTeacherProcess(dto.getWeightTeacherProcess())
                .weightWeeklyReport(dto.getWeightWeeklyReport())
                .weightStageMaterial(dto.getWeightStageMaterial())
                .weightSummary(dto.getWeightSummary())
                .materialChecklist(dto.getMaterialChecklist())
                .weeklyFrequency(dto.getWeeklyFrequency())
                .weeklyDeadlineDay(dto.getWeeklyDeadlineDay())
                .safetyPassingScore(dto.getSafetyPassingScore())
                .safetyMaxAttempts(dto.getSafetyMaxAttempts())
                .status("DRAFT")
                .build();

        internshipTaskMapper.insert(task);

        // 绑定专业与班级
        bindMajorsAndClasses(task.getId(), dto.getMajorIds(), dto.getClassIds());

        return getTaskDetail(task.getId(), loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO updateTask(Long id, TaskUpdateDTO dto, LoginUser loginUser) {
        checkTaskManagePermission(loginUser);

        InternshipTask task = internshipTaskMapper.selectById(id);
        if (task == null || task.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习任务不存在或已被删除");
        }
        checkDeptScope(task.getDeptId(), loginUser);

        // 校验五项成绩权重合计必须严格等于100.00%
        validateWeights(dto.getWeightEnterprise(), dto.getWeightTeacherProcess(),
                dto.getWeightWeeklyReport(), dto.getWeightStageMaterial(), dto.getWeightSummary());

        task.setTaskName(dto.getTaskName());
        task.setAcademicYear(dto.getAcademicYear());
        task.setSemester(dto.getSemester());
        task.setInternshipMode(dto.getInternshipMode());
        task.setStartDate(dto.getStartDate());
        task.setEndDate(dto.getEndDate());
        task.setWeightEnterprise(dto.getWeightEnterprise());
        task.setWeightTeacherProcess(dto.getWeightTeacherProcess());
        task.setWeightWeeklyReport(dto.getWeightWeeklyReport());
        task.setWeightStageMaterial(dto.getWeightStageMaterial());
        task.setWeightSummary(dto.getWeightSummary());
        if (dto.getMaterialChecklist() != null) task.setMaterialChecklist(dto.getMaterialChecklist());
        task.setWeeklyFrequency(dto.getWeeklyFrequency());
        task.setWeeklyDeadlineDay(dto.getWeeklyDeadlineDay());
        task.setSafetyPassingScore(dto.getSafetyPassingScore());
        task.setSafetyMaxAttempts(dto.getSafetyMaxAttempts());

        internshipTaskMapper.updateById(task);

        // 差量更新绑定专业与班级，杜绝软删除唯一索引冲突
        if (dto.getMajorIds() != null) {
            List<InternshipTaskMajor> existingMajors = taskMajorMapper.selectList(
                    new LambdaQueryWrapper<InternshipTaskMajor>().eq(InternshipTaskMajor::getTaskId, id));
            List<Long> existingMajorIds = existingMajors.stream().map(InternshipTaskMajor::getMajorId).toList();
            for (InternshipTaskMajor em : existingMajors) {
                if (!dto.getMajorIds().contains(em.getMajorId())) {
                    taskMajorMapper.deleteById(em.getId());
                }
            }
            for (Long mId : dto.getMajorIds()) {
                if (!existingMajorIds.contains(mId)) {
                    taskMajorMapper.insert(InternshipTaskMajor.builder().taskId(id).majorId(mId).isDeleted(0).build());
                }
            }
        }

        if (dto.getClassIds() != null) {
            List<InternshipTaskClass> existingClasses = taskClassMapper.selectList(
                    new LambdaQueryWrapper<InternshipTaskClass>().eq(InternshipTaskClass::getTaskId, id));
            List<Long> existingClassIds = existingClasses.stream().map(InternshipTaskClass::getClassId).toList();
            for (InternshipTaskClass ec : existingClasses) {
                if (!dto.getClassIds().contains(ec.getClassId())) {
                    taskClassMapper.deleteById(ec.getId());
                }
            }
            for (Long cId : dto.getClassIds()) {
                if (!existingClassIds.contains(cId)) {
                    taskClassMapper.insert(InternshipTaskClass.builder().taskId(id).classId(cId).isDeleted(0).build());
                }
            }
        }

        return getTaskDetail(id, loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishTask(Long id, LoginUser loginUser) {
        checkTaskManagePermission(loginUser);

        InternshipTask task = internshipTaskMapper.selectById(id);
        if (task == null || task.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习任务不存在");
        }
        checkDeptScope(task.getDeptId(), loginUser);

        // 核心规则：未配置完整周报频次、截止日、安全及格分、重测次数与权重时禁止发布任务 (TASK-004/009)
        validateTaskPublishReadiness(task);

        task.setStatus("PUBLISHED");
        internshipTaskMapper.updateById(task);

        // 自动圈定学生名单：根据关联班级查询学生并导入 internship_task_student
        List<InternshipTaskClass> classLinks = taskClassMapper.selectList(
                new LambdaQueryWrapper<InternshipTaskClass>().eq(InternshipTaskClass::getTaskId, id));

        for (InternshipTaskClass cl : classLinks) {
            List<SysUser> students = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getClassId, cl.getClassId())
                    .eq(SysUser::getUserType, "STUDENT")
                    .eq(SysUser::getIsDeleted, 0));

            for (SysUser s : students) {
                Long exists = taskStudentMapper.selectCount(new LambdaQueryWrapper<InternshipTaskStudent>()
                        .eq(InternshipTaskStudent::getTaskId, id)
                        .eq(InternshipTaskStudent::getStudentId, s.getId()));
                if (exists == null || exists == 0) {
                    InternshipTaskStudent ts = InternshipTaskStudent.builder()
                            .taskId(id)
                            .studentId(s.getId())
                            .studentNumber(s.getUserNumber())
                            .studentName(s.getRealName())
                            .classId(s.getClassId())
                            .readMaterialIds("[]")
                            .readMaterialCount(0)
                            .safetyStatus("NOT_STARTED")
                            .isDeleted(0)
                            .build();
                    taskStudentMapper.insert(ts);
                }
            }
        }
        log.info("实习任务 [{}] 成功正式发布并完成学生名单圈定", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id, LoginUser loginUser) {
        checkTaskManagePermission(loginUser);

        InternshipTask task = internshipTaskMapper.selectById(id);
        if (task == null || task.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习任务不存在");
        }
        checkDeptScope(task.getDeptId(), loginUser);

        if (!"DRAFT".equals(task.getStatus())) {
            throw new BusinessException(400, "仅允许删除未正式发布的草稿任务 (TASK-011)");
        }

        internshipTaskMapper.deleteById(id);
    }

    @Override
    public TaskVO getTaskDetail(Long id, LoginUser loginUser) {
        InternshipTask task = internshipTaskMapper.selectById(id);
        if (task == null || task.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习任务不存在");
        }

        String deptName = "";
        if (task.getDeptId() != null) {
            BaseDepartment dept = departmentMapper.selectById(task.getDeptId());
            if (dept != null) deptName = dept.getDeptName();
        }

        // 查询专业名称
        List<InternshipTaskMajor> majors = taskMajorMapper.selectList(
                new LambdaQueryWrapper<InternshipTaskMajor>().eq(InternshipTaskMajor::getTaskId, id));
        List<Long> majorIds = new ArrayList<>();
        List<String> majorNames = new ArrayList<>();
        for (InternshipTaskMajor m : majors) {
            majorIds.add(m.getMajorId());
            BaseMajor bm = majorMapper.selectById(m.getMajorId());
            if (bm != null) majorNames.add(bm.getMajorName());
        }

        // 查询班级名称
        List<InternshipTaskClass> classes = taskClassMapper.selectList(
                new LambdaQueryWrapper<InternshipTaskClass>().eq(InternshipTaskClass::getTaskId, id));
        List<Long> classIds = new ArrayList<>();
        List<String> classNames = new ArrayList<>();
        for (InternshipTaskClass c : classes) {
            classIds.add(c.getClassId());
            BaseClass bc = classMapper.selectById(c.getClassId());
            if (bc != null) classNames.add(bc.getClassName());
        }

        // 圈定学生数
        Long studentCount = taskStudentMapper.selectCount(
                new LambdaQueryWrapper<InternshipTaskStudent>().eq(InternshipTaskStudent::getTaskId, id));

        return TaskVO.builder()
                .id(task.getId())
                .taskCode(task.getTaskCode())
                .taskName(task.getTaskName())
                .deptId(task.getDeptId())
                .deptName(deptName)
                .academicYear(task.getAcademicYear())
                .semester(task.getSemester())
                .internshipMode(task.getInternshipMode())
                .startDate(task.getStartDate())
                .endDate(task.getEndDate())
                .weightEnterprise(task.getWeightEnterprise())
                .weightTeacherProcess(task.getWeightTeacherProcess())
                .weightWeeklyReport(task.getWeightWeeklyReport())
                .weightStageMaterial(task.getWeightStageMaterial())
                .weightSummary(task.getWeightSummary())
                .materialChecklist(task.getMaterialChecklist())
                .weeklyFrequency(task.getWeeklyFrequency())
                .weeklyDeadlineDay(task.getWeeklyDeadlineDay())
                .safetyPassingScore(task.getSafetyPassingScore())
                .safetyMaxAttempts(task.getSafetyMaxAttempts())
                .status(task.getStatus())
                .createTime(task.getCreateTime())
                .majorIds(majorIds)
                .majorNames(majorNames)
                .classIds(classIds)
                .classNames(classNames)
                .studentCount(studentCount != null ? studentCount.intValue() : 0)
                .build();
    }

    @Override
    public List<TaskVO> listTasks(Long deptId, String status, LoginUser loginUser) {
        LambdaQueryWrapper<InternshipTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InternshipTask::getIsDeleted, 0);

        if ("STUDENT".equals(loginUser.getUserType())) {
            // 学生端：仅查看与本人相关的已发布或进行中任务
            wrapper.in(InternshipTask::getStatus, "PUBLISHED", "IN_PROGRESS");
            if (loginUser.getDeptId() != null) {
                wrapper.eq(InternshipTask::getDeptId, loginUser.getDeptId());
            }
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType()) || "TEACHER".equals(loginUser.getUserType())) {
            // 院系负责人与教师按院系隔离
            wrapper.eq(InternshipTask::getDeptId, loginUser.getDeptId());
        } else if (deptId != null) {
            wrapper.eq(InternshipTask::getDeptId, deptId);
        }

        if (StringUtils.hasText(status)) {
            wrapper.eq(InternshipTask::getStatus, status);
        }

        wrapper.orderByDesc(InternshipTask::getId);
        List<InternshipTask> list = internshipTaskMapper.selectList(wrapper);

        return list.stream().map(t -> getTaskDetail(t.getId(), loginUser)).toList();
    }

    private void validateTaskPublishReadiness(InternshipTask task) {
        // 1. 五项成绩权重必须非空且严格等于 100.00%
        validateWeights(task.getWeightEnterprise(), task.getWeightTeacherProcess(),
                task.getWeightWeeklyReport(), task.getWeightStageMaterial(), task.getWeightSummary());

        // 2. 周报频次与截止日必须明确配置
        if (!StringUtils.hasText(task.getWeeklyFrequency())) {
            throw new BusinessException(400, "实习任务尚未配置周报提交频次，未完整配置时禁止发布任务 (TASK-004)");
        }
        if (task.getWeeklyDeadlineDay() == null || task.getWeeklyDeadlineDay() < 1 || task.getWeeklyDeadlineDay() > 7) {
            throw new BusinessException(400, "实习任务尚未配置合法的每周周报截止日(1-7)，未完整配置时禁止发布任务 (TASK-004)");
        }

        // 3. 安全考试及格分与最大允许重测次数必须明确配置
        if (task.getSafetyPassingScore() == null || task.getSafetyPassingScore() <= 0 || task.getSafetyPassingScore() > 100) {
            throw new BusinessException(400, "实习任务尚未配置安全教育考试合格分值(1-100)，未完整配置时禁止发布任务 (TASK-004)");
        }
        if (task.getSafetyMaxAttempts() == null || task.getSafetyMaxAttempts() < 1) {
            throw new BusinessException(400, "实习任务尚未配置安全考试最大允许重测次数(>=1)，未完整配置时禁止发布任务 (TASK-004)");
        }
    }

    private void validateWeights(BigDecimal enterprise, BigDecimal teacherProcess,
                                 BigDecimal weeklyReport, BigDecimal stageMaterial, BigDecimal summary) {
        if (enterprise == null || teacherProcess == null || weeklyReport == null || stageMaterial == null || summary == null) {
            throw new BusinessException(400, "五项评价成绩权重均不能为空");
        }
        BigDecimal sum = enterprise.add(teacherProcess).add(weeklyReport).add(stageMaterial).add(summary);
        if (sum.compareTo(new BigDecimal("100.00")) != 0 && sum.compareTo(new BigDecimal("100")) != 0) {
            throw new BusinessException(400, "五项评价成绩权重之和必须严格等于100.00%，当前合计为: " + sum + "% (TASK-009)");
        }
    }

    private void bindMajorsAndClasses(Long taskId, List<Long> majorIds, List<Long> classIds) {
        if (majorIds != null) {
            for (Long mId : majorIds) {
                taskMajorMapper.insert(InternshipTaskMajor.builder().taskId(taskId).majorId(mId).isDeleted(0).build());
            }
        }
        if (classIds != null) {
            for (Long cId : classIds) {
                taskClassMapper.insert(InternshipTaskClass.builder().taskId(taskId).classId(cId).isDeleted(0).build());
            }
        }
    }

    private void checkTaskManagePermission(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅院系负责人或学校管理员具备实习任务配置管理权限");
        }
    }

    private void checkDeptScope(Long taskDeptId, LoginUser loginUser) {
        if ("DEPT_ADMIN".equals(loginUser.getUserType()) && !taskDeptId.equals(loginUser.getDeptId())) {
            throw new BusinessException(403, "不能越权修改其他二级院系的实习任务");
        }
    }

    @Override
    public List<TaskStudentVO> getTaskStudents(Long taskId, Long classId, Long teacherId, String keyword, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "无权访问任务圈定学生名单，仅允许院系教学负责人或学校管理员查看 (TASK-003)");
        }
        InternshipTask task = internshipTaskMapper.selectById(taskId);
        if (task == null || task.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习任务不存在");
        }

        // 院系数据隔离
        if ("DEPT_ADMIN".equals(loginUser.getUserType()) && !task.getDeptId().equals(loginUser.getDeptId())) {
            throw new BusinessException(403, "无权查看其他二级院系任务圈定学生名单");
        }

        LambdaQueryWrapper<InternshipTaskStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InternshipTaskStudent::getTaskId, taskId)
                .eq(InternshipTaskStudent::getIsDeleted, 0);

        if (teacherId != null) {
            wrapper.eq(InternshipTaskStudent::getTeacherId, teacherId);
        }

        if (classId != null) {
            wrapper.eq(InternshipTaskStudent::getClassId, classId);
        }

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(InternshipTaskStudent::getStudentName, keyword)
                    .or().like(InternshipTaskStudent::getStudentNumber, keyword));
        }

        wrapper.orderByAsc(InternshipTaskStudent::getId);
        List<InternshipTaskStudent> list = taskStudentMapper.selectList(wrapper);
        List<TaskStudentVO> result = new ArrayList<>();

        for (InternshipTaskStudent ts : list) {
            String className = "未分班";
            if (ts.getClassId() != null) {
                BaseClass bc = classMapper.selectById(ts.getClassId());
                if (bc != null) {
                    className = bc.getClassName();
                }
            }
            String teacherName = "待分配导师";
            if (ts.getTeacherId() != null) {
                SysUser teacher = userMapper.selectById(ts.getTeacherId());
                if (teacher != null) {
                    teacherName = teacher.getRealName();
                }
            }

            result.add(TaskStudentVO.builder()
                    .id(ts.getId())
                    .taskId(ts.getTaskId())
                    .studentId(ts.getStudentId())
                    .studentNumber(ts.getStudentNumber())
                    .studentName(ts.getStudentName())
                    .classId(ts.getClassId())
                    .className(className)
                    .teacherId(ts.getTeacherId())
                    .teacherName(teacherName)
                    .safetyStatus(ts.getSafetyStatus())
                    .build());
        }
        return result;
    }

    @Override
    public List<TeacherSimpleVO> getAvailableTeachers(Long taskId, LoginUser loginUser) {
        if (loginUser == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "无权访问指导教师列表，仅允许院系教学负责人或学校管理员查看 (ASSIGN-001)");
        }
        InternshipTask task = internshipTaskMapper.selectById(taskId);
        if (task == null || task.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习任务不存在");
        }

        // 院系数据隔离
        if ("DEPT_ADMIN".equals(loginUser.getUserType()) && !task.getDeptId().equals(loginUser.getDeptId())) {
            throw new BusinessException(403, "无权查看其他二级院系可用指导教师列表");
        }

        Long deptId = task.getDeptId();
        BaseDepartment dept = departmentMapper.selectById(deptId);
        String deptName = dept != null ? dept.getDeptName() : "未知院系";

        List<SysUser> teachers = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, "TEACHER")
                .eq(SysUser::getDeptId, deptId)
                .eq(SysUser::getIsDeleted, 0)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));

        List<TeacherSimpleVO> result = new ArrayList<>();
        for (SysUser t : teachers) {
            Long count = taskStudentMapper.selectCount(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, taskId)
                    .eq(InternshipTaskStudent::getTeacherId, t.getId())
                    .eq(InternshipTaskStudent::getIsDeleted, 0));

            result.add(TeacherSimpleVO.builder()
                    .id(t.getId())
                    .userNumber(t.getUserNumber())
                    .realName(t.getRealName())
                    .deptId(t.getDeptId())
                    .deptName(deptName)
                    .assignedStudentsCount(count != null ? count.intValue() : 0)
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTeacher(Long taskId, TeacherAssignDTO dto, LoginUser loginUser) {
        checkTaskManagePermission(loginUser);

        InternshipTask task = internshipTaskMapper.selectById(taskId);
        if (task == null || task.getIsDeleted() == 1) {
            throw new BusinessException(404, "实习任务不存在");
        }
        checkDeptScope(task.getDeptId(), loginUser);

        // 校验指导教师有效性
        SysUser teacher = userMapper.selectById(dto.getTeacherId());
        if (teacher == null || teacher.getIsDeleted() == 1 || !"TEACHER".equals(teacher.getUserType())) {
            throw new BusinessException(400, "指定的指导教师不存在或非教师角色");
        }
        if (teacher.getDeptId() != null && !teacher.getDeptId().equals(task.getDeptId())) {
            throw new BusinessException(400, "该指导教师属于其他院系，仅限指派本二级院系专业教师");
        }

        int updatedCount = 0;
        for (Long studentId : dto.getStudentIds()) {
            InternshipTaskStudent ts = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, taskId)
                    .eq(InternshipTaskStudent::getStudentId, studentId)
                    .eq(InternshipTaskStudent::getIsDeleted, 0));
            if (ts == null) {
                throw new BusinessException(400, "指派失败：学生ID [" + studentId + "] 不在当前实习任务圈定名单中，禁止分配 (ASSIGN-001)");
            }
            ts.setTeacherId(dto.getTeacherId());
            taskStudentMapper.updateById(ts);
            updatedCount++;
        }

        // 记录审计日志
        try {
            SysOperationLog operLog = SysOperationLog.builder()
                    .title("指导教师指派与调整 (ASSIGN-001 ~ ASSIGN-003)")
                    .businessType("UPDATE")
                    .method("InternshipTaskServiceImpl.assignTeacher")
                    .requestMethod("POST")
                    .operatorId(loginUser.getUserId())
                    .operatorName(loginUser.getRealName())
                    .operUrl("/api/v1/tasks/" + taskId + "/assign-teacher")
                    .operIp("127.0.0.1")
                    .operParam("taskId=" + taskId + ", teacherId=" + dto.getTeacherId() + ", studentCount=" + dto.getStudentIds().size())
                    .jsonResult("成功为任务 [" + task.getTaskName() + "] 中的 " + updatedCount + " 名学生分配指导教师 [" + teacher.getRealName() + "]")
                    .status(1)
                    .operTime(java.time.LocalDateTime.now())
                    .build();
            operationLogMapper.insert(operLog);
        } catch (Exception e) {
            log.warn("记录指导教师分配审计日志失败: {}", e.getMessage());
        }

        log.info("用户 [{}] 成功为任务 [{}] 分配指导教师 [{}] (关联学生数: {})",
                loginUser.getRealName(), taskId, teacher.getRealName(), updatedCount);
    }
}
