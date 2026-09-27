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
import com.college.internship.vo.MaterialVersionVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.college.internship.util.SafeUrlValidator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

        Long targetStudentId = studentId;
        if ("STUDENT".equals(loginUser.getUserType())) {
            targetStudentId = loginUser.getUserId();
        } else if (targetStudentId == null) {
            throw new BusinessException(400, "指导教师或管理端必须指定目标学生ID");
        } else if ("TEACHER".equals(loginUser.getUserType())) {
            // 教师权限隔离校验：只能查带教学生
            InternshipTaskStudent binding = taskStudentMapper.selectOne(new LambdaQueryWrapper<InternshipTaskStudent>()
                    .eq(InternshipTaskStudent::getTaskId, taskId)
                    .eq(InternshipTaskStudent::getStudentId, targetStudentId)
                    .eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId()));
            if (binding == null) {
                throw new BusinessException(403, "无权查看非负责学生的阶段材料");
            }
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType())) {
            if (task.getDeptId() != null && !task.getDeptId().equals(loginUser.getDeptId())) {
                throw new BusinessException(403, "院系负责人无权跨院系查看阶段材料清单");
            }
        }

        SysUser studentUser = userMapper.selectById(targetStudentId);
        String studentName = studentUser != null ? studentUser.getRealName() : "";
        String studentNo = studentUser != null ? studentUser.getUsername() : "";

        // 解析材料清单
        List<ChecklistItem> checklist = parseChecklist(task.getMaterialChecklist());

        // 查询已提交的材料实体
        List<StudentMaterialItem> existingItems = materialItemMapper.selectList(new LambdaQueryWrapper<StudentMaterialItem>()
                .eq(StudentMaterialItem::getTaskId, taskId)
                .eq(StudentMaterialItem::getStudentId, targetStudentId)
                .eq(BasePhase7Entity::getIsDeleted, 0));
        Map<String, StudentMaterialItem> itemMap = existingItems.stream()
                .collect(Collectors.toMap(StudentMaterialItem::getMaterialCode, item -> item, (k1, k2) -> k1));

        List<MaterialItemVO> resultList = new ArrayList<>();
        for (ChecklistItem item : checklist) {
            StudentMaterialItem entity = itemMap.get(item.getMaterialCode());
            int minLen = getMinLengthForCode(item.getMaterialCode());

            MaterialItemVO vo = MaterialItemVO.builder()
                    .taskId(taskId)
                    .studentId(targetStudentId)
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
        return resultList;
    }

    @Override
    public MaterialItemVO getMaterialDetail(Long id, LoginUser loginUser) {
        StudentMaterialItem entity = materialItemMapper.selectById(id);
        if (entity == null || entity.getIsDeleted() == 1) {
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

        // 0. 外部附件 URL 安全与 SSRF 防护校验
        if (StringUtils.hasText(dto.getAttachmentUrl())) {
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
        if (item == null || item.getIsDeleted() == 1) {
            throw new BusinessException(400, "阶段材料记录不存在");
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
            if (!StringUtils.hasText(dto.getAuditComment())) {
                throw new BusinessException(400, "退回必须填写查验意见");
            }
        } else if (!isApproved) {
            throw new BusinessException(400, "查验动作非法 (仅支持 APPROVED 或 RETURNED)");
        }

        LocalDateTime now = LocalDateTime.now();
        item.setStatus(isApproved ? "APPROVED" : "RETURNED");
        item.setAuditScore(dto.getAuditScore());
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
        if (item == null || item.getIsDeleted() == 1) {
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
        if (url != null && !url.trim().isEmpty()) {
            SafeUrlValidator.validateUrl(url);
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
