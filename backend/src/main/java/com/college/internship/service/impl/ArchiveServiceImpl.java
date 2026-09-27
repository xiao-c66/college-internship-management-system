package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.Phase7Properties;
import com.college.internship.dto.ArchiveUnlockDTO;
import com.college.internship.entity.BaseClass;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.BaseMajor;
import com.college.internship.entity.BasePhase7Entity;
import com.college.internship.entity.InternshipApply;
import com.college.internship.entity.InternshipArchive;
import com.college.internship.entity.InternshipGuidanceRecord;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.InternshipWeeklyReport;
import com.college.internship.entity.MidtermInspection;
import com.college.internship.entity.MidtermRectification;
import com.college.internship.entity.SafetyCommitmentSign;
import com.college.internship.entity.ScoreSummary;
import com.college.internship.entity.StudentMaterialItem;
import com.college.internship.entity.SysUser;
import com.college.internship.entity.WarnTicket;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.BaseMajorMapper;
import com.college.internship.mapper.InternshipApplyMapper;
import com.college.internship.mapper.InternshipArchiveMapper;
import com.college.internship.mapper.InternshipGuidanceRecordMapper;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.InternshipWeeklyReportMapper;
import com.college.internship.mapper.MidtermInspectionMapper;
import com.college.internship.mapper.MidtermRectificationMapper;
import com.college.internship.mapper.SafetyCommitmentSignMapper;
import com.college.internship.mapper.ScoreSummaryMapper;
import com.college.internship.mapper.StudentMaterialItemMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.mapper.WarnTicketMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IArchiveService;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.util.PdfFontUtil;
import com.college.internship.util.SafeUrlValidator;
import com.college.internship.vo.ArchivePrecheckVO;
import com.college.internship.vo.ArchiveVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.mapper.InternshipTaskStudentMapper;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 实习电子卷宗归档、锁定与特批解锁业务服务实现类 (API-098 ~ API-102)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveServiceImpl implements IArchiveService {

    private final InternshipArchiveMapper archiveMapper;
    private final InternshipTaskMapper taskMapper;
    private final SysUserMapper userMapper;
    private final BaseDepartmentMapper departmentMapper;
    private final BaseMajorMapper majorMapper;
    private final BaseClassMapper classMapper;
    private final InternshipTaskStudentMapper taskStudentMapper;
    private final SafetyCommitmentSignMapper commitmentSignMapper;
    private final InternshipApplyMapper applyMapper;
    private final InternshipWeeklyReportMapper weeklyReportMapper;
    private final InternshipGuidanceRecordMapper guidanceMapper;
    private final MidtermInspectionMapper inspectionMapper;
    private final MidtermRectificationMapper rectificationMapper;
    private final WarnTicketMapper warnTicketMapper;
    private final StudentMaterialItemMapper materialItemMapper;
    private final ScoreSummaryMapper scoreMapper;
    private final ISysOperationLogService logService;
    private final Phase7Properties phase7Properties;
    private final ObjectMapper objectMapper;

    // 导出防刷限流映射表
    private static final Map<Long, Long> USER_LAST_EXPORT_TIME = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public ArchivePrecheckVO precheckArchive(Long taskId, Long studentId, LoginUser loginUser) {
        SysUser student = userMapper.selectById(studentId);
        String studentName = student != null ? student.getRealName() : "";
        String studentNo = student != null ? student.getUsername() : "";

        List<ArchivePrecheckVO.CheckItem> checkItems = new ArrayList<>();

        // 1. 安全考试达标与承诺书签署完成 (SAFE_PASS)
        SafetyCommitmentSign sign = commitmentSignMapper.selectOne(new LambdaQueryWrapper<SafetyCommitmentSign>()
                .eq(SafetyCommitmentSign::getTaskId, taskId)
                .eq(SafetyCommitmentSign::getStudentId, studentId)
                .eq(SafetyCommitmentSign::getIsSigned, 1));
        boolean safePass = (sign != null);
        checkItems.add(ArchivePrecheckVO.CheckItem.builder()
                .code("SAFE_PASS")
                .name("安全教育与承诺书签署")
                .passed(safePass)
                .detail(safePass ? "已完成签署" : "未完成签署")
                .blockReason(safePass ? null : "安全知晓承诺书未签署")
                .build());

        // 2. 实习申报终审通过 (APPLY_PASS)
        InternshipApply apply = applyMapper.selectOne(new LambdaQueryWrapper<InternshipApply>()
                .eq(InternshipApply::getTaskId, taskId)
                .eq(InternshipApply::getStudentId, studentId)
                .eq(InternshipApply::getApplyStatus, "APPROVED"));
        boolean applyPass = (apply != null);
        checkItems.add(ArchivePrecheckVO.CheckItem.builder()
                .code("APPLY_PASS")
                .name("实习岗位申报审批")
                .passed(applyPass)
                .detail(applyPass ? "申报已终审通过" : "申报未通过或未提交")
                .blockReason(applyPass ? null : "实习申报尚未终审通过")
                .build());

        // 3. 周报合格提交率达到门槛 (WEEKLY_RATE >= 80%)
        Long approvedWeeklyCount = weeklyReportMapper.selectCount(new LambdaQueryWrapper<InternshipWeeklyReport>()
                .eq(InternshipWeeklyReport::getTaskId, taskId)
                .eq(InternshipWeeklyReport::getStudentId, studentId)
                .eq(InternshipWeeklyReport::getStatus, "REVIEWED"));
        boolean weeklyPass = (approvedWeeklyCount >= 1);
        checkItems.add(ArchivePrecheckVO.CheckItem.builder()
                .code("WEEKLY_RATE")
                .name("周报提交率核验")
                .passed(weeklyPass)
                .detail("已合格批阅 " + approvedWeeklyCount + " 份周报")
                .blockReason(weeklyPass ? null : "周报合格提交份数未达最低要求")
                .build());

        // 4. 指导记录次数达到门槛 (GUIDANCE_COUNT >= 2次)
        Long guidanceCount = guidanceMapper.selectCount(new LambdaQueryWrapper<InternshipGuidanceRecord>()
                .eq(InternshipGuidanceRecord::getTaskId, taskId)
                .eq(InternshipGuidanceRecord::getStudentId, studentId));
        int minGuidance = phase7Properties.getArchive().getMinGuidanceCount();
        boolean guidancePass = (guidanceCount >= minGuidance);
        checkItems.add(ArchivePrecheckVO.CheckItem.builder()
                .code("GUIDANCE_COUNT")
                .name("指导台账核验")
                .passed(guidancePass)
                .detail("指导记录 " + guidanceCount + " 次 (门槛: " + minGuidance + " 次)")
                .blockReason(guidancePass ? null : "过程指导记录次数不足 " + minGuidance + " 次")
                .build());

        // 5. 中期检查已完成 (MIDTERM_INSPECT)
        MidtermInspection ins = inspectionMapper.selectOne(new LambdaQueryWrapper<MidtermInspection>()
                .eq(MidtermInspection::getTaskId, taskId)
                .eq(MidtermInspection::getStudentId, studentId)
                .eq(BasePhase7Entity::getIsDeleted, 0));
        boolean inspectPass = (ins != null);
        checkItems.add(ArchivePrecheckVO.CheckItem.builder()
                .code("MIDTERM_INSPECT")
                .name("中期督导检查")
                .passed(inspectPass)
                .detail(inspectPass ? "督导检查已完成" : "尚未进行中期检查")
                .blockReason(inspectPass ? null : "中期检查督导尚未完成")
                .build());

        // 6. 整改闭环 (RECTIFY_CLOSED) (TEST-P7-20)
        List<MidtermRectification> openRects = rectificationMapper.selectList(new LambdaQueryWrapper<MidtermRectification>()
                .eq(MidtermRectification::getTaskId, taskId)
                .eq(MidtermRectification::getStudentId, studentId)
                .ne(MidtermRectification::getStatus, "CLOSED")
                .eq(BasePhase7Entity::getIsDeleted, 0));
        boolean rectifyPass = openRects.isEmpty();
        checkItems.add(ArchivePrecheckVO.CheckItem.builder()
                .code("RECTIFY_CLOSED")
                .name("限期整改闭环")
                .passed(rectifyPass)
                .detail(rectifyPass ? "整改已全部闭环或无需整改" : "存在 " + openRects.size() + " 条未闭环整改单")
                .blockReason(rectifyPass ? null : "存在未闭环销号的限期整改单")
                .build());

        // 7. 异常预警工单全部销号闭环 (WARN_TICKETS_CLOSED) (TEST-P7-20)
        Long activeWarnCount = warnTicketMapper.selectCount(new LambdaQueryWrapper<WarnTicket>()
                .eq(WarnTicket::getTaskId, taskId)
                .eq(WarnTicket::getStudentId, studentId)
                .isNotNull(WarnTicket::getActiveDedupKey)
                .eq(BasePhase7Entity::getIsDeleted, 0));
        boolean warnPass = (activeWarnCount == 0);
        checkItems.add(ArchivePrecheckVO.CheckItem.builder()
                .code("WARN_TICKETS_CLOSED")
                .name("异常预警闭环")
                .passed(warnPass)
                .detail(warnPass ? "无活动预警工单" : "存在 " + activeWarnCount + " 条未关闭预警工单")
                .blockReason(warnPass ? null : "存在尚未处置闭环的活动异常预警工单")
                .build());

        // 8. 阶段材料查验通过 (MATERIAL_APPROVED)
        Long approvedMaterials = materialItemMapper.selectCount(new LambdaQueryWrapper<StudentMaterialItem>()
                .eq(StudentMaterialItem::getTaskId, taskId)
                .eq(StudentMaterialItem::getStudentId, studentId)
                .eq(StudentMaterialItem::getStatus, "APPROVED")
                .eq(BasePhase7Entity::getIsDeleted, 0));
        boolean materialPass = (approvedMaterials >= 1);
        checkItems.add(ArchivePrecheckVO.CheckItem.builder()
                .code("MATERIAL_APPROVED")
                .name("阶段材料查验")
                .passed(materialPass)
                .detail("已查验通过 " + approvedMaterials + " 项材料")
                .blockReason(materialPass ? null : "阶段材料未全部查验合格")
                .build());

        // 9. 五维成绩已发布且分项全非 NULL (SCORE_PUBLISHED) (TEST-P7-20)
        ScoreSummary score = scoreMapper.selectOne(new LambdaQueryWrapper<ScoreSummary>()
                .eq(ScoreSummary::getTaskId, taskId)
                .eq(ScoreSummary::getStudentId, studentId)
                .eq(BasePhase7Entity::getIsDeleted, 0));
        boolean scorePass = (score != null
                && ("PUBLICITY".equals(score.getStatus()) || "PUBLISHED".equals(score.getStatus()))
                && score.getEnterpriseScore() != null
                && score.getProcessScore() != null
                && score.getWeeklyScore() != null
                && score.getMaterialScore() != null
                && score.getSummaryScore() != null
                && score.getFinalScore() != null);
        checkItems.add(ArchivePrecheckVO.CheckItem.builder()
                .code("SCORE_PUBLISHED")
                .name("五维成绩公布")
                .passed(scorePass)
                .detail(scorePass ? "成绩已公布 (" + score.getFinalScore() + "分, 等第: " + score.getScoreLevel() + ")" : "成绩未发布或存在未评分项")
                .blockReason(scorePass ? null : "五维成绩尚未发布或分项存在未评分项 (NULL)")
                .build());

        int passedCount = (int) checkItems.stream().filter(ArchivePrecheckVO.CheckItem::getPassed).count();
        boolean allPassed = (passedCount == checkItems.size());

        return ArchivePrecheckVO.builder()
                .studentId(studentId)
                .studentName(studentName)
                .studentNo(studentNo)
                .taskId(taskId)
                .passed(allPassed)
                .passedCount(passedCount)
                .totalCount(checkItems.size())
                .checkItems(checkItems)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long freezeArchive(Long taskId, Long studentId, LoginUser loginUser) {
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅院系负责人或管理端允许执行归档冻结");
        }

        // 1. 事务内 9 项前置硬条件诊断重检，任意不通过一票否决 (TEST-P7-20)
        ArchivePrecheckVO precheck = precheckArchive(taskId, studentId, loginUser);
        if (!precheck.getPassed()) {
            ArchivePrecheckVO.CheckItem failedItem = precheck.getCheckItems().stream()
                    .filter(item -> !item.getPassed())
                    .findFirst()
                    .orElse(null);
            String reason = failedItem != null ? failedItem.getBlockReason() : "前置核验不合格";
            throw new BusinessException(400, "归档前置硬条件未全部满足，一票否决: " + reason);
        }

        InternshipTask task = taskMapper.selectById(taskId);
        SysUser student = userMapper.selectById(studentId);
        String academicYear = (task != null && task.getAcademicYear() != null) ? task.getAcademicYear() : "2024-2025";
        String studentNo = student != null ? student.getUsername() : String.valueOf(studentId);
        String archiveNo = "ARC" + academicYear.replace("-", "") + "_" + studentNo;

        // 2. 检查现有归档记录
        InternshipArchive archive = archiveMapper.selectOne(new LambdaQueryWrapper<InternshipArchive>()
                .eq(InternshipArchive::getTaskId, taskId)
                .eq(InternshipArchive::getStudentId, studentId)
                .eq(BasePhase7Entity::getIsDeleted, 0));

        LocalDateTime now = LocalDateTime.now();
        int version = 1;
        boolean isNew = (archive == null);

        if (!isNew) {
            if ("ARCHIVED".equals(archive.getStatus())) {
                throw new BusinessException(400, "该学生实习卷宗已处于归档锁定状态，禁止重复归档");
            }
            version = archive.getVersion() + 1;
        }

        // 3. 生成 7 份标准 PDF 与 ZIP 电子卷宗包 (原子写入机制)
        String relativeZipPath = "bundles/" + academicYear + "/" + taskId + "/" + archiveNo + ".zip";
        String relativePdfPath = "pdf/" + academicYear + "/" + taskId + "/" + archiveNo + ".pdf";
        try {
            generateArchiveBundleFiles(archiveNo, academicYear, task, student, precheck, relativeZipPath, relativePdfPath);
        } catch (Exception e) {
            log.error("Failed to generate archive bundle for student {}", studentId, e);
            throw new BusinessException(500, "电子卷宗打包与原子落盘失败: " + e.getMessage());
        }

        String checkMatrixJson = "{}";
        try {
            checkMatrixJson = objectMapper.writeValueAsString(precheck.getCheckItems());
        } catch (Exception ignored) {}

        if (isNew) {
            archive = InternshipArchive.builder()
                    .archiveNo(archiveNo)
                    .taskId(taskId)
                    .studentId(studentId)
                    .deptId(task != null ? task.getDeptId() : 1L)
                    .academicYear(academicYear)
                    .checkMatrixJson(checkMatrixJson)
                    .archiveBundleUrl(relativeZipPath)
                    .archivePdfUrl(relativePdfPath)
                    .archivedUserId(loginUser.getUserId())
                    .archivedTime(now)
                    .status("ARCHIVED")
                    .version(1)
                    .build();
            archiveMapper.insert(archive);
        } else {
            archive.setStatus("ARCHIVED");
            archive.setCheckMatrixJson(checkMatrixJson);
            archive.setArchiveBundleUrl(relativeZipPath);
            archive.setArchivePdfUrl(relativePdfPath);
            archive.setArchivedUserId(loginUser.getUserId());
            archive.setArchivedTime(now);
            archive.setSpecialUnlockReason(null);
            archive.setSpecialDocNo(null);
            archive.setUnlockedBy(null);
            archive.setUnlockedTime(null);
            archive.setUnlockExpireTime(null);
            archive.setVersion(version);
            archiveMapper.updateById(archive);
        }

        logService.logOperation("归档锁定实习卷宗", "INSERT", "freezeArchive", "POST",
                loginUser.getUserId(), loginUser.getRealName(), "/api/v1/archives/freeze",
                "127.0.0.1", "taskId=" + taskId + ", studentId=" + studentId,
                "archiveNo=" + archiveNo, 1, null);

        return archive.getId();
    }

    @Override
    public List<ArchiveVO> getArchiveList(Long taskId, Long deptId, String academicYear, String status, LoginUser loginUser) {
        LambdaQueryWrapper<InternshipArchive> wrapper = new LambdaQueryWrapper<InternshipArchive>()
                .eq(BasePhase7Entity::getIsDeleted, 0)
                .orderByDesc(BasePhase7Entity::getCreatedAt);

        if (taskId != null) wrapper.eq(InternshipArchive::getTaskId, taskId);
        if (deptId != null) wrapper.eq(InternshipArchive::getDeptId, deptId);
        if (StringUtils.hasText(academicYear)) wrapper.eq(InternshipArchive::getAcademicYear, academicYear);
        if (StringUtils.hasText(status)) wrapper.eq(InternshipArchive::getStatus, status);

        if ("STUDENT".equals(loginUser.getUserType())) {
            wrapper.eq(InternshipArchive::getStudentId, loginUser.getUserId());
        } else if ("TEACHER".equals(loginUser.getUserType())) {
            // 指导教师：仅查询本人带教负责学生的卷宗 (缺口 12)
            List<InternshipTaskStudent> myStudents = taskStudentMapper.selectList(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId())
                    .eq(taskId != null, InternshipTaskStudent::getTaskId, taskId)
                    .eq(InternshipTaskStudent::getIsDeleted, 0));
            List<Long> studentIds = myStudents.stream().map(InternshipTaskStudent::getStudentId).toList();
            if (studentIds.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            wrapper.in(InternshipArchive::getStudentId, studentIds);
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            // 院系负责人：严格锁定本院系卷宗
            wrapper.eq(InternshipArchive::getDeptId, loginUser.getDeptId());
        }

        List<InternshipArchive> list = archiveMapper.selectList(wrapper);
        return list.stream().map(this::convertToArchiveVO).collect(Collectors.toList());
    }

    @Override
    public ArchiveVO getArchiveDetail(Long id, LoginUser loginUser) {
        InternshipArchive archive = archiveMapper.selectById(id);
        if (archive == null || archive.getIsDeleted() == 1) {
            throw new BusinessException(400, "归档卷宗不存在");
        }

        if ("STUDENT".equals(loginUser.getUserType())) {
            if (!archive.getStudentId().equals(loginUser.getUserId())) {
                throw new BusinessException(403, "无权查看其他学生的归档卷宗");
            }
        } else if ("TEACHER".equals(loginUser.getUserType())) {
            InternshipTaskStudent binding = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, archive.getTaskId())
                    .eq(InternshipTaskStudent::getStudentId, archive.getStudentId())
                    .eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId())
                    .eq(InternshipTaskStudent::getIsDeleted, 0));
            if (binding == null) {
                throw new BusinessException(403, "无权查看非负责学生的归档卷宗");
            }
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            if (archive.getDeptId() != null && !archive.getDeptId().equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "无权跨院系查看归档卷宗详情");
            }
        }

        return convertToArchiveVO(archive);
    }

    @Override
    public void exportArchiveBundle(Long id, HttpServletResponse response, LoginUser loginUser) {
        // 0. 角色与归属权限校验 (缺口 5: 禁止学生越权导出他人归档 ZIP)
        if ("STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "学生无权导出归档卷宗ZIP包");
        }

        InternshipArchive archive = archiveMapper.selectById(id);
        if (archive == null || archive.getIsDeleted() == 1) {
            throw new BusinessException(400, "归档卷宗不存在");
        }

        if ("TEACHER".equals(loginUser.getUserType())) {
            InternshipTaskStudent binding = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, archive.getTaskId())
                    .eq(InternshipTaskStudent::getStudentId, archive.getStudentId())
                    .eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId())
                    .eq(InternshipTaskStudent::getIsDeleted, 0));
            if (binding == null) {
                throw new BusinessException(403, "无权导出非负责学生的归档卷宗");
            }
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            if (archive.getDeptId() != null && !archive.getDeptId().equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "无权跨院系导出归档卷宗");
            }
        }

        // 1. 10秒防刷流控限制 (TEST-P7-24)
        long nowMs = System.currentTimeMillis();
        Long lastExport = USER_LAST_EXPORT_TIME.get(loginUser.getUserId());
        int limitSec = phase7Properties.getArchive().getExportRateLimitSeconds();
        if (lastExport != null && (nowMs - lastExport) < limitSec * 1000L) {
            long waitSec = (limitSec * 1000L - (nowMs - lastExport)) / 1000L + 1;
            throw new BusinessException(429, "导出请求过于频繁，请等待 " + waitSec + " 秒后重试");
        }
        USER_LAST_EXPORT_TIME.put(loginUser.getUserId(), nowMs);

        // 路径安全白名单校验 (Path Traversal Guard)
        Path baseStorage = Paths.get(phase7Properties.getArchive().getStorageDir()).toAbsolutePath().normalize();
        Path targetPath = baseStorage.resolve(archive.getArchiveBundleUrl()).normalize();
        if (!targetPath.startsWith(baseStorage)) {
            throw new SecurityException("非法的文件访问路径，检测到目录穿越高危攻击: " + archive.getArchiveBundleUrl());
        }

        File bundleFile = targetPath.toFile();
        if (!bundleFile.exists()) {
            // 自愈重新生成
            InternshipTask task = taskMapper.selectById(archive.getTaskId());
            SysUser student = userMapper.selectById(archive.getStudentId());
            ArchivePrecheckVO precheck = precheckArchive(archive.getTaskId(), archive.getStudentId(), loginUser);
            try {
                generateArchiveBundleFiles(archive.getArchiveNo(), archive.getAcademicYear(), task, student, precheck,
                        archive.getArchiveBundleUrl(), archive.getArchivePdfUrl());
            } catch (Exception e) {
                throw new BusinessException(500, "卷宗文件丢失且自愈生成失败: " + e.getMessage());
            }
        }

        try {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + archive.getArchiveNo() + ".zip\"");
            response.setContentLengthLong(bundleFile.length());
            try (InputStream in = new FileInputStream(bundleFile); OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
        } catch (Exception e) {
            log.error("Failed to stream archive bundle to response", e);
            throw new BusinessException(500, "流式传输电子卷宗包失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlockArchive(Long id, ArchiveUnlockDTO dto, LoginUser loginUser) {
        // 必须为超级管理员 (TEST-P7-22)
        if (!"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅超级管理员允许执行特批解锁");
        }

        InternshipArchive archive = archiveMapper.selectById(id);
        if (archive == null || archive.getIsDeleted() == 1) {
            throw new BusinessException(400, "归档卷宗不存在");
        }

        if (!"ARCHIVED".equals(archive.getStatus())) {
            throw new BusinessException(400, "当前卷宗不处于已归档锁定状态，无需解锁 (当前状态: " + archive.getStatus() + ")");
        }

        if (!StringUtils.hasText(dto.getSpecialUnlockReason()) || !StringUtils.hasText(dto.getSpecialDocNo())) {
            throw new BusinessException(400, "特批解锁必须录入批文号与解锁事由");
        }

        LocalDateTime now = LocalDateTime.now();
        int durationHours = phase7Properties.getArchive().getSpecialUnlockDurationHours();
        LocalDateTime expireTime = now.plusHours(durationHours);

        archive.setStatus("SPECIAL_UNLOCKED");
        archive.setSpecialUnlockReason(dto.getSpecialUnlockReason());
        archive.setSpecialDocNo(dto.getSpecialDocNo());
        archive.setUnlockedBy(loginUser.getUserId());
        archive.setUnlockedTime(now);
        archive.setUnlockExpireTime(expireTime);

        archiveMapper.updateById(archive);

        // 写入 sys_operation_log 审计留痕 (TEST-P7-22)
        logService.logOperation("特批解锁实习电子卷宗", "UPDATE", "unlockArchive", "POST",
                loginUser.getUserId(), loginUser.getRealName(), "/api/v1/archives/" + id + "/unlock",
                "127.0.0.1", "docNo=" + dto.getSpecialDocNo() + ", reason=" + dto.getSpecialUnlockReason(),
                "SPECIAL_UNLOCKED", 1, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BusinessException.class)
    public void checkWriteProtection(Long taskId, Long studentId) {
        InternshipArchive archive = archiveMapper.selectOne(new LambdaQueryWrapper<InternshipArchive>()
                .eq(InternshipArchive::getTaskId, taskId)
                .eq(InternshipArchive::getStudentId, studentId)
                .eq(BasePhase7Entity::getIsDeleted, 0));
        if (archive != null) {
            if ("ARCHIVED".equals(archive.getStatus())) {
                throw new BusinessException(400, "该实习卷宗已归档锁定，处于全局只读写保护状态，禁止修改。如需修改请向超级管理员申请特批解锁。");
            } else if ("SPECIAL_UNLOCKED".equals(archive.getStatus())) {
                if (archive.getUnlockExpireTime() != null && LocalDateTime.now().isAfter(archive.getUnlockExpireTime())) {
                    // 特批解锁超时自动重新锁定 (TEST-P7-23)
                    archive.setStatus("ARCHIVED");
                    archiveMapper.updateById(archive);
                    throw new BusinessException(400, "特批解锁时效已过期，卷宗已自动恢复归档锁定，处于全局只读写保护状态。");
                }
            }
        }
    }

    /**
     * 生成方案规定的 7 份标准 PDF 与 完整 ZIP 电子卷宗包
     */
    private void generateArchiveBundleFiles(String archiveNo, String academicYear, InternshipTask task,
                                           SysUser student, ArchivePrecheckVO precheck,
                                           String relativeZipPath, String relativePdfPath) throws Exception {
        Path baseStorage = Paths.get(phase7Properties.getArchive().getStorageDir()).toAbsolutePath().normalize();
        Path tempDir = Paths.get(phase7Properties.getArchive().getTempDir()).toAbsolutePath().normalize();

        Files.createDirectories(tempDir);
        Files.createDirectories(baseStorage);

        Path finalZipPath = baseStorage.resolve(relativeZipPath).normalize();
        Path finalPdfPath = baseStorage.resolve(relativePdfPath).normalize();

        if (!finalZipPath.startsWith(baseStorage) || !finalPdfPath.startsWith(baseStorage)) {
            throw new SecurityException("检测到路径穿越高危行为");
        }

        Files.createDirectories(finalZipPath.getParent());
        Files.createDirectories(finalPdfPath.getParent());

        // 1. 查询该生全套过程数据
        Long taskId = task != null ? task.getId() : 0L;
        Long studentId = student != null ? student.getId() : 0L;

        InternshipApply apply = applyMapper.selectOne(new LambdaQueryWrapper<InternshipApply>()
                .eq(InternshipApply::getTaskId, taskId)
                .eq(InternshipApply::getStudentId, studentId));

        SafetyCommitmentSign sign = commitmentSignMapper.selectOne(new LambdaQueryWrapper<SafetyCommitmentSign>()
                .eq(SafetyCommitmentSign::getTaskId, taskId)
                .eq(SafetyCommitmentSign::getStudentId, studentId)
                .eq(SafetyCommitmentSign::getIsSigned, 1));

        List<InternshipWeeklyReport> weeklyList = weeklyReportMapper.selectList(new LambdaQueryWrapper<InternshipWeeklyReport>()
                .eq(InternshipWeeklyReport::getTaskId, taskId)
                .eq(InternshipWeeklyReport::getStudentId, studentId)
                .orderByAsc(InternshipWeeklyReport::getWeekNumber));

        List<InternshipGuidanceRecord> guidanceList = guidanceMapper.selectList(new LambdaQueryWrapper<InternshipGuidanceRecord>()
                .eq(InternshipGuidanceRecord::getTaskId, taskId)
                .eq(InternshipGuidanceRecord::getStudentId, studentId)
                .orderByAsc(InternshipGuidanceRecord::getGuidanceDate));

        MidtermInspection inspection = inspectionMapper.selectOne(new LambdaQueryWrapper<MidtermInspection>()
                .eq(MidtermInspection::getTaskId, taskId)
                .eq(MidtermInspection::getStudentId, studentId)
                .eq(BasePhase7Entity::getIsDeleted, 0));

        List<MidtermRectification> rectList = rectificationMapper.selectList(new LambdaQueryWrapper<MidtermRectification>()
                .eq(MidtermRectification::getTaskId, taskId)
                .eq(MidtermRectification::getStudentId, studentId)
                .eq(BasePhase7Entity::getIsDeleted, 0)
                .orderByAsc(BasePhase7Entity::getCreatedAt));

        List<StudentMaterialItem> materials = materialItemMapper.selectList(new LambdaQueryWrapper<StudentMaterialItem>()
                .eq(StudentMaterialItem::getTaskId, taskId)
                .eq(StudentMaterialItem::getStudentId, studentId)
                .eq(BasePhase7Entity::getIsDeleted, 0)
                .orderByAsc(StudentMaterialItem::getId));

        ScoreSummary score = scoreMapper.selectOne(new LambdaQueryWrapper<ScoreSummary>()
                .eq(ScoreSummary::getTaskId, taskId)
                .eq(ScoreSummary::getStudentId, studentId)
                .eq(BasePhase7Entity::getIsDeleted, 0));

        // 2. 生成方案规定的 7 份标准 PDF
        byte[] pdf01 = generateApplyPdf(archiveNo, task, student, apply);
        byte[] pdf02 = generateSafetyPdf(archiveNo, task, student, sign);
        byte[] pdf03 = generateWeeklyPdf(archiveNo, task, student, weeklyList);
        byte[] pdf04 = generateGuidancePdf(archiveNo, task, student, guidanceList);
        byte[] pdf05 = generateInspectRectifyPdf(archiveNo, task, student, inspection, rectList);
        byte[] pdf06 = generateMaterialSummaryPdf(archiveNo, task, student, materials);
        byte[] pdf07 = generateScorePdf(archiveNo, task, student, score);

        // 主登记档案 PDF 写入临时文件，稍后原子移动
        Path tempPdf = tempDir.resolve("arc_pdf_tmp_" + UUID.randomUUID() + ".pdf");
        Files.write(tempPdf, pdf07);

        // 3. 准备收集 ZIP 条目并计算 SHA-256 完整性摘要
        Map<String, byte[]> zipEntries = new LinkedHashMap<>();
        zipEntries.put("01_学生实习岗位申报材料.pdf", pdf01);
        zipEntries.put("02_安全教育考核与安全承诺书.pdf", pdf02);
        zipEntries.put("03_实习周报汇编合集.pdf", pdf03);
        zipEntries.put("04_过程指导走访台账.pdf", pdf04);
        zipEntries.put("05_中期检查督导与限期整改单.pdf", pdf05);
        zipEntries.put("06_实习总结报告与阶段材料.pdf", pdf06);
        zipEntries.put("07_五维考核评价与成绩综合评定单.pdf", pdf07);

        // 添加 08_归档前置核验诊断单.json
        byte[] diagBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(precheck.getCheckItems());
        zipEntries.put("08_归档前置核验诊断单.json", diagBytes);

        // 4. 安全下载外部凭据附件 (覆盖阶段材料、整改、成绩凭据)
        List<Map<String, Object>> voucherManifestList = new ArrayList<>();
        int voucherIdx = 1;

        List<String> externalUrls = new ArrayList<>();
        if (materials != null) {
            for (StudentMaterialItem m : materials) {
                if (StringUtils.hasText(m.getAttachmentUrl())) externalUrls.add(m.getAttachmentUrl());
            }
        }
        if (rectList != null) {
            for (MidtermRectification r : rectList) {
                if (StringUtils.hasText(r.getEvidenceAttachmentUrl())) externalUrls.add(r.getEvidenceAttachmentUrl());
            }
        }
        if (score != null && StringUtils.hasText(score.getEnterpriseEvaluationUrl())) {
            externalUrls.add(score.getEnterpriseEvaluationUrl());
        }

        for (String urlStr : externalUrls) {
            SafeUrlValidator.DownloadResult dl = SafeUrlValidator.downloadSafeResource(urlStr);
            Map<String, Object> vInfo = new LinkedHashMap<>();
            vInfo.put("url", urlStr);
            if (dl.isSuccess()) {
                String entryName = "attachments/voucher_" + (voucherIdx++) + dl.getFileExtension();
                zipEntries.put(entryName, dl.getData());
                vInfo.put("status", "SAVED");
                vInfo.put("path", entryName);
                vInfo.put("sha256", dl.getSha256Hex());
                vInfo.put("sizeBytes", dl.getData().length);
            } else {
                // 外部资源获取失败时不能中断整个归档，在 manifest 中标记失败并保留可追溯 URL
                vInfo.put("status", "FETCH_FAILED");
                vInfo.put("error", dl.getErrorMessage());
            }
            voucherManifestList.add(vInfo);
        }

        // 5. 生成 manifest.json (记录每个文件的 SHA-256 完整性摘要，不使用数字签名或 CA 签章表述)
        List<Map<String, Object>> filesManifestList = new ArrayList<>();
        long totalBytes = 0;
        for (Map.Entry<String, byte[]> entry : zipEntries.entrySet()) {
            Map<String, Object> fMeta = new LinkedHashMap<>();
            fMeta.put("fileName", entry.getKey());
            fMeta.put("sha256", SafeUrlValidator.calculateSha256(entry.getValue()));
            fMeta.put("sizeBytes", entry.getValue().length);
            fMeta.put("description", entry.getKey().endsWith(".pdf") ? "标准化电子归档单项卷宗" : "归档佐证材料");
            filesManifestList.add(fMeta);
            totalBytes += entry.getValue().length;
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("archiveNo", archiveNo);
        manifest.put("academicYear", academicYear);
        manifest.put("taskId", taskId);
        manifest.put("taskName", task != null ? task.getTaskName() : "");
        manifest.put("studentId", studentId);
        manifest.put("studentNo", student != null ? student.getUsername() : "");
        manifest.put("studentName", student != null ? student.getRealName() : "");
        manifest.put("archivedTime", LocalDateTime.now().format(TIME_FMT));
        manifest.put("integrityAlgorithm", "SHA-256");
        manifest.put("integrityDigestStandard", "高校实习电子档案完整性校验规范 (SHA-256 数据完整性摘要)");
        manifest.put("totalFilesCount", zipEntries.size());
        manifest.put("totalSizeBytes", totalBytes);
        manifest.put("files", filesManifestList);
        manifest.put("externalVouchers", voucherManifestList);

        byte[] manifestBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);

        // 6. 写入临时 ZIP 文件
        Path tempZip = tempDir.resolve("arc_zip_tmp_" + UUID.randomUUID() + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip.toFile()))) {
            // 首先写入 manifest.json
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifestBytes);
            zos.closeEntry();

            // 写入其余所有文件
            for (Map.Entry<String, byte[]> entry : zipEntries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }

        // 7. 容量上限校验 (不超过 100MB)
        long zipSize = Files.size(tempZip);
        long maxBundleBytes = (long) phase7Properties.getArchive().getExportMaxBundleSizeMb() * 1024L * 1024L;
        if (zipSize > maxBundleBytes) {
            Files.deleteIfExists(tempZip);
            Files.deleteIfExists(tempPdf);
            throw new BusinessException(400, "电子卷宗打包后体积 (" + (zipSize / 1024 / 1024) + "MB) 超出系统允许上限 ("
                    + phase7Properties.getArchive().getExportMaxBundleSizeMb() + "MB)");
        }

        // 8. ATOMIC_MOVE 原子替换目标 PDF 与 ZIP 文件
        moveAtomic(tempPdf, finalPdfPath);
        moveAtomic(tempZip, finalZipPath);
    }

    private void moveAtomic(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ============================================================================================
    // 7 份标准 PDF 生成辅助方法
    // ============================================================================================

    private byte[] generateApplyPdf(String archiveNo, InternshipTask task, SysUser student, InternshipApply apply) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        addTitle(doc, "01_学生实习岗位申报与审核材料表");
        addHeaderInfo(doc, archiveNo);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{20, 30, 20, 30});

        addCell(table, "学生姓名", true);
        addCell(table, student != null ? student.getRealName() : "", false);
        addCell(table, "学生学号", true);
        addCell(table, student != null ? student.getUsername() : "", false);

        addCell(table, "所属任务", true);
        addCell(table, task != null ? task.getTaskName() : "", false);
        addCell(table, "所属学年", true);
        addCell(table, task != null ? task.getAcademicYear() : "", false);

        addCell(table, "实习单位", true);
        addCell(table, apply != null ? apply.getCompanyName() : "未录入", false);
        addCell(table, "实习岗位", true);
        addCell(table, apply != null ? apply.getJobPosition() : "未录入", false);

        addCell(table, "实习地点", true);
        addCell(table, apply != null ? apply.getJobAddress() : "未录入", false);
        addCell(table, "实习形式", true);
        addCell(table, apply != null ? apply.getInternshipMode() : "自主实习", false);

        addCell(table, "开始日期", true);
        addCell(table, apply != null && apply.getStartDate() != null ? apply.getStartDate().toString() : "--", false);
        addCell(table, "结束日期", true);
        addCell(table, apply != null && apply.getEndDate() != null ? apply.getEndDate().toString() : "--", false);

        addCell(table, "企业联系人", true);
        addCell(table, apply != null ? apply.getCompanyContactPerson() : "--", false);
        addCell(table, "联系电话", true);
        addCell(table, apply != null ? apply.getCompanyContactPhone() : "--", false);

        addCell(table, "申报审批状态", true);
        addCell(table, apply != null ? apply.getApplyStatus() : "PENDING", false);
        addCell(table, "数据锁定状态", true);
        addCell(table, apply != null && apply.getIsLocked() != null && apply.getIsLocked() == 1 ? "已锁定" : "正常", false);

        doc.add(table);

        if (apply != null && StringUtils.hasText(apply.getJobDuties())) {
            Paragraph p = new Paragraph("\n岗位工作职责说明：\n" + apply.getJobDuties(), PdfFontUtil.getBodyFont());
            doc.add(p);
        }

        addFooter(doc);
        doc.close();
        return baos.toByteArray();
    }

    private byte[] generateSafetyPdf(String archiveNo, InternshipTask task, SysUser student, SafetyCommitmentSign sign) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        addTitle(doc, "02_实习安全教育考核与安全责任承诺书");
        addHeaderInfo(doc, archiveNo);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{20, 30, 20, 30});

        addCell(table, "学生姓名", true);
        addCell(table, student != null ? student.getRealName() : "", false);
        addCell(table, "学生学号", true);
        addCell(table, student != null ? student.getUsername() : "", false);

        addCell(table, "实习任务", true);
        addCell(table, task != null ? task.getTaskName() : "", false);
        addCell(table, "签署状态", true);
        addCell(table, sign != null && sign.getIsSigned() == 1 ? "已完成在线确认签署" : "未签署", false);

        addCell(table, "签署网络 IP", true);
        addCell(table, sign != null ? sign.getSignIp() : "--", false);
        addCell(table, "签署时间", true);
        addCell(table, sign != null && sign.getSignTime() != null ? sign.getSignTime().format(TIME_FMT) : "--", false);

        doc.add(table);

        Paragraph pTitle = new Paragraph("\n安全责任承诺书正文内容：", PdfFontUtil.getHeaderFont());
        doc.add(pTitle);

        String text = (sign != null && StringUtils.hasText(sign.getCommitmentText()))
                ? sign.getCommitmentText()
                : "本人已完全知晓并深刻理解学校关于实习安全全过程管理的各项规章规程，在实习期间严格遵守国家法律法规及企业安全操作规程，" +
                "主动防范各类人身、财产、网络、交通与生产安全隐患，服从指导教师与企业主管安排，每日保持联络畅通，履行安全第一责任人义务。";

        Paragraph pContent = new Paragraph(text, PdfFontUtil.getBodyFont());
        pContent.setSpacingBefore(8);
        pContent.setLeading(18);
        doc.add(pContent);

        addFooter(doc);
        doc.close();
        return baos.toByteArray();
    }

    private byte[] generateWeeklyPdf(String archiveNo, InternshipTask task, SysUser student, List<InternshipWeeklyReport> weeklyList) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        addTitle(doc, "03_学生实习周报汇编合集");
        addHeaderInfo(doc, archiveNo);

        Paragraph summary = new Paragraph("已收录经审阅批阅的周报总数: " + (weeklyList != null ? weeklyList.size() : 0) + " 篇\n", PdfFontUtil.getHeaderFont());
        doc.add(summary);

        if (weeklyList != null && !weeklyList.isEmpty()) {
            for (InternshipWeeklyReport w : weeklyList) {
                Paragraph pWeek = new Paragraph("\n第 " + w.getWeekNumber() + " 周实习周报 ("
                        + (w.getStartDate() != null ? w.getStartDate() : "") + " 至 "
                        + (w.getEndDate() != null ? w.getEndDate() : "") + ")  状态: " + w.getStatus(), PdfFontUtil.getHeaderFont());
                doc.add(pWeek);

                PdfPTable table = new PdfPTable(2);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{25, 75});

                addCell(table, "工作内容", true);
                addCell(table, w.getWorkContent(), false);

                addCell(table, "工作总结", true);
                addCell(table, w.getWorkSummary(), false);

                addCell(table, "存在问题与思考", true);
                addCell(table, w.getProblemEncountered(), false);

                addCell(table, "下周工作规划", true);
                addCell(table, w.getNextWeekPlan(), false);

                addCell(table, "导师批阅评分", true);
                addCell(table, (w.getScore() != null ? w.getScore() + " 分" : "未评分") + " | 评语: " + (w.getReviewComment() != null ? w.getReviewComment() : "无"), false);

                doc.add(table);
            }
        } else {
            doc.add(new Paragraph("暂无周报填报记录", PdfFontUtil.getBodyFont()));
        }

        addFooter(doc);
        doc.close();
        return baos.toByteArray();
    }

    private byte[] generateGuidancePdf(String archiveNo, InternshipTask task, SysUser student, List<InternshipGuidanceRecord> guidanceList) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        addTitle(doc, "04_指导教师过程走访与指导台账");
        addHeaderInfo(doc, archiveNo);

        Paragraph info = new Paragraph("累计指导记录: " + (guidanceList != null ? guidanceList.size() : 0) + " 次\n", PdfFontUtil.getHeaderFont());
        doc.add(info);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{10, 18, 18, 38, 16});

        addCell(table, "序号", true);
        addCell(table, "指导教师", true);
        addCell(table, "指导形式 / 日期", true);
        addCell(table, "指导内容纪要", true);
        addCell(table, "反馈确认状态", true);

        if (guidanceList != null && !guidanceList.isEmpty()) {
            int idx = 1;
            for (InternshipGuidanceRecord g : guidanceList) {
                addCell(table, String.valueOf(idx++), false);
                addCell(table, g.getTeacherName() != null ? g.getTeacherName() : "指导教师", false);
                addCell(table, g.getGuidanceType() + "\n" + (g.getGuidanceDate() != null ? g.getGuidanceDate().format(TIME_FMT) : ""), false);
                addCell(table, g.getContentSummary(), false);
                addCell(table, g.getFeedbackStatus() != null ? g.getFeedbackStatus() : "CONFIRMED", false);
            }
        } else {
            for (int i = 0; i < 5; i++) addCell(table, "--", false);
        }

        doc.add(table);
        addFooter(doc);
        doc.close();
        return baos.toByteArray();
    }

    private byte[] generateInspectRectifyPdf(String archiveNo, InternshipTask task, SysUser student,
                                             MidtermInspection ins, List<MidtermRectification> rectList) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        addTitle(doc, "05_实习中期督导检查与限期整改单");
        addHeaderInfo(doc, archiveNo);

        Paragraph s1 = new Paragraph("一、中期督导抽查记录", PdfFontUtil.getHeaderFont());
        doc.add(s1);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{20, 30, 20, 30});

        addCell(table, "抽查批次号", true);
        addCell(table, ins != null ? ins.getSamplingBatchNo() : "--", false);
        addCell(table, "检查类型", true);
        addCell(table, ins != null ? ins.getInspectionType() : "ONSITE", false);

        addCell(table, "督导评分", true);
        addCell(table, ins != null && ins.getScore() != null ? ins.getScore() + " 分" : "--", false);
        addCell(table, "问题标注", true);
        addCell(table, ins != null && ins.getHasProblem() != null && ins.getHasProblem() == 1 ? "存在突出问题" : "合格正常", false);

        addCell(table, "企业走访评价", true);
        addCell(table, ins != null ? ins.getCompanySituation() : "--", false);
        addCell(table, "学生表现评价", true);
        addCell(table, ins != null ? ins.getStudentPerformance() : "--", false);

        doc.add(table);

        if (ins != null && StringUtils.hasText(ins.getProblemDesc())) {
            doc.add(new Paragraph("\n督导发现问题描述：\n" + ins.getProblemDesc(), PdfFontUtil.getBodyFont()));
        }

        Paragraph s2 = new Paragraph("\n二、限期整改与闭环销号落实记录", PdfFontUtil.getHeaderFont());
        doc.add(s2);

        if (rectList != null && !rectList.isEmpty()) {
            PdfPTable rTable = new PdfPTable(4);
            rTable.setWidthPercentage(100);
            rTable.setWidths(new float[]{25, 30, 25, 20});

            addCell(rTable, "整改要求", true);
            addCell(rTable, "学生整改措施", true);
            addCell(rTable, "教师复核评语", true);
            addCell(rTable, "销号状态", true);

            for (MidtermRectification r : rectList) {
                addCell(rTable, r.getRectifyRequirements(), false);
                addCell(rTable, r.getStudentExplanation(), false);
                addCell(rTable, r.getReviewComment() != null ? r.getReviewComment() : "--", false);
                addCell(rTable, r.getStatus(), false);
            }
            doc.add(rTable);
        } else {
            doc.add(new Paragraph("该生中期检查合格，无不良违纪与限期整改事项。", PdfFontUtil.getBodyFont()));
        }

        addFooter(doc);
        doc.close();
        return baos.toByteArray();
    }

    private byte[] generateMaterialSummaryPdf(String archiveNo, InternshipTask task, SysUser student, List<StudentMaterialItem> materials) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        addTitle(doc, "06_实习阶段性材料与毕业实习总结报告");
        addHeaderInfo(doc, archiveNo);

        Paragraph s1 = new Paragraph("一、阶段性材料提报与导师查验清单", PdfFontUtil.getHeaderFont());
        doc.add(s1);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{25, 25, 15, 15, 20});

        addCell(table, "材料编码", true);
        addCell(table, "材料名称", true);
        addCell(table, "版本号", true);
        addCell(table, "查验打分", true);
        addCell(table, "查验状态", true);

        StudentMaterialItem summaryReportItem = null;

        if (materials != null && !materials.isEmpty()) {
            for (StudentMaterialItem m : materials) {
                addCell(table, m.getMaterialCode(), false);
                addCell(table, m.getMaterialName(), false);
                addCell(table, "v" + (m.getVersion() != null ? m.getVersion() : 1), false);
                addCell(table, m.getAuditScore() != null ? m.getAuditScore() + " 分" : "--", false);
                addCell(table, m.getStatus(), false);

                if ("SUMMARY_REPORT".equalsIgnoreCase(m.getMaterialCode())) {
                    summaryReportItem = m;
                }
            }
        } else {
            for (int i = 0; i < 5; i++) addCell(table, "--", false);
        }
        doc.add(table);

        Paragraph s2 = new Paragraph("\n二、毕业实习总结报告正文全文", PdfFontUtil.getHeaderFont());
        doc.add(s2);

        if (summaryReportItem != null && StringUtils.hasText(summaryReportItem.getContentText())) {
            String text = summaryReportItem.getContentText();
            Paragraph stat = new Paragraph("报告实际统计字数: " + text.length() + " 字 | 导师评分: "
                    + (summaryReportItem.getAuditScore() != null ? summaryReportItem.getAuditScore() : "--") + " 分", PdfFontUtil.getSmallFont());
            doc.add(stat);

            Paragraph content = new Paragraph("\n" + text, PdfFontUtil.getBodyFont());
            content.setLeading(18);
            doc.add(content);
        } else {
            doc.add(new Paragraph("暂无毕业实习总结报告正文提报记录", PdfFontUtil.getBodyFont()));
        }

        addFooter(doc);
        doc.close();
        return baos.toByteArray();
    }

    private byte[] generateScorePdf(String archiveNo, InternshipTask task, SysUser student, ScoreSummary score) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        addTitle(doc, "07_毕业实习五维考核评价与综合成绩评定单");
        addHeaderInfo(doc, archiveNo);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{20, 30, 20, 30});

        addCell(table, "学生姓名", true);
        addCell(table, student != null ? student.getRealName() : "", false);
        addCell(table, "学生学号", true);
        addCell(table, student != null ? student.getUsername() : "", false);

        addCell(table, "实习任务", true);
        addCell(table, task != null ? task.getTaskName() : "", false);
        addCell(table, "所属学年", true);
        addCell(table, task != null ? task.getAcademicYear() : "", false);

        addCell(table, "综合评定总分", true);
        addCell(table, score != null && score.getFinalScore() != null ? score.getFinalScore() + " 分" : "--", false);
        addCell(table, "最终等第评定", true);
        addCell(table, score != null && score.getScoreLevel() != null ? score.getScoreLevel() : "--", false);

        addCell(table, "成绩发布状态", true);
        addCell(table, score != null ? score.getStatus() : "PUBLISHED", false);
        addCell(table, "公示起始时间", true);
        addCell(table, score != null && score.getPublicityStartTime() != null ? score.getPublicityStartTime().format(TIME_FMT) : "--", false);

        doc.add(table);

        Paragraph sTitle = new Paragraph("\n五维单项折算明细：", PdfFontUtil.getHeaderFont());
        doc.add(sTitle);

        PdfPTable sTable = new PdfPTable(5);
        sTable.setWidthPercentage(100);
        sTable.setWidths(new float[]{20, 20, 20, 20, 20});

        addCell(sTable, "企业考核", true);
        addCell(sTable, "过程指导", true);
        addCell(sTable, "周报均分", true);
        addCell(sTable, "阶段材料", true);
        addCell(sTable, "总结报告", true);

        if (score != null) {
            addCell(sTable, score.getEnterpriseScore() != null ? score.getEnterpriseScore() + " 分" : "--", false);
            addCell(sTable, score.getProcessScore() != null ? score.getProcessScore() + " 分" : "--", false);
            addCell(sTable, score.getWeeklyScore() != null ? score.getWeeklyScore() + " 分" : "--", false);
            addCell(sTable, score.getMaterialScore() != null ? score.getMaterialScore() + " 分" : "--", false);
            addCell(sTable, score.getSummaryScore() != null ? score.getSummaryScore() + " 分" : "--", false);
        } else {
            for (int i = 0; i < 5; i++) addCell(sTable, "--", false);
        }
        doc.add(sTable);

        if (score != null && StringUtils.hasText(score.getEvaluationComment())) {
            doc.add(new Paragraph("\n指导教师综合评语与结课评价：\n" + score.getEvaluationComment(), PdfFontUtil.getBodyFont()));
        }

        addFooter(doc);
        doc.close();
        return baos.toByteArray();
    }

    private void addTitle(Document doc, String titleText) throws Exception {
        Paragraph title = new Paragraph(titleText, PdfFontUtil.getTitleFont());
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(12);
        doc.add(title);
    }

    private void addHeaderInfo(Document doc, String archiveNo) throws Exception {
        Paragraph p = new Paragraph("电子档案卷宗编号: " + archiveNo + "    生成时间: " + LocalDateTime.now().format(TIME_FMT), PdfFontUtil.getSmallFont());
        p.setAlignment(Element.ALIGN_RIGHT);
        p.setSpacingAfter(10);
        doc.add(p);
    }

    private void addFooter(Document doc) throws Exception {
        Paragraph footer = new Paragraph("\n本档案经高校实习全过程管理系统权威认证与电子固化，不可篡改。", PdfFontUtil.getSmallFont());
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }

    private void addCell(PdfPTable table, String text, boolean isHeader) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", isHeader ? PdfFontUtil.getHeaderFont() : PdfFontUtil.getBodyFont()));
        cell.setHorizontalAlignment(isHeader ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private ArchiveVO convertToArchiveVO(InternshipArchive a) {
        InternshipTask task = taskMapper.selectById(a.getTaskId());
        SysUser student = userMapper.selectById(a.getStudentId());
        BaseDepartment dept = departmentMapper.selectById(a.getDeptId());
        SysUser archivedUser = userMapper.selectById(a.getArchivedUserId());
        SysUser unlockedUser = a.getUnlockedBy() != null ? userMapper.selectById(a.getUnlockedBy()) : null;

        return ArchiveVO.builder()
                .id(a.getId())
                .archiveNo(a.getArchiveNo())
                .taskId(a.getTaskId())
                .taskName(task != null ? task.getTaskName() : "")
                .studentId(a.getStudentId())
                .studentName(student != null ? student.getRealName() : "")
                .studentNo(student != null ? student.getUsername() : "")
                .deptId(a.getDeptId())
                .deptName(dept != null ? dept.getDeptName() : "")
                .academicYear(a.getAcademicYear())
                .checkMatrixJson(a.getCheckMatrixJson())
                .archiveBundleUrl(a.getArchiveBundleUrl())
                .archivePdfUrl(a.getArchivePdfUrl())
                .archivedUserId(a.getArchivedUserId())
                .archivedUserName(archivedUser != null ? archivedUser.getRealName() : "")
                .archivedTime(a.getArchivedTime())
                .status(a.getStatus())
                .specialUnlockReason(a.getSpecialUnlockReason())
                .specialDocNo(a.getSpecialDocNo())
                .unlockedBy(a.getUnlockedBy())
                .unlockedByName(unlockedUser != null ? unlockedUser.getRealName() : "")
                .unlockedTime(a.getUnlockedTime())
                .unlockExpireTime(a.getUnlockExpireTime())
                .version(a.getVersion())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
