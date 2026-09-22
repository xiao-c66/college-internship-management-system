package com.college.internship.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.WeeklyProperties;
import com.college.internship.dto.GuidanceCreateDTO;
import com.college.internship.dto.GuidanceFeedbackDTO;
import com.college.internship.entity.BaseClass;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.InternshipApply;
import com.college.internship.entity.InternshipGuidanceRecord;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.InternshipApplyMapper;
import com.college.internship.mapper.InternshipGuidanceRecordMapper;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.InternshipTaskStudentMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IGuidanceRecordService;
import com.college.internship.vo.GuidanceExportVO;
import com.college.internship.vo.GuidanceRecordVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 阶段6 过程指导走访台账与学生反馈服务实现类 (API-065 ~ API-067)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuidanceRecordServiceImpl implements IGuidanceRecordService {

    private final InternshipGuidanceRecordMapper guidanceMapper;
    private final InternshipTaskMapper taskMapper;
    private final InternshipTaskStudentMapper taskStudentMapper;
    private final InternshipApplyMapper applyMapper;
    private final BaseDepartmentMapper departmentMapper;
    private final BaseClassMapper classMapper;
    private final SysUserMapper userMapper;
    private final WeeklyProperties weeklyProperties;

    // 内存级防刷限流表 (用户ID -> 上次请求时间戳毫秒)
    private final Map<Long, Long> exportRateLimitMap = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GuidanceRecordVO createGuidance(GuidanceCreateDTO dto, LoginUser loginUser) {
        if (!"TEACHER".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅指导教师允许登记过程指导记录");
        }

        // 越权防御：教师只能为分配给本人的学生录入指导记录 (TEST-W13)
        InternshipTaskStudent taskStudent = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                .eq(InternshipTaskStudent::getTaskId, dto.getTaskId())
                .eq(InternshipTaskStudent::getStudentId, dto.getStudentId()));
        if (taskStudent == null || !loginUser.getUserId().equals(taskStudent.getTeacherId())) {
            throw new BusinessException(403, "无权为非本人负责的学生录入指导记录");
        }

        // 字数阈值校验
        int minLen = weeklyProperties.getMinGuidanceLength();
        if (!StringUtils.hasText(dto.getContentSummary()) || dto.getContentSummary().trim().length() < minLen) {
            throw new BusinessException(400, "交流内容要点去除空格后不得少于 " + minLen + " 字");
        }

        // 实地走访地点校验
        if ("ONSITE".equalsIgnoreCase(dto.getGuidanceType()) && !StringUtils.hasText(dto.getLocation())) {
            throw new BusinessException(400, "实地走访时走访地点不能为空");
        }

        // 凭证附件 URL 校验 (若有)
        validateAttachmentUrl(dto.getAttachmentUrl());

        SysUser student = userMapper.selectById(dto.getStudentId());
        Long deptId = loginUser.getDeptId() != null ? loginUser.getDeptId() : (student != null ? student.getDeptId() : null);
        if (deptId == null) {
            InternshipTask task = taskMapper.selectById(dto.getTaskId());
            deptId = task != null ? task.getDeptId() : null;
        }
        if (deptId == null) {
            throw new BusinessException(400, "无法确定过程指导记录所属院系");
        }

        InternshipGuidanceRecord record = InternshipGuidanceRecord.builder()
                .taskId(dto.getTaskId())
                .teacherId(loginUser.getUserId())
                .teacherName(loginUser.getRealName())
                .studentId(dto.getStudentId())
                .studentName(student != null ? student.getRealName() : "")
                .deptId(deptId)
                .guidanceDate(dto.getGuidanceDate())
                .guidanceType(dto.getGuidanceType().toUpperCase())
                .contentSummary(dto.getContentSummary().trim())
                .location(dto.getLocation())
                .followupActions(dto.getFollowupActions())
                .attachmentUrl(dto.getAttachmentUrl())
                .feedbackStatus("UNCONFIRMED")
                .build();

        guidanceMapper.insert(record); // (TEST-W12)

        String studentNumber = student != null ? (StringUtils.hasText(student.getUserNumber()) ? student.getUserNumber() : student.getUsername()) : "";
        String className = "";
        if (student != null && student.getClassId() != null) {
            BaseClass bc = classMapper.selectById(student.getClassId());
            if (bc != null) className = bc.getClassName();
        }
        LambdaQueryWrapper<InternshipApply> applyQw = new LambdaQueryWrapper<>();
        applyQw.eq(InternshipApply::getTaskId, dto.getTaskId());
        applyQw.eq(InternshipApply::getStudentId, dto.getStudentId());
        applyQw.orderByDesc(InternshipApply::getId);
        List<InternshipApply> applies = applyMapper.selectList(applyQw);
        String companyName = "";
        if (!applies.isEmpty()) {
            InternshipApply apply = applies.get(0);
            if (StringUtils.hasText(apply.getCompanyName())) {
                companyName = apply.getCompanyName();
            }
            if (!StringUtils.hasText(className) && apply.getClassId() != null) {
                BaseClass bc = classMapper.selectById(apply.getClassId());
                if (bc != null) className = bc.getClassName();
            }
        }
        return convertToVO(record, studentNumber, className, companyName);
    }

    @Override
    public List<GuidanceRecordVO> listGuidances(Long taskId, Long studentId, String guidanceType,
                                               LocalDate startDate, LocalDate endDate, LoginUser loginUser) {
        LambdaQueryWrapper<InternshipGuidanceRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(taskId != null, InternshipGuidanceRecord::getTaskId, taskId);
        qw.eq(StringUtils.hasText(guidanceType), InternshipGuidanceRecord::getGuidanceType, guidanceType);
        if (startDate != null) {
            qw.ge(InternshipGuidanceRecord::getGuidanceDate, startDate.atStartOfDay());
        }
        if (endDate != null) {
            qw.le(InternshipGuidanceRecord::getGuidanceDate, endDate.atTime(23, 59, 59));
        }

        // 数据范围隔离：学生仅能查看本人；教师查看本人负责学生；院系查本院 (TEST-W18)
        if ("STUDENT".equals(loginUser.getUserType())) {
            if (studentId != null && !loginUser.getUserId().equals(studentId)) {
                throw new BusinessException(403, "学生仅允许查询本人的过程指导记录");
            }
            qw.eq(InternshipGuidanceRecord::getStudentId, loginUser.getUserId());
        } else if ("TEACHER".equals(loginUser.getUserType())) {
            qw.eq(InternshipGuidanceRecord::getTeacherId, loginUser.getUserId());
            qw.eq(studentId != null, InternshipGuidanceRecord::getStudentId, studentId);
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            qw.eq(InternshipGuidanceRecord::getDeptId, loginUser.getDeptId());
            qw.eq(studentId != null, InternshipGuidanceRecord::getStudentId, studentId);
        } else if ("SYS_ADMIN".equals(loginUser.getUserType())) {
            qw.eq(studentId != null, InternshipGuidanceRecord::getStudentId, studentId);
        }

        qw.orderByDesc(InternshipGuidanceRecord::getGuidanceDate);
        List<InternshipGuidanceRecord> records = guidanceMapper.selectList(qw);
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        // 补充学生学号、行政班级与实习单位关联数据
        List<Long> studentIds = records.stream().map(InternshipGuidanceRecord::getStudentId).distinct().toList();
        Map<Long, SysUser> studentMap = studentIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(studentIds).stream().collect(Collectors.toMap(SysUser::getId, u -> u, (k1, k2) -> k1));

        List<Long> classIds = studentMap.values().stream()
                .map(SysUser::getClassId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, BaseClass> classMap = classIds.isEmpty() ? Collections.emptyMap() :
                classMapper.selectBatchIds(classIds).stream().collect(Collectors.toMap(BaseClass::getId, c -> c, (k1, k2) -> k1));

        LambdaQueryWrapper<InternshipApply> applyQw = new LambdaQueryWrapper<>();
        applyQw.in(InternshipApply::getStudentId, studentIds);
        List<InternshipApply> applyList = studentIds.isEmpty() ? Collections.emptyList() : applyMapper.selectList(applyQw);
        Map<String, String> companyMap = new java.util.HashMap<>();
        Map<String, String> applyClassMap = new java.util.HashMap<>();
        for (InternshipApply a : applyList) {
            if (StringUtils.hasText(a.getCompanyName())) {
                companyMap.put(a.getTaskId() + "_" + a.getStudentId(), a.getCompanyName());
            }
            if (a.getClassId() != null && !classMap.containsKey(a.getClassId())) {
                BaseClass bc = classMapper.selectById(a.getClassId());
                if (bc != null) {
                    applyClassMap.put(a.getTaskId() + "_" + a.getStudentId(), bc.getClassName());
                }
            }
        }

        return records.stream().map(r -> {
            SysUser u = studentMap.get(r.getStudentId());
            String studentNumber = u != null ? (StringUtils.hasText(u.getUserNumber()) ? u.getUserNumber() : u.getUsername()) : "";
            String className = "";
            if (u != null && u.getClassId() != null && classMap.containsKey(u.getClassId())) {
                className = classMap.get(u.getClassId()).getClassName();
            } else if (applyClassMap.containsKey(r.getTaskId() + "_" + r.getStudentId())) {
                className = applyClassMap.get(r.getTaskId() + "_" + r.getStudentId());
            }
            String companyName = companyMap.getOrDefault(r.getTaskId() + "_" + r.getStudentId(), "");
            return convertToVO(r, studentNumber, className, companyName);
        }).toList();
    }

    @Override
    public void exportGuidanceExcel(Long taskId, Long studentId, String guidanceType,
                                   LocalDate startDate, LocalDate endDate,
                                   HttpServletResponse response, LoginUser loginUser) {
        // 1. 角色权限拦截：学生角色严禁调用导出 (TEST-W17)
        if ("STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "学生角色无权导出过程走访台账数据");
        }

        // 2. 时间跨度限制拦截 (TEST-W17)
        if (startDate != null && endDate != null) {
            long span = ChronoUnit.DAYS.between(startDate, endDate);
            if (span > weeklyProperties.getMaxExportDays()) {
                throw new BusinessException(400, "导出时间跨度超过系统最大允许限制（" + weeklyProperties.getMaxExportDays() + "天），请重新调整开始日期与结束日期");
            }
        }

        // 3. 防刷限流拦截 (TEST-W17)
        long now = System.currentTimeMillis();
        Long lastExport = exportRateLimitMap.get(loginUser.getUserId());
        long limitMs = weeklyProperties.getExportRateLimitSeconds() * 1000L;
        if (lastExport != null && (now - lastExport < limitMs)) {
            throw new BusinessException(429, "导出操作过于频繁，系统限制每位用户 " + weeklyProperties.getExportRateLimitSeconds() + " 秒内仅可发起一次导出请求，请稍后重试");
        }
        exportRateLimitMap.put(loginUser.getUserId(), now);

        // 4. 查询数据
        List<GuidanceRecordVO> list = listGuidances(taskId, studentId, guidanceType, startDate, endDate, loginUser);

        // 5. 单次最大行数限制拦截 (TEST-W17)
        if (list.size() > weeklyProperties.getMaxExportRows()) {
            throw new BusinessException(400, "导出数据量（当前查询共 " + list.size() + " 条）超出单次导出上限（" + weeklyProperties.getMaxExportRows() + "条），请缩窄时间范围或选择特定班级后重试");
        }

        // 6. 构造 EasyExcel 导出模型
        List<GuidanceExportVO> exportDataList = new ArrayList<>();
        int idx = 1;
        for (GuidanceRecordVO r : list) {
            GuidanceExportVO evo = GuidanceExportVO.builder()
                    .index(idx++)
                    .guidanceTime(r.getGuidanceDate() != null ? r.getGuidanceDate().format(DATETIME_FORMATTER) : "")
                    .guidanceType(formatGuidanceType(r.getGuidanceType()))
                    .studentNumber(r.getStudentNumber())
                    .studentName(r.getStudentName())
                    .className(r.getClassName())
                    .companyName(r.getCompanyName())
                    .teacherName(r.getTeacherName())
                    .contentSummary(r.getContentSummary())
                    .location(r.getLocation())
                    .studentFeedback(r.getStudentFeedback())
                    .feedbackTime(r.getFeedbackTime() != null ? r.getFeedbackTime().format(DATETIME_FORMATTER) : "")
                    .feedbackStatus("CONFIRMED".equals(r.getFeedbackStatus()) ? "已确认" : "未确认")
                    .attachmentUrl(r.getAttachmentUrl())
                    .build();
            exportDataList.add(evo);
        }

        // 7. 使用 EasyExcel 输出文件流
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
            String fileName = URLEncoder.encode("高校实习过程指导走访台账", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + ".xlsx\"; filename*=UTF-8''" + fileName + ".xlsx");

            EasyExcel.write(response.getOutputStream(), GuidanceExportVO.class)
                    .sheet("过程指导台账")
                    .doWrite(exportDataList);
        } catch (Exception e) {
            log.error("EasyExcel 导出过程走访台账异常", e);
            throw new BusinessException(500, "EasyExcel 导出走访台账文件生成失败，请联系系统管理员或稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GuidanceRecordVO submitFeedback(Long id, GuidanceFeedbackDTO dto, LoginUser loginUser) {
        if (!"STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅学生允许提交指导记录反馈");
        }

        InternshipGuidanceRecord record = guidanceMapper.selectById(id);
        // 越权校验：非本人记录直接 403 (TEST-W15)
        if (record == null || !loginUser.getUserId().equals(record.getStudentId())) {
            throw new BusinessException(403, "无权操作非本人的过程指导记录");
        }

        // 状态预检：本人已确认过直接 400 (TEST-W16)
        if ("CONFIRMED".equals(record.getFeedbackStatus())) {
            throw new BusinessException(400, "该指导记录已确认反馈并锁定，禁止重复提交");
        }

        // 字数阈值校验
        int minFeedbackLen = weeklyProperties.getMinFeedbackLength();
        if (!StringUtils.hasText(dto.getStudentFeedback()) || dto.getStudentFeedback().trim().length() < minFeedbackLen) {
            throw new BusinessException(400, "在岗反馈内容去除空格后不得少于 " + minFeedbackLen + " 字");
        }

        // CAS 条件更新：防止并发重复提交与覆盖 (TEST-W14, TEST-W16)
        LocalDateTime now = LocalDateTime.now();
        int affected = guidanceMapper.updateFeedbackWithCondition(id, loginUser.getUserId(), dto.getStudentFeedback().trim(), now);
        if (affected == 0) {
            throw new BusinessException(400, "该指导记录已确认反馈并锁定，禁止重复提交");
        }

        record.setStudentFeedback(dto.getStudentFeedback().trim());
        record.setFeedbackTime(now);
        record.setFeedbackStatus("CONFIRMED");

        SysUser student = userMapper.selectById(record.getStudentId());
        String studentNumber = student != null ? (StringUtils.hasText(student.getUserNumber()) ? student.getUserNumber() : student.getUsername()) : loginUser.getUsername();
        String className = "";
        if (student != null && student.getClassId() != null) {
            BaseClass bc = classMapper.selectById(student.getClassId());
            if (bc != null) className = bc.getClassName();
        }
        LambdaQueryWrapper<InternshipApply> applyQw = new LambdaQueryWrapper<>();
        applyQw.eq(InternshipApply::getTaskId, record.getTaskId());
        applyQw.eq(InternshipApply::getStudentId, record.getStudentId());
        applyQw.orderByDesc(InternshipApply::getId);
        List<InternshipApply> applies = applyMapper.selectList(applyQw);
        String companyName = "";
        if (!applies.isEmpty()) {
            InternshipApply apply = applies.get(0);
            if (StringUtils.hasText(apply.getCompanyName())) {
                companyName = apply.getCompanyName();
            }
            if (!StringUtils.hasText(className) && apply.getClassId() != null) {
                BaseClass bc = classMapper.selectById(apply.getClassId());
                if (bc != null) className = bc.getClassName();
            }
        }
        return convertToVO(record, studentNumber, className, companyName);
    }

    private void validateAttachmentUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        String lower = url.trim().toLowerCase();
        if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".pdf"))) {
            throw new BusinessException(400, "走访凭证仅支持 .jpg, .jpeg, .png, .pdf 格式文件");
        }
    }

    private String formatGuidanceType(String type) {
        if ("PHONE".equalsIgnoreCase(type)) return "电话谈话";
        if ("ONLINE".equalsIgnoreCase(type)) return "网络连线";
        if ("ONSITE".equalsIgnoreCase(type)) return "实地走访";
        if ("EMAIL_OTHER".equalsIgnoreCase(type)) return "其他交流";
        return type;
    }

    private GuidanceRecordVO convertToVO(InternshipGuidanceRecord r, String studentNumber, String className, String companyName) {
        return GuidanceRecordVO.builder()
                .id(r.getId())
                .taskId(r.getTaskId())
                .teacherId(r.getTeacherId())
                .teacherName(r.getTeacherName())
                .studentId(r.getStudentId())
                .studentName(r.getStudentName())
                .studentNumber(studentNumber)
                .className(className)
                .companyName(companyName)
                .deptId(r.getDeptId())
                .guidanceDate(r.getGuidanceDate())
                .guidanceType(r.getGuidanceType())
                .contentSummary(r.getContentSummary())
                .studentFeedback(r.getStudentFeedback())
                .feedbackTime(r.getFeedbackTime())
                .feedbackStatus(r.getFeedbackStatus())
                .followupActions(r.getFollowupActions())
                .location(r.getLocation())
                .attachmentUrl(r.getAttachmentUrl())
                .createTime(r.getCreateTime())
                .build();
    }
}
