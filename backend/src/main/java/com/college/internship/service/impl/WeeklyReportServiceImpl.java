package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.WeeklyProperties;
import com.college.internship.dto.WeeklyReportReviewDTO;
import com.college.internship.dto.WeeklyReportSaveDTO;
import com.college.internship.entity.BaseClass;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.InternshipApply;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.entity.InternshipWeeklyReport;
import com.college.internship.entity.InternshipWeeklyReportHistory;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.InternshipApplyMapper;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.InternshipTaskStudentMapper;
import com.college.internship.mapper.InternshipWeeklyReportHistoryMapper;
import com.college.internship.mapper.InternshipWeeklyReportMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.service.IWeeklyReportService;
import com.college.internship.vo.WeeklyMonitorSummaryVO;
import com.college.internship.vo.WeeklyReportDetailVO;
import com.college.internship.vo.WeeklyReportVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 阶段6 实习周报核心业务服务实现类 (API-056 ~ API-059)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportServiceImpl implements IWeeklyReportService {

    private final InternshipWeeklyReportMapper weeklyReportMapper;
    private final InternshipWeeklyReportHistoryMapper historyMapper;
    private final InternshipTaskMapper taskMapper;
    private final InternshipTaskStudentMapper taskStudentMapper;
    private final InternshipApplyMapper applyMapper;
    private final BaseDepartmentMapper departmentMapper;
    private final BaseClassMapper classMapper;
    private final SysUserMapper userMapper;
    private final ISysOperationLogService logService;
    private final WeeklyProperties weeklyProperties;
    private final ObjectMapper objectMapper;
    private final com.college.internship.mapper.InternshipArchiveMapper archiveMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WeeklyReportVO saveOrSubmitReport(WeeklyReportSaveDTO dto, LoginUser loginUser) {
        if (!"STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅学生账号允许撰写或提交周报");
        }
        Long studentId = loginUser.getUserId();

        // 0. 全局归档写保护拦截 (TEST-P7-21)
        com.college.internship.entity.InternshipArchive archive = archiveMapper.selectOne(new LambdaQueryWrapper<com.college.internship.entity.InternshipArchive>()
                .eq(com.college.internship.entity.InternshipArchive::getTaskId, dto.getTaskId())
                .eq(com.college.internship.entity.InternshipArchive::getStudentId, studentId)
                .eq(com.college.internship.entity.BasePhase7Entity::getIsDeleted, 0));
        if (archive != null) {
            if ("ARCHIVED".equals(archive.getStatus())) {
                throw new BusinessException(400, "该实习卷宗已归档锁定，处于全局只读写保护状态，禁止修改。如需修改请向超级管理员申请特批解锁。");
            } else if ("SPECIAL_UNLOCKED".equals(archive.getStatus())) {
                if (archive.getUnlockExpireTime() != null && LocalDateTime.now().isAfter(archive.getUnlockExpireTime())) {
                    archive.setStatus("ARCHIVED");
                    archiveMapper.updateById(archive);
                    throw new BusinessException(400, "特批解锁时效已过期，卷宗已自动恢复归档锁定，处于全局只读写保护状态。");
                }
            }
        }

        // 1. 前置准入强校验：学生必须存在处于 APPROVED 且 is_locked = 1 的生效实习记录 (TEST-W01)
        InternshipApply apply = applyMapper.selectOne(new LambdaQueryWrapper<InternshipApply>()
                .eq(InternshipApply::getTaskId, dto.getTaskId())
                .eq(InternshipApply::getStudentId, studentId)
                .eq(InternshipApply::getApplyStatus, "APPROVED")
                .eq(InternshipApply::getIsLocked, 1));
        if (apply == null) {
            throw new BusinessException(400, "尚未通过实习申报审核，无法提交或暂存周报");
        }

        // 2. 任务存在性与周期计算
        InternshipTask task = taskMapper.selectById(dto.getTaskId());
        if (task == null) {
            throw new BusinessException(400, "实习任务不存在");
        }

        // 获取绑定的指导教师与院系
        InternshipTaskStudent taskStudent = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                .eq(InternshipTaskStudent::getTaskId, dto.getTaskId())
                .eq(InternshipTaskStudent::getStudentId, studentId));
        if (taskStudent == null) {
            throw new BusinessException(400, "当前学生未在实习任务圈定名单中");
        }
        if (taskStudent.getTeacherId() == null) {
            throw new BusinessException(400, "实习任务中尚未分配指导教师");
        }
        Long teacherId = taskStudent.getTeacherId();

        if (task.getDeptId() == null) {
            throw new BusinessException(400, "实习任务所属院系缺失");
        }
        Long deptId = task.getDeptId();

        // 校验并计算周期起止与截止时间（拒绝任何静默兜底）
        if (!StringUtils.hasText(task.getWeeklyFrequency())) {
            throw new BusinessException(400, "实习任务周报频次配置缺失");
        }
        String freq = task.getWeeklyFrequency().trim().toUpperCase();
        if (!"WEEKLY".equals(freq) && !"BIWEEKLY".equals(freq)) {
            throw new BusinessException(400, "实习任务周报频次配置非法 (仅支持 WEEKLY 或 BIWEEKLY)");
        }
        int step = "BIWEEKLY".equals(freq) ? 2 : 1;

        if (task.getStartDate() == null) {
            throw new BusinessException(400, "实习任务起始日期缺失");
        }
        LocalDate startDate = task.getStartDate();

        if (task.getEndDate() == null) {
            throw new BusinessException(400, "实习任务结束日期缺失");
        }
        LocalDate endDate = task.getEndDate();

        if (task.getWeeklyDeadlineDay() == null) {
            throw new BusinessException(400, "实习任务周报截止星期配置缺失");
        }
        int deadlineDay = task.getWeeklyDeadlineDay();
        if (deadlineDay < 1 || deadlineDay > 7) {
            throw new BusinessException(400, "实习任务周报截止星期必须在1至7之间");
        }

        LocalDate periodStart = startDate.plusDays((dto.getWeekNumber() - 1) * 7L * step);
        LocalDate periodEnd = startDate.plusDays(dto.getWeekNumber() * 7L * step - 1);
        if (periodStart.isAfter(endDate)) {
            throw new BusinessException(400, "周次超出实习任务周期范围");
        }
        if (periodEnd.isAfter(endDate)) {
            periodEnd = endDate;
        }

        LocalDate monday = periodEnd.with(DayOfWeek.MONDAY);
        LocalDate deadlineDate = monday.plusDays(deadlineDay - 1);
        if (deadlineDate.isBefore(periodStart)) {
            deadlineDate = deadlineDate.plusWeeks(1);
        }
        LocalDateTime deadlineTime = LocalDateTime.of(deadlineDate, LocalTime.of(23, 59, 59));

        // 3. 查重防重校验 (TEST-W06)
        InternshipWeeklyReport report = weeklyReportMapper.selectOne(new LambdaQueryWrapper<InternshipWeeklyReport>()
                .eq(InternshipWeeklyReport::getTaskId, dto.getTaskId())
                .eq(InternshipWeeklyReport::getStudentId, studentId)
                .eq(InternshipWeeklyReport::getWeekNumber, dto.getWeekNumber()));

        String action = dto.getAction().trim().toUpperCase();
        if (report != null) {
            if ("REVIEWED".equals(report.getStatus())) {
                throw new BusinessException(400, "该周报已批阅锁定，禁止修改或重新提交");
            }
            if ("SUBMITTED".equals(report.getStatus())) {
                throw new BusinessException(400, "该周报已提交等待批阅，禁止重复提交");
            }
        }

        // 4. 草稿暂存逻辑 (TEST-W02)
        if ("DRAFT".equals(action)) {
            if (report == null) {
                report = InternshipWeeklyReport.builder()
                        .taskId(dto.getTaskId())
                        .studentId(studentId)
                        .teacherId(teacherId)
                        .deptId(deptId)
                        .weekNumber(dto.getWeekNumber())
                        .startDate(periodStart)
                        .endDate(periodEnd)
                        .deadlineTime(deadlineTime)
                        .version(1)
                        .status("DRAFT")
                        .isOverdue(0)
                        .overdueDays(0)
                        .build();
            }
            report.setWorkContent(dto.getWorkContent());
            report.setWorkSummary(dto.getWorkSummary());
            report.setProblemEncountered(dto.getProblemEncountered());
            report.setNextWeekPlan(dto.getNextWeekPlan());
            report.setAttachmentUrl(dto.getAttachmentUrl());

            if (report.getId() == null) {
                weeklyReportMapper.insert(report);
            } else {
                weeklyReportMapper.updateById(report);
            }
            return convertToVO(report, task.getTaskName(), loginUser.getRealName());
        }

        // 5. 正式提交逻辑 (SUBMIT)
        if ("SUBMIT".equals(action)) {
            // 业务字数阈值强校验 (TEST-W03)
            int minLen = weeklyProperties.getMinContentLength();
            validateLength(dto.getWorkContent(), minLen, "本周工作内容");
            validateLength(dto.getWorkSummary(), minLen, "实习收获与体会");
            validateLength(dto.getProblemEncountered(), minLen, "遇到的问题与思路");
            validateLength(dto.getNextWeekPlan(), minLen, "下周工作计划");

            // 附件 URL 扩展名白名单校验 (若有)
            validateAttachmentUrl(dto.getAttachmentUrl());

            // 逾期判定 (TEST-W04, TEST-W05)
            ZoneId zoneId = ZoneId.of(weeklyProperties.getSystemTimezone());
            LocalDateTime now = LocalDateTime.now(zoneId);
            int isOverdue = 0;
            int overdueDays = 0;
            if (now.isAfter(deadlineTime)) {
                isOverdue = 1;
                overdueDays = (int) Math.max(1, ChronoUnit.DAYS.between(deadlineDate, now.toLocalDate()));
            }

            boolean isResubmit = (report != null && "RETURNED".equals(report.getStatus()));
            if (report == null) {
                report = InternshipWeeklyReport.builder()
                        .taskId(dto.getTaskId())
                        .studentId(studentId)
                        .teacherId(teacherId)
                        .deptId(deptId)
                        .weekNumber(dto.getWeekNumber())
                        .startDate(periodStart)
                        .endDate(periodEnd)
                        .deadlineTime(deadlineTime)
                        .version(1)
                        .build();
            } else if (isResubmit) {
                // 退回修改后重新提交，版本自增 (TEST-W10)
                report.setVersion(report.getVersion() + 1);
            }

            report.setWorkContent(dto.getWorkContent().trim());
            report.setWorkSummary(dto.getWorkSummary().trim());
            report.setProblemEncountered(dto.getProblemEncountered().trim());
            report.setNextWeekPlan(dto.getNextWeekPlan().trim());
            report.setAttachmentUrl(dto.getAttachmentUrl());
            report.setStatus("SUBMITTED");
            report.setIsOverdue(isOverdue);
            report.setOverdueDays(overdueDays);
            report.setSubmitTime(now);

            if (report.getId() == null) {
                weeklyReportMapper.insert(report);
            } else {
                weeklyReportMapper.updateById(report);
            }

            // 首次正式提交与退回重提均必须留存 SUBMIT 历史快照 (统一历史动作: SUBMIT, APPROVE, RETURN)
            saveHistorySnapshot(report, "SUBMIT", loginUser.getUserId(), loginUser.getRealName(), "STUDENT", null);

            return convertToVO(report, task.getTaskName(), loginUser.getRealName());
        }

        throw new BusinessException(400, "非法的周报操作动作 (仅支持 DRAFT 或 SUBMIT)");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WeeklyReportDetailVO reviewReport(Long id, WeeklyReportReviewDTO dto, LoginUser loginUser) {
        if (!"TEACHER".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅指导教师允许批阅周报");
        }

        InternshipWeeklyReport report = weeklyReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(400, "周报不存在");
        }

        // 越权防御：只能批阅分配给本人的学生周报 (TEST-W07)
        if (!loginUser.getUserId().equals(report.getTeacherId())) {
            throw new BusinessException(403, "无权批阅非本人负责的学生周报");
        }

        if ("REVIEWED".equals(report.getStatus())) {
            throw new BusinessException(400, "该周报已批阅锁定，禁止重复批阅");
        }
        if ("DRAFT".equals(report.getStatus())) {
            throw new BusinessException(400, "该周报处于草稿状态，学生尚未提交");
        }
        if ("RETURNED".equals(report.getStatus())) {
            throw new BusinessException(400, "该周报处于已退回状态，等待学生重新提交");
        }

        String action = dto.getAction().trim().toUpperCase();

        // 批阅通过 (APPROVE) (TEST-W11)
        if ("APPROVE".equals(action)) {
            if (dto.getScore() == null || dto.getScore().compareTo(BigDecimal.ZERO) < 0 || dto.getScore().compareTo(new BigDecimal("100")) > 0) {
                throw new BusinessException(400, "批阅得分必须在 0.00 至 100.00 之间");
            }
            int minCommentLen = weeklyProperties.getMinReviewCommentLength();
            validateLength(dto.getReviewComment(), minCommentLen, "导师指导评语");

            report.setStatus("REVIEWED");
            report.setScore(dto.getScore().setScale(2, RoundingMode.HALF_UP));
            report.setReviewComment(dto.getReviewComment().trim());
            report.setReviewAnnotations(dto.getReviewAnnotations());
            report.setReviewerId(loginUser.getUserId());
            report.setReviewTime(LocalDateTime.now());
            weeklyReportMapper.updateById(report);

            saveHistorySnapshot(report, "APPROVE", loginUser.getUserId(), loginUser.getRealName(), "TEACHER", null);
            return getReportDetail(report.getId(), loginUser);
        }

        // 退回修改 (RETURN) (TEST-W08, TEST-W09)
        if ("RETURN".equals(action)) {
            int minReasonLen = weeklyProperties.getMinReturnReasonLength();
            validateLength(dto.getReviewComment(), minReasonLen, "退回修改原因");

            report.setStatus("RETURNED");
            report.setReviewComment(dto.getReviewComment().trim());
            report.setReviewerId(loginUser.getUserId());
            report.setReviewTime(LocalDateTime.now());
            weeklyReportMapper.updateById(report);

            saveHistorySnapshot(report, "RETURN", loginUser.getUserId(), loginUser.getRealName(), "TEACHER", dto.getReviewComment().trim());

            // NOTICE-006 状态提示与审计留痕
            logService.logOperation("周报退回提示", "WEEKLY_REPORT", "reviewReport", "POST",
                    loginUser.getUserId(), loginUser.getRealName(), "/api/v1/internship/weekly-reports/" + id + "/review",
                    "127.0.0.1", "reportId=" + id, "已记录周报退回状态提示与审计日志", 0, null);

            return getReportDetail(report.getId(), loginUser);
        }

        throw new BusinessException(400, "非法的批阅动作 (仅支持 APPROVE 或 RETURN)");
    }

    @Override
    public List<WeeklyReportVO> listReports(Long taskId, Long studentId, String status, LoginUser loginUser) {
        LambdaQueryWrapper<InternshipWeeklyReport> qw = new LambdaQueryWrapper<>();
        qw.eq(taskId != null, InternshipWeeklyReport::getTaskId, taskId);
        qw.eq(StringUtils.hasText(status), InternshipWeeklyReport::getStatus, status);

        // 行级数据权限隔离
        if ("STUDENT".equals(loginUser.getUserType())) {
            if (studentId != null && !loginUser.getUserId().equals(studentId)) {
                throw new BusinessException(403, "学生仅允许查询本人的周报记录");
            }
            qw.eq(InternshipWeeklyReport::getStudentId, loginUser.getUserId());
        } else if ("TEACHER".equals(loginUser.getUserType())) {
            qw.eq(InternshipWeeklyReport::getTeacherId, loginUser.getUserId());
            qw.eq(studentId != null, InternshipWeeklyReport::getStudentId, studentId);
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            qw.eq(InternshipWeeklyReport::getDeptId, loginUser.getDeptId());
            qw.eq(studentId != null, InternshipWeeklyReport::getStudentId, studentId);
        } else if ("SYS_ADMIN".equals(loginUser.getUserType())) {
            qw.eq(studentId != null, InternshipWeeklyReport::getStudentId, studentId);
        }

        qw.orderByAsc(InternshipWeeklyReport::getWeekNumber);
        List<InternshipWeeklyReport> reports = weeklyReportMapper.selectList(qw);
        if (reports.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量关联学生姓名、任务名称
        Map<Long, String> taskNameMap = taskMapper.selectBatchIds(reports.stream().map(InternshipWeeklyReport::getTaskId).distinct().toList())
                .stream().collect(Collectors.toMap(InternshipTask::getId, InternshipTask::getTaskName, (k1, k2) -> k1));
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(reports.stream().map(InternshipWeeklyReport::getStudentId).distinct().toList())
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u, (k1, k2) -> k1));

        return reports.stream().map(r -> {
            String taskName = taskNameMap.getOrDefault(r.getTaskId(), "");
            SysUser u = userMap.get(r.getStudentId());
            String studentName = u != null ? u.getRealName() : "";
            WeeklyReportVO vo = convertToVO(r, taskName, studentName);
            if (u != null) {
                vo.setStudentNumber(u.getUsername());
            }
            return vo;
        }).toList();
    }

    @Override
    public WeeklyReportDetailVO getReportDetail(Long id, LoginUser loginUser) {
        InternshipWeeklyReport report = weeklyReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(400, "周报不存在");
        }

        // 越权查验
        if ("STUDENT".equals(loginUser.getUserType()) && !loginUser.getUserId().equals(report.getStudentId())) {
            throw new BusinessException(403, "无权查看其他学生的周报");
        }
        if ("TEACHER".equals(loginUser.getUserType()) && !loginUser.getUserId().equals(report.getTeacherId())) {
            throw new BusinessException(403, "无权查看非本人负责学生的周报");
        }
        if ("DEPT_ADMIN".equals(loginUser.getUserType()) && !loginUser.getDeptId().equals(report.getDeptId())) {
            throw new BusinessException(403, "无权跨院系查看周报记录");
        }

        InternshipTask task = taskMapper.selectById(report.getTaskId());
        SysUser student = userMapper.selectById(report.getStudentId());
        SysUser teacher = userMapper.selectById(report.getTeacherId());
        BaseDepartment dept = departmentMapper.selectById(report.getDeptId());

        // 查询版本流转历史
        List<InternshipWeeklyReportHistory> histories = historyMapper.selectList(new LambdaQueryWrapper<InternshipWeeklyReportHistory>()
                .eq(InternshipWeeklyReportHistory::getReportId, report.getId())
                .orderByDesc(InternshipWeeklyReportHistory::getOperateTime));

        WeeklyReportDetailVO detail = WeeklyReportDetailVO.builder()
                .id(report.getId())
                .taskId(report.getTaskId())
                .taskName(task != null ? task.getTaskName() : "")
                .studentId(report.getStudentId())
                .studentName(student != null ? student.getRealName() : "")
                .studentNumber(student != null ? student.getUsername() : "")
                .teacherId(report.getTeacherId())
                .teacherName(teacher != null ? teacher.getRealName() : "")
                .deptId(report.getDeptId())
                .deptName(dept != null ? dept.getDeptName() : "")
                .weekNumber(report.getWeekNumber())
                .startDate(report.getStartDate())
                .endDate(report.getEndDate())
                .deadlineTime(report.getDeadlineTime())
                .workContent(report.getWorkContent())
                .workSummary(report.getWorkSummary())
                .problemEncountered(report.getProblemEncountered())
                .nextWeekPlan(report.getNextWeekPlan())
                .attachmentUrl(report.getAttachmentUrl())
                .version(report.getVersion())
                .status(report.getStatus())
                .isOverdue(report.getIsOverdue())
                .overdueDays(report.getOverdueDays())
                .submitTime(report.getSubmitTime())
                .score(report.getScore())
                .reviewComment(report.getReviewComment())
                .reviewAnnotations(report.getReviewAnnotations())
                .reviewerId(report.getReviewerId())
                .reviewerName(report.getReviewerId() != null && report.getReviewerId().equals(teacher != null ? teacher.getId() : null) ? (teacher != null ? teacher.getRealName() : "") : "")
                .reviewTime(report.getReviewTime())
                .createTime(report.getCreateTime())
                .updateTime(report.getUpdateTime())
                .historyList(histories)
                .build();

        return detail;
    }

    @Override
    public WeeklyMonitorSummaryVO getMonitorSummary(Long taskId, Long deptId, LoginUser loginUser) {
        if ("STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "学生角色无权查看周报监控数据");
        }
        if ("TEACHER".equals(loginUser.getUserType())) {
            if (deptId != null && !loginUser.getDeptId().equals(deptId)) {
                throw new BusinessException(403, "指导教师无权跨院系查看监控数据");
            }
            deptId = loginUser.getDeptId();
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            if (deptId != null && !loginUser.getDeptId().equals(deptId)) {
                throw new BusinessException(403, "院系负责人无权跨院系查看监控大盘"); // TEST-W18
            }
            deptId = loginUser.getDeptId();
        }

        if (taskId == null) {
            throw new BusinessException(400, "请指定需要监控的实习任务 ID");
        }

        InternshipTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(400, "指定的实习任务不存在");
        }
        String taskName = task.getTaskName();

        if (!StringUtils.hasText(task.getWeeklyFrequency())) {
            throw new BusinessException(400, "实习任务周报频次 (weeklyFrequency) 未配置，无法生成监控统计");
        }
        String freq = task.getWeeklyFrequency().trim().toUpperCase();
        if (!"WEEKLY".equals(freq) && !"BIWEEKLY".equals(freq)) {
            throw new BusinessException(400, "实习任务周报频次配置非法（仅支持 WEEKLY 或 BIWEEKLY）");
        }
        if (task.getStartDate() == null || task.getEndDate() == null) {
            throw new BusinessException(400, "实习任务起止日期配置不完整，无法生成监控统计");
        }

        BaseDepartment dept = deptId != null ? departmentMapper.selectById(deptId) : null;
        String deptName = dept != null ? dept.getDeptName() : "全校";

        LambdaQueryWrapper<InternshipWeeklyReport> qw = new LambdaQueryWrapper<>();
        qw.eq(InternshipWeeklyReport::getTaskId, taskId);
        qw.eq(deptId != null, InternshipWeeklyReport::getDeptId, deptId);
        if ("TEACHER".equals(loginUser.getUserType())) {
            qw.eq(InternshipWeeklyReport::getTeacherId, loginUser.getUserId());
        }
        List<InternshipWeeklyReport> allReports = weeklyReportMapper.selectList(qw);

        // 圈定学生总数（按院系及指导教师正确过滤）
        LambdaQueryWrapper<InternshipTaskStudent> sqw = new LambdaQueryWrapper<>();
        sqw.eq(InternshipTaskStudent::getTaskId, taskId);
        if ("TEACHER".equals(loginUser.getUserType())) {
            sqw.eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId());
        }
        List<InternshipTaskStudent> allTaskStudents = taskStudentMapper.selectList(sqw);

        List<InternshipTaskStudent> students;
        final Long filterDeptId = deptId;
        if (filterDeptId != null) {
            List<Long> studentIds = allTaskStudents.stream().map(InternshipTaskStudent::getStudentId).distinct().toList();
            if (studentIds.isEmpty()) {
                students = Collections.emptyList();
            } else {
                Map<Long, SysUser> userMap = userMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u, (k1, k2) -> k1));
                students = allTaskStudents.stream()
                        .filter(ts -> {
                            SysUser u = userMap.get(ts.getStudentId());
                            return u != null && filterDeptId.equals(u.getDeptId());
                        })
                        .toList();
            }
        } else {
            students = allTaskStudents;
        }
        int totalStudents = students.size();

        // 根据任务实际起止日期和 WEEKLY/BIWEEKLY 频次精确计算应交周报总数
        int cycleDays = "BIWEEKLY".equals(freq) ? 14 : 7;
        long totalDays = ChronoUnit.DAYS.between(task.getStartDate(), task.getEndDate()) + 1;
        int expectedWeeks = (int) Math.max(1, (totalDays + cycleDays - 1) / cycleDays);
        int totalExpectedReports = totalStudents * expectedWeeks;

        int totalSubmitted = (int) allReports.stream().filter(r -> !"DRAFT".equals(r.getStatus())).count();
        int totalOnTime = (int) allReports.stream().filter(r -> !"DRAFT".equals(r.getStatus()) && (r.getIsOverdue() == null || r.getIsOverdue() == 0)).count();
        int totalOverdue = (int) allReports.stream().filter(r -> !"DRAFT".equals(r.getStatus()) && (r.getIsOverdue() != null && r.getIsOverdue() == 1)).count();
        int totalReviewed = (int) allReports.stream().filter(r -> "REVIEWED".equals(r.getStatus())).count();
        int totalPending = (int) allReports.stream().filter(r -> "SUBMITTED".equals(r.getStatus())).count();

        BigDecimal submitRate = totalExpectedReports > 0 ? BigDecimal.valueOf(totalSubmitted).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalExpectedReports), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal reviewRate = totalSubmitted > 0 ? BigDecimal.valueOf(totalReviewed).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalSubmitted), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal onTimeRate = totalSubmitted > 0 ? BigDecimal.valueOf(totalOnTime).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalSubmitted), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return WeeklyMonitorSummaryVO.builder()
                .taskId(taskId)
                .taskName(taskName)
                .deptId(deptId)
                .deptName(deptName)
                .totalStudents(totalStudents)
                .totalExpectedReports(totalExpectedReports)
                .totalSubmittedReports(totalSubmitted)
                .totalOnTimeReports(totalOnTime)
                .totalOverdueReports(totalOverdue)
                .totalReviewedReports(totalReviewed)
                .totalPendingReports(totalPending)
                .submissionRate(submitRate)
                .reviewRate(reviewRate)
                .onTimeRate(onTimeRate)
                .weekStats(new ArrayList<>())
                .build();
    }

    private void validateLength(String content, int minLen, String fieldName) {
        if (!StringUtils.hasText(content) || content.trim().length() < minLen) {
            throw new BusinessException(400, fieldName + "去除空格后不得少于 " + minLen + " 字");
        }
    }

    private void validateAttachmentUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        String lower = url.trim().toLowerCase();
        if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".pdf"))) {
            throw new BusinessException(400, "凭证附件仅支持 .jpg, .jpeg, .png, .pdf 格式文件");
        }
    }

    private void saveHistorySnapshot(InternshipWeeklyReport report, String action, Long operatorId, String operatorName, String operatorRole, String returnReason) {
        String json = "";
        try {
            json = objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            log.error("周报序列化快照失败", e);
        }

        InternshipWeeklyReportHistory history = InternshipWeeklyReportHistory.builder()
                .reportId(report.getId())
                .taskId(report.getTaskId())
                .studentId(report.getStudentId())
                .version(report.getVersion())
                .action(action)
                .operatorId(operatorId)
                .operatorName(operatorName)
                .operatorRole(operatorRole)
                .returnReason(returnReason)
                .score(report.getScore())
                .reviewComment(report.getReviewComment())
                .snapshotContent(json)
                .operateTime(LocalDateTime.now())
                .build();
        historyMapper.insert(history);
    }

    private WeeklyReportVO convertToVO(InternshipWeeklyReport r, String taskName, String studentName) {
        return WeeklyReportVO.builder()
                .id(r.getId())
                .taskId(r.getTaskId())
                .taskName(taskName)
                .studentId(r.getStudentId())
                .studentName(studentName)
                .teacherId(r.getTeacherId())
                .deptId(r.getDeptId())
                .weekNumber(r.getWeekNumber())
                .startDate(r.getStartDate())
                .endDate(r.getEndDate())
                .deadlineTime(r.getDeadlineTime())
                .status(r.getStatus())
                .isOverdue(r.getIsOverdue())
                .overdueDays(r.getOverdueDays())
                .submitTime(r.getSubmitTime())
                .score(r.getScore())
                .reviewComment(r.getReviewComment())
                .version(r.getVersion())
                .updateTime(r.getUpdateTime())
                .build();
    }
}
