package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.Phase7Properties;
import com.college.internship.dto.MaterialAuditDTO;
import com.college.internship.dto.MaterialSubmitDTO;
import com.college.internship.entity.BasePhase7Entity;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.entity.MaterialVersionHistory;
import com.college.internship.entity.StudentMaterialItem;
import com.college.internship.entity.SysUser;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.InternshipTaskStudentMapper;
import com.college.internship.mapper.MaterialVersionHistoryMapper;
import com.college.internship.mapper.StudentMaterialItemMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IArchiveService;
import com.college.internship.service.IMaterialService;
import com.college.internship.vo.MaterialItemVO;
import com.college.internship.vo.MaterialUploadVO;
import com.college.internship.vo.MaterialVersionVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.college.internship.util.SafeUrlValidator;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 阶段材料与实习总结报告核心业务服务实现类 (API-060 ~ API-064)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements IMaterialService {

    private final StudentMaterialItemMapper materialItemMapper;
    private final MaterialVersionHistoryMapper versionHistoryMapper;
    private final InternshipTaskMapper taskMapper;
    private final InternshipTaskStudentMapper taskStudentMapper;
    private final SysUserMapper userMapper;
    private final Phase7Properties phase7Properties;
    private final ObjectMapper objectMapper;
    @Lazy
    private final IArchiveService archiveService;

    @Override
    public List<MaterialItemVO> getMaterialList(Long taskId, Long studentId, LoginUser loginUser) {
        if (taskId == null) {
            throw new BusinessException(400, "实习任务ID不能为空");
        }
        InternshipTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(400, "实习任务不存在");
        }

        List<Long> targetStudentIds = new ArrayList<>();
        if ("STUDENT".equals(loginUser.getUserType())) {
            targetStudentIds.add(loginUser.getUserId());
        } else if (studentId != null) {
            if ("TEACHER".equals(loginUser.getUserType())) {
                // 教师权限隔离校验：只能查带教学生
                InternshipTaskStudent binding = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                        .eq(InternshipTaskStudent::getTaskId, taskId)
                        .eq(InternshipTaskStudent::getStudentId, studentId)
                        .eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId()));
                if (binding == null) {
                    throw new BusinessException(403, "无权查看非负责学生的阶段材料");
                }
            } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
                if (task.getDeptId() != null && !task.getDeptId().equals(loginUser.getDeptId())) {
                    throw new BusinessException(403, "院系负责人无权跨院系查看阶段材料清单");
                }
            }
            targetStudentIds.add(studentId);
        } else if ("TEACHER".equals(loginUser.getUserType())) {
            // 教师默认查所有负责带教的学生
            List<InternshipTaskStudent> bindings = taskStudentMapper.selectList(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, taskId)
                    .eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId()));
            for (InternshipTaskStudent b : bindings) {
                targetStudentIds.add(b.getStudentId());
            }
        } else {
            // 院系或校级管理端默认查任务下所有学生
            if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
                if (task.getDeptId() != null && !task.getDeptId().equals(loginUser.getDeptId())) {
                    throw new BusinessException(403, "院系负责人无权跨院系查看阶段材料清单");
                }
            }
            List<InternshipTaskStudent> bindings = taskStudentMapper.selectList(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, taskId));
            for (InternshipTaskStudent b : bindings) {
                targetStudentIds.add(b.getStudentId());
            }
        }

        // 解析材料清单
        List<ChecklistItem> checklist = parseChecklist(task.getMaterialChecklist());
        List<MaterialItemVO> resultList = new ArrayList<>();

        for (Long sId : targetStudentIds) {
            SysUser studentUser = userMapper.selectById(sId);
            String studentName = studentUser != null ? studentUser.getRealName() : "";
            String studentNo = studentUser != null ? studentUser.getUsername() : "";

            // 查询该学生已提交的材料实体
            List<StudentMaterialItem> existingItems = materialItemMapper.selectList(new LambdaQueryWrapper<StudentMaterialItem>()
                    .eq(StudentMaterialItem::getTaskId, taskId)
                    .eq(StudentMaterialItem::getStudentId, sId)
                    .eq(BasePhase7Entity::getIsDeleted, 0));
            Map<String, StudentMaterialItem> itemMap = existingItems.stream()
                    .collect(Collectors.toMap(StudentMaterialItem::getMaterialCode, item -> item, (k1, k2) -> k1));

            for (ChecklistItem item : checklist) {
                StudentMaterialItem entity = itemMap.get(item.getMaterialCode());
                int minLen = getMinLengthForCode(item.getMaterialCode());

                MaterialItemVO vo = MaterialItemVO.builder()
                        .taskId(taskId)
                        .studentId(sId)
                        .studentName(studentName)
                        .studentNo(studentNo)
                        .materialCode(item.getMaterialCode())
                        .materialName(item.getMaterialName())
                        .materialType(item.getMaterialType())
                        .required(item.getRequired())
                        .minContentLength(minLen)
                        .status("UNSUBMITTED")
                        .version(1)
                        .build();

                if (entity != null) {
                    vo.setId(entity.getId());
                    vo.setContentText(entity.getContentText());
                    vo.setAttachmentUrl(entity.getAttachmentUrl());
                    vo.setFileName(entity.getFileName());
                    vo.setFileSize(entity.getFileSize());
                    vo.setFileHash(entity.getFileHash());
                    vo.setVersion(entity.getVersion());
                    vo.setStatus(entity.getStatus());
                    vo.setSubmitTime(entity.getSubmitTime());
                    vo.setAuditTeacherId(entity.getAuditTeacherId());
                    vo.setAuditScore(entity.getAuditScore());
                    vo.setAuditComment(entity.getAuditComment());
                    vo.setAuditTime(entity.getAuditTime());
                    if (entity.getAuditTeacherId() != null) {
                        SysUser teacher = userMapper.selectById(entity.getAuditTeacherId());
                        if (teacher != null) {
                            vo.setAuditTeacherName(teacher.getRealName());
                        }
                    }
                }
                resultList.add(vo);
            }
        }
        return resultList;
    }

    @Override
    public MaterialItemVO getMaterialDetail(Long id, LoginUser loginUser) {
        StudentMaterialItem entity = materialItemMapper.selectById(id);
        if (entity == null || (entity.getIsDeleted() != null && entity.getIsDeleted() == 1)) {
            throw new BusinessException(400, "阶段材料记录不存在");
        }
        // 权限校验
        if ("STUDENT".equals(loginUser.getUserType()) && !entity.getStudentId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权查看其他学生的材料详情");
        }
        if ("TEACHER".equals(loginUser.getUserType())) {
            InternshipTaskStudent binding = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, entity.getTaskId())
                    .eq(InternshipTaskStudent::getStudentId, entity.getStudentId())
                    .eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId()));
            if (binding == null) {
                throw new BusinessException(403, "无权查看非负责学生的阶段材料");
            }
        }
        if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            InternshipTask task = taskMapper.selectById(entity.getTaskId());
            if (task != null && task.getDeptId() != null && !task.getDeptId().equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "院系负责人无权跨院系查看阶段材料详情");
            }
        }

        SysUser student = userMapper.selectById(entity.getStudentId());
        String teacherName = null;
        if (entity.getAuditTeacherId() != null) {
            SysUser teacher = userMapper.selectById(entity.getAuditTeacherId());
            if (teacher != null) {
                teacherName = teacher.getRealName();
            }
        }

        return MaterialItemVO.builder()
                .id(entity.getId())
                .taskId(entity.getTaskId())
                .studentId(entity.getStudentId())
                .studentName(student != null ? student.getRealName() : "")
                .studentNo(student != null ? student.getUsername() : "")
                .materialCode(entity.getMaterialCode())
                .materialName(entity.getMaterialName())
                .materialType(entity.getMaterialType())
                .minContentLength(getMinLengthForCode(entity.getMaterialCode()))
                .contentText(entity.getContentText())
                .attachmentUrl(entity.getAttachmentUrl())
                .fileName(entity.getFileName())
                .fileSize(entity.getFileSize())
                .fileHash(entity.getFileHash())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .submitTime(entity.getSubmitTime())
                .auditTeacherId(entity.getAuditTeacherId())
                .auditTeacherName(teacherName)
                .auditScore(entity.getAuditScore())
                .auditComment(entity.getAuditComment())
                .auditTime(entity.getAuditTime())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitMaterial(MaterialSubmitDTO dto, LoginUser loginUser) {
        if (!"STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅学生允许提交阶段材料");
        }
        Long studentId = loginUser.getUserId();
        Long taskId = dto.getTaskId();
        if (taskId == null) {
            throw new BusinessException(400, "实习任务ID不能为空");
        }

        // 0. 外部附件 URL 安全与 SSRF 防护校验 (仅校验外部 HTTP/HTTPS 链接，内部存储路径豁免)
        if (StringUtils.hasText(dto.getAttachmentUrl()) && !dto.getAttachmentUrl().startsWith("/")) {
            SafeUrlValidator.validateUrl(dto.getAttachmentUrl());
        }

        // 1. 全局归档写保护拦截 (TEST-P7-21)
        archiveService.checkWriteProtection(taskId, studentId);

        // 2. 动态字数约束校验 (TEST-P7-02)
        if ("SUMMARY_REPORT".equals(dto.getMaterialCode())) {
            int minLen = phase7Properties.getScore().getMinSummaryLength();
            int currentLen = dto.getContentText() != null ? dto.getContentText().trim().length() : 0;
            if (currentLen < minLen) {
                throw new BusinessException(400, "毕业实习总结报告正文字数不足，当前: " + currentLen + " 字，最低要求: " + minLen + " 字");
            }
        } else if ("MIDTERM_SUMMARY".equals(dto.getMaterialCode())) {
            int minLen = phase7Properties.getMaterial().getMinMidtermSummaryLength();
            int currentLen = dto.getContentText() != null ? dto.getContentText().trim().length() : 0;
            if (currentLen < minLen) {
                throw new BusinessException(400, "实习中期进展总结正文字数不足，当前: " + currentLen + " 字，最低要求: " + minLen + " 字");
            }
        }

        // 3. 校验材料编码合法性
        InternshipTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(400, "实习任务不存在");
        }
        List<ChecklistItem> checklist = parseChecklist(task.getMaterialChecklist());
        ChecklistItem matchedItem = checklist.stream()
                .filter(item -> item.getMaterialCode() != null && item.getMaterialCode().equalsIgnoreCase(dto.getMaterialCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "未知的阶段材料编码: " + dto.getMaterialCode()));

        // 4. URL 安全与大小校验
        if (StringUtils.hasText(dto.getAttachmentUrl())) {
            validateUrlSecurity(dto.getAttachmentUrl());
        }

        // 5. 查重与版本控制
        StudentMaterialItem existing = materialItemMapper.selectOne(new LambdaQueryWrapper<StudentMaterialItem>()
                .eq(StudentMaterialItem::getTaskId, taskId)
                .eq(StudentMaterialItem::getStudentId, studentId)
                .eq(StudentMaterialItem::getMaterialCode, dto.getMaterialCode())
                .eq(BasePhase7Entity::getIsDeleted, 0));

        LocalDateTime now = LocalDateTime.now();
        int newVersion = 1;
        if (existing != null) {
            if ("APPROVED".equalsIgnoreCase(existing.getStatus())) {
                throw new BusinessException(400, "已查验通过的材料禁止重复提交修改");
            }
            int maxVersions = phase7Properties.getMaterial().getMaxSubmitVersions();
            if (existing.getVersion() >= maxVersions) {
                throw new BusinessException(400, "材料提报重提版本已达上限 (" + maxVersions + " 次)");
            }
            newVersion = existing.getVersion() + 1;
            existing.setVersion(newVersion);
            existing.setContentText(dto.getContentText());
            existing.setAttachmentUrl(dto.getAttachmentUrl());
            existing.setFileName(dto.getFileName());
            existing.setFileSize(dto.getFileSize());
            existing.setFileHash(dto.getFileHash());
            existing.setStatus("SUBMITTED");
            existing.setSubmitTime(now);
            existing.setAuditTeacherId(null);
            existing.setAuditScore(null);
            existing.setAuditComment(null);
            existing.setAuditTime(null);
            materialItemMapper.updateById(existing);
        } else {
            existing = StudentMaterialItem.builder()
                    .taskId(taskId)
                    .studentId(studentId)
                    .materialCode(dto.getMaterialCode())
                    .materialName(matchedItem.getMaterialName())
                    .materialType(matchedItem.getMaterialType())
                    .contentText(dto.getContentText())
                    .attachmentUrl(dto.getAttachmentUrl())
                    .fileName(dto.getFileName())
                    .fileSize(dto.getFileSize())
                    .fileHash(dto.getFileHash())
                    .version(1)
                    .status("SUBMITTED")
                    .submitTime(now)
                    .build();
            materialItemMapper.insert(existing);
        }

        // 6. 记录版本快照 (TEST-P7-01)
        MaterialVersionHistory history = MaterialVersionHistory.builder()
                .materialId(existing.getId())
                .taskId(taskId)
                .studentId(studentId)
                .version(newVersion)
                .contentText(dto.getContentText())
                .attachmentUrl(dto.getAttachmentUrl())
                .fileName(dto.getFileName())
                .fileSize(dto.getFileSize())
                .fileHash(dto.getFileHash())
                .submitTime(now)
                .status("SUBMITTED")
                .build();
        versionHistoryMapper.insert(history);

        return existing.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditMaterial(Long id, MaterialAuditDTO dto, LoginUser loginUser) {
        if (!"TEACHER".equals(loginUser.getUserType()) && !"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅指导教师或管理端允许查验阶段材料");
        }

        StudentMaterialItem item = materialItemMapper.selectById(id);
        if (item == null || (item.getIsDeleted() != null && item.getIsDeleted() == 1)) {
            throw new BusinessException(400, "阶段材料记录不存在");
        }

        if ("UNSUBMITTED".equalsIgnoreCase(item.getStatus())) {
            throw new BusinessException(400, "未提交的材料无法进行查验");
        }

        // 教师权限隔离校验 (TEST-P7-01, TEST-P7-03 & 缺口 4)
        if ("TEACHER".equals(loginUser.getUserType())) {
            InternshipTaskStudent binding = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, item.getTaskId())
                    .eq(InternshipTaskStudent::getStudentId, item.getStudentId())
                    .eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId()));
            if (binding == null) {
                throw new BusinessException(403, "无权查验非负责学生的阶段材料");
            }
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            InternshipTask task = taskMapper.selectById(item.getTaskId());
            if (task != null && task.getDeptId() != null && !task.getDeptId().equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "院系负责人无权跨院系查验阶段材料");
            }
        }

        boolean isApproved = "APPROVED".equalsIgnoreCase(dto.getAction());
        if (!isApproved && "RETURNED".equalsIgnoreCase(dto.getAction())) {
            if (!StringUtils.hasText(dto.getAuditComment()) || dto.getAuditComment().trim().isEmpty()) {
                throw new BusinessException(400, "退回必须填写查验意见");
            }
        } else if (!isApproved) {
            throw new BusinessException(400, "查验动作非法 (仅支持 APPROVED 或 RETURNED)");
        } else if (dto.getAuditScore() == null) {
            throw new BusinessException(400, "查验通过必须录入考评分数");
        }

        LocalDateTime now = LocalDateTime.now();
        item.setStatus(isApproved ? "APPROVED" : "RETURNED");
        item.setAuditScore(isApproved ? dto.getAuditScore() : null);
        item.setAuditComment(dto.getAuditComment());
        item.setAuditTeacherId(loginUser.getUserId());
        item.setAuditTime(now);
        materialItemMapper.updateById(item);

        // 同步更新最新版本快照
        MaterialVersionHistory latestHistory = versionHistoryMapper.selectOne(new LambdaQueryWrapper<MaterialVersionHistory>()
                .eq(MaterialVersionHistory::getMaterialId, item.getId())
                .eq(MaterialVersionHistory::getVersion, item.getVersion()));
        if (latestHistory != null) {
            latestHistory.setAuditTeacherId(loginUser.getUserId());
            latestHistory.setAuditScore(dto.getAuditScore());
            latestHistory.setAuditComment(dto.getAuditComment());
            latestHistory.setAuditTime(now);
            latestHistory.setStatus(item.getStatus());
            versionHistoryMapper.updateById(latestHistory);
        }
    }

    @Override
    public List<MaterialVersionVO> getMaterialVersions(Long id, LoginUser loginUser) {
        StudentMaterialItem item = materialItemMapper.selectById(id);
        if (item == null || (item.getIsDeleted() != null && item.getIsDeleted() == 1)) {
            throw new BusinessException(400, "阶段材料记录不存在");
        }

        // 权限隔离
        if ("STUDENT".equals(loginUser.getUserType()) && !item.getStudentId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权查看其他学生的材料历史版本");
        }
        if ("TEACHER".equals(loginUser.getUserType())) {
            InternshipTaskStudent binding = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, item.getTaskId())
                    .eq(InternshipTaskStudent::getStudentId, item.getStudentId())
                    .eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId()));
            if (binding == null) {
                throw new BusinessException(403, "无权查看非负责学生的材料历史版本");
            }
        }
        if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            InternshipTask task = taskMapper.selectById(item.getTaskId());
            if (task != null && task.getDeptId() != null && !task.getDeptId().equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "院系负责人无权跨院系查看材料历史版本");
            }
        }

        List<MaterialVersionHistory> list = versionHistoryMapper.selectList(new LambdaQueryWrapper<MaterialVersionHistory>()
                .eq(MaterialVersionHistory::getMaterialId, id)
                .orderByDesc(MaterialVersionHistory::getVersion));

        return list.stream().map(h -> {
            String teacherName = null;
            if (h.getAuditTeacherId() != null) {
                SysUser t = userMapper.selectById(h.getAuditTeacherId());
                if (t != null) teacherName = t.getRealName();
            }
            return MaterialVersionVO.builder()
                    .id(h.getId())
                    .materialId(h.getMaterialId())
                    .version(h.getVersion())
                    .contentText(h.getContentText())
                    .attachmentUrl(h.getAttachmentUrl())
                    .fileName(h.getFileName())
                    .fileSize(h.getFileSize())
                    .fileHash(h.getFileHash())
                    .submitTime(h.getSubmitTime())
                    .auditTeacherId(h.getAuditTeacherId())
                    .auditTeacherName(teacherName)
                    .auditScore(h.getAuditScore())
                    .auditComment(h.getAuditComment())
                    .auditTime(h.getAuditTime())
                    .status(h.getStatus())
                    .createdAt(h.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    private int getMinLengthForCode(String materialCode) {
        if ("SUMMARY_REPORT".equals(materialCode)) {
            return phase7Properties.getScore().getMinSummaryLength();
        } else if ("MIDTERM_SUMMARY".equals(materialCode)) {
            return phase7Properties.getMaterial().getMinMidtermSummaryLength();
        }
        return 0;
    }

    private List<ChecklistItem> parseChecklist(String json) {
        List<ChecklistItem> list = new ArrayList<>();
        if (StringUtils.hasText(json)) {
            try {
                List<ChecklistItem> parsed = objectMapper.readValue(json, new TypeReference<List<ChecklistItem>>() {});
                if (parsed != null) {
                    for (ChecklistItem item : parsed) {
                        if (!StringUtils.hasText(item.getMaterialCode()) && StringUtils.hasText(item.getMaterialName())) {
                            String name = item.getMaterialName();
                            if (name.contains("三方") || name.contains("接收")) {
                                item.setMaterialCode("TRIPARTITE_AGREEMENT");
                                if (item.getMaterialType() == null) item.setMaterialType("VOUCHER_FILE");
                            } else if (name.contains("入职") || name.contains("报到")) {
                                item.setMaterialCode("EMPLOYMENT_NOTICE");
                                if (item.getMaterialType() == null) item.setMaterialType("VOUCHER_FILE");
                            } else if (name.contains("安全")) {
                                item.setMaterialCode("SAFETY_TRAINING_RECORD");
                                if (item.getMaterialType() == null) item.setMaterialType("VOUCHER_FILE");
                            } else if (name.contains("中期")) {
                                item.setMaterialCode("MIDTERM_SUMMARY");
                                if (item.getMaterialType() == null) item.setMaterialType("HYBRID");
                            } else if (name.contains("总结") || name.contains("报告")) {
                                item.setMaterialCode("SUMMARY_REPORT");
                                if (item.getMaterialType() == null) item.setMaterialType("REPORT_TEXT");
                            }
                        }
                        if (StringUtils.hasText(item.getMaterialCode())) {
                            list.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse material checklist JSON, falling back to defaults: {}", e.getMessage());
            }
        }
        if (list.isEmpty()) {
            list.add(new ChecklistItem("TRIPARTITE_AGREEMENT", "三方协议书/接收函盖章件", "VOUCHER_FILE", true));
            list.add(new ChecklistItem("EMPLOYMENT_NOTICE", "企业入职报到通知书", "VOUCHER_FILE", true));
            list.add(new ChecklistItem("SAFETY_TRAINING_RECORD", "企业岗位安全培训记录表", "VOUCHER_FILE", true));
            list.add(new ChecklistItem("MIDTERM_SUMMARY", "实习中期进展总结", "HYBRID", true));
            list.add(new ChecklistItem("SUMMARY_REPORT", "毕业实习总结报告", "REPORT_TEXT", true));
        } else {
            ensureItemPresent(list, "TRIPARTITE_AGREEMENT", "三方协议书/接收函盖章件", "VOUCHER_FILE", true);
            ensureItemPresent(list, "EMPLOYMENT_NOTICE", "企业入职报到通知书", "VOUCHER_FILE", true);
            ensureItemPresent(list, "SAFETY_TRAINING_RECORD", "企业岗位安全培训记录表", "VOUCHER_FILE", true);
            ensureItemPresent(list, "MIDTERM_SUMMARY", "实习中期进展总结", "HYBRID", true);
            ensureItemPresent(list, "SUMMARY_REPORT", "毕业实习总结报告", "REPORT_TEXT", true);
        }
        return list;
    }

    private void ensureItemPresent(List<ChecklistItem> list, String code, String name, String type, boolean required) {
        boolean exists = list.stream().anyMatch(i -> code.equalsIgnoreCase(i.getMaterialCode()));
        if (!exists) {
            list.add(new ChecklistItem(code, name, type, required));
        }
    }

    private void validateUrlSecurity(String url) {
        if (url != null && !url.trim().isEmpty() && !url.startsWith("/")) {
            SafeUrlValidator.validateUrl(url);
        }
    }

    @Override
    public MaterialUploadVO uploadMaterialFile(MultipartFile file, Long taskId, String materialCode, LoginUser loginUser) {
        if (!"STUDENT".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅学生允许上传阶段材料附件");
        }
        if (taskId == null) {
            throw new BusinessException(400, "实习任务ID不能为空");
        }
        InternshipTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(400, "实习任务不存在");
        }
        // 归档写保护拦截 (TEST-P7-21)
        archiveService.checkWriteProtection(taskId, loginUser.getUserId());

        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }

        long maxBytes = (long) phase7Properties.getMaterial().getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(400, "上传文件大小超过限制 (最大允许 " + phase7Properties.getMaterial().getMaxFileSizeMb() + "MB)");
        }

        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(400, "文件名不能为空");
        }

        // 防目录穿越与文件名清理
        String cleanOriginalName = Paths.get(originalFilename).getFileName().toString();
        int dotIndex = cleanOriginalName.lastIndexOf('.');
        if (dotIndex < 0) {
            throw new BusinessException(400, "上传文件缺少有效后缀扩展名");
        }
        String ext = cleanOriginalName.substring(dotIndex).toLowerCase();
        List<String> allowedExts = Arrays.asList(".pdf", ".doc", ".docx", ".png", ".jpg", ".jpeg", ".zip");
        if (!allowedExts.contains(ext)) {
            throw new BusinessException(400, "仅支持上传 PDF、Word(doc/docx)、图片(png/jpg/jpeg)及 ZIP 压缩包格式附件");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(500, "读取上传文件内容失败: " + e.getMessage());
        }

        // 文件魔数与特征签名校验
        validateFileMagicNumber(fileBytes, ext);

        // 计算 SHA-256 哈希
        String sha256Hex = calculateSha256(fileBytes);

        // 存储路径与防穿越处理
        String storageDirStr = phase7Properties.getMaterial().getStorageDir();
        Path baseDir = Paths.get(storageDirStr).toAbsolutePath().normalize();
        Path studentDir = baseDir.resolve(String.valueOf(taskId)).resolve(String.valueOf(loginUser.getUserId())).normalize();
        if (!studentDir.startsWith(baseDir)) {
            throw new BusinessException(400, "检测到非法路径穿越攻击");
        }

        try {
            Files.createDirectories(studentDir);
            String storedFileName = UUID.randomUUID().toString().replace("-", "") + "_" + cleanOriginalName.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
            Path targetFile = studentDir.resolve(storedFileName).normalize();
            if (!targetFile.startsWith(baseDir)) {
                throw new BusinessException(400, "检测到非法文件保存路径");
            }
            Files.write(targetFile, fileBytes);

            String attachmentUrl = "/api/v1/internship/materials/files/" + taskId + "/" + loginUser.getUserId() + "/" + storedFileName;
            return MaterialUploadVO.builder()
                    .fileName(cleanOriginalName)
                    .fileSize(file.getSize())
                    .fileHash(sha256Hex)
                    .attachmentUrl(attachmentUrl)
                    .build();
        } catch (Exception e) {
            log.error("Failed to save uploaded material file", e);
            throw new BusinessException(500, "保存材料附件失败: " + e.getMessage());
        }
    }

    @Override
    public void downloadMaterialFile(Long id, HttpServletResponse response, LoginUser loginUser) {
        StudentMaterialItem item = materialItemMapper.selectById(id);
        if (item == null || (item.getIsDeleted() != null && item.getIsDeleted() == 1)) {
            throw new BusinessException(400, "阶段材料记录不存在");
        }
        checkMaterialAccessPermission(item.getTaskId(), item.getStudentId(), loginUser);
        streamAttachmentFile(item.getAttachmentUrl(), item.getFileName(), item.getFileHash(), response);
    }

    @Override
    public void downloadVersionFile(Long versionId, HttpServletResponse response, LoginUser loginUser) {
        MaterialVersionHistory history = versionHistoryMapper.selectById(versionId);
        if (history == null) {
            throw new BusinessException(400, "材料历史版本记录不存在");
        }
        checkMaterialAccessPermission(history.getTaskId(), history.getStudentId(), loginUser);
        streamAttachmentFile(history.getAttachmentUrl(), history.getFileName(), history.getFileHash(), response);
    }

    private void checkMaterialAccessPermission(Long taskId, Long studentId, LoginUser loginUser) {
        if ("STUDENT".equals(loginUser.getUserType())) {
            if (!studentId.equals(loginUser.getUserId())) {
                throw new BusinessException(403, "学生无权下载其他同学的材料附件");
            }
        } else if ("TEACHER".equals(loginUser.getUserType())) {
            InternshipTaskStudent binding = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, taskId)
                    .eq(InternshipTaskStudent::getStudentId, studentId)
                    .eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId()));
            if (binding == null) {
                throw new BusinessException(403, "无权下载非负责学生的阶段材料附件");
            }
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            InternshipTask task = taskMapper.selectById(taskId);
            if (task != null && task.getDeptId() != null && !task.getDeptId().equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "院系负责人无权跨院系下载阶段材料附件");
            }
        }
    }

    private void streamAttachmentFile(String attachmentUrl, String originalFileName, String fileHash, HttpServletResponse response) {
        if (!StringUtils.hasText(attachmentUrl)) {
            throw new BusinessException(400, "该材料未上传附件");
        }
        Path targetFile = resolveAttachmentPath(attachmentUrl);
        if (!Files.exists(targetFile) || !Files.isRegularFile(targetFile)) {
            throw new BusinessException(404, "材料附件在服务器物理存储中不存在或已被清理");
        }
        try {
            String downloadName = StringUtils.hasText(originalFileName) ? originalFileName : targetFile.getFileName().toString();
            response.setContentType(determineContentType(downloadName));
            String encodedName = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
            if (StringUtils.hasText(fileHash)) {
                response.setHeader("X-File-SHA256", fileHash);
            }
            response.setContentLengthLong(Files.size(targetFile));
            Files.copy(targetFile, response.getOutputStream());
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("Failed to stream material attachment", e);
            throw new BusinessException(500, "下载材料附件失败: " + e.getMessage());
        }
    }

    private Path resolveAttachmentPath(String attachmentUrl) {
        String storageDirStr = phase7Properties.getMaterial().getStorageDir();
        Path baseDir = Paths.get(storageDirStr).toAbsolutePath().normalize();

        String relativePath;
        if (attachmentUrl.startsWith("/api/v1/internship/materials/files/")) {
            relativePath = attachmentUrl.substring("/api/v1/internship/materials/files/".length());
        } else {
            relativePath = attachmentUrl;
        }
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new BusinessException(400, "检测到非法路径穿越下载");
        }
        return resolved;
    }

    private String determineContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".doc")) return "application/msword";
        return "application/octet-stream";
    }

    private void validateFileMagicNumber(byte[] bytes, String ext) {
        if (bytes == null || bytes.length < 4) {
            throw new BusinessException(400, "上传文件内容为空或过小，无法识别文件特征");
        }
        boolean matched = false;
        switch (ext) {
            case ".pdf":
                // %PDF
                matched = bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46;
                break;
            case ".png":
                // 89 50 4E 47
                matched = (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
                break;
            case ".jpg":
            case ".jpeg":
                // FF D8 FF
                matched = (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
                break;
            case ".zip":
            case ".docx":
                // PK\x03\x04 (50 4B 03 04) or PK\x05\x06
                matched = (bytes[0] == 0x50 && bytes[1] == 0x4B && (bytes[2] == 0x03 || bytes[2] == 0x05 || bytes[2] == 0x07));
                break;
            case ".doc":
                // OLE CFB: D0 CF 11 E0
                matched = (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF && (bytes[2] & 0xFF) == 0x11 && (bytes[3] & 0xFF) == 0xE0;
                break;
            default:
                matched = false;
        }
        if (!matched) {
            throw new BusinessException(400, "文件扩展名与文件内容魔数特征不匹配，拒绝上传伪造文件");
        }
    }

    private String calculateSha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ChecklistItem {
        @com.fasterxml.jackson.annotation.JsonAlias({"code", "material_code", "materialCode", "key"})
        private String materialCode;
        @com.fasterxml.jackson.annotation.JsonAlias({"name", "material_name", "materialName", "title"})
        private String materialName;
        @com.fasterxml.jackson.annotation.JsonAlias({"type", "material_type", "materialType"})
        private String materialType;
        private Boolean required;
    }
}
