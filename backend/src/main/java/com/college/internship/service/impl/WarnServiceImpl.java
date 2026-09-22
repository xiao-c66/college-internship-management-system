package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.Phase7Properties;
import com.college.internship.dto.WarnFeedbackDTO;
import com.college.internship.dto.WarnHandleDTO;
import com.college.internship.dto.WarnRuleUpdateDTO;
import com.college.internship.dto.WarnTicketDispatchDTO;
import com.college.internship.entity.BaseClass;
import com.college.internship.entity.BaseDepartment;
import com.college.internship.entity.BasePhase7Entity;
import com.college.internship.entity.InternshipApply;
import com.college.internship.entity.InternshipTask;
import com.college.internship.entity.InternshipTaskStudent;
import com.college.internship.entity.InternshipWeeklyReport;
import com.college.internship.entity.MidtermInspection;
import com.college.internship.entity.MidtermRectification;
import com.college.internship.entity.SafetyCommitmentSign;
import com.college.internship.entity.ScoreSummary;
import com.college.internship.entity.StudentMaterialItem;
import com.college.internship.entity.SysUser;
import com.college.internship.entity.WarnProcessHistory;
import com.college.internship.entity.WarnRuleConfig;
import com.college.internship.entity.WarnTicket;
import com.college.internship.mapper.BaseClassMapper;
import com.college.internship.mapper.BaseDepartmentMapper;
import com.college.internship.mapper.InternshipApplyMapper;
import com.college.internship.mapper.InternshipTaskMapper;
import com.college.internship.mapper.InternshipTaskStudentMapper;
import com.college.internship.mapper.InternshipWeeklyReportMapper;
import com.college.internship.mapper.MidtermInspectionMapper;
import com.college.internship.mapper.MidtermRectificationMapper;
import com.college.internship.mapper.SafetyCommitmentSignMapper;
import com.college.internship.mapper.ScoreSummaryMapper;
import com.college.internship.mapper.StudentMaterialItemMapper;
import com.college.internship.mapper.SysUserMapper;
import com.college.internship.mapper.WarnProcessHistoryMapper;
import com.college.internship.mapper.WarnRuleConfigMapper;
import com.college.internship.mapper.WarnTicketMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IWarnService;
import com.college.internship.vo.WarnProcessHistoryVO;
import com.college.internship.vo.WarnRuleVO;
import com.college.internship.vo.WarnTicketVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 异常预警全景引擎业务服务实现类 (API-082 ~ API-090)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarnServiceImpl implements IWarnService {

    private final WarnRuleConfigMapper ruleMapper;
    private final WarnTicketMapper ticketMapper;
    private final WarnProcessHistoryMapper historyMapper;
    private final InternshipTaskMapper taskMapper;
    private final InternshipTaskStudentMapper taskStudentMapper;
    private final SafetyCommitmentSignMapper commitmentSignMapper;
    private final InternshipApplyMapper applyMapper;
    private final InternshipWeeklyReportMapper weeklyReportMapper;
    private final MidtermInspectionMapper inspectionMapper;
    private final MidtermRectificationMapper rectificationMapper;
    private final StudentMaterialItemMapper materialItemMapper;
    private final ScoreSummaryMapper scoreMapper;
    private final SysUserMapper userMapper;
    private final BaseDepartmentMapper departmentMapper;
    private final BaseClassMapper classMapper;
    private final Phase7Properties phase7Properties;

    // 内存防刷流控映射表 (用户ID -> 上次扫描毫秒时间戳)
    private static final Map<Long, Long> USER_LAST_SCAN_TIME = new ConcurrentHashMap<>();

    @Override
    public List<WarnRuleVO> getRuleList(LoginUser loginUser) {
        List<WarnRuleConfig> list = ruleMapper.selectList(new LambdaQueryWrapper<WarnRuleConfig>()
                .eq(BasePhase7Entity::getIsDeleted, 0)
                .orderByAsc(WarnRuleConfig::getRuleCode));

        return list.stream().map(r -> WarnRuleVO.builder()
                .id(r.getId())
                .ruleCode(r.getRuleCode())
                .ruleName(r.getRuleName())
                .anomalyCategory(r.getAnomalyCategory())
                .warnLevel(r.getWarnLevel())
                .thresholdParamsJson(r.getThresholdParamsJson())
                .dispatchedRole(r.getDispatchedRole())
                .handlingTimeoutDays(r.getHandlingTimeoutDays())
                .isEnabled(r.getIsEnabled())
                .version(r.getVersion())
                .description(r.getDescription())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build()).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(Long id, WarnRuleUpdateDTO dto, LoginUser loginUser) {
        if (!"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅超级管理员允许修改预警规则配置");
        }

        WarnRuleConfig rule = ruleMapper.selectById(id);
        if (rule == null || rule.getIsDeleted() == 1) {
            throw new BusinessException(400, "预警规则不存在");
        }

        rule.setRuleName(dto.getRuleName());
        rule.setWarnLevel(dto.getWarnLevel());
        rule.setThresholdParamsJson(dto.getThresholdParamsJson());
        if (StringUtils.hasText(dto.getDispatchedRole())) {
            rule.setDispatchedRole(dto.getDispatchedRole());
        }
        rule.setHandlingTimeoutDays(dto.getHandlingTimeoutDays());
        rule.setDescription(dto.getDescription());
        rule.setVersion(rule.getVersion() + 1);

        ruleMapper.updateById(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleRule(Long id, LoginUser loginUser) {
        if (!"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅超级管理员允许切换预警规则启用状态");
        }

        WarnRuleConfig rule = ruleMapper.selectById(id);
        if (rule == null || rule.getIsDeleted() == 1) {
            throw new BusinessException(400, "预警规则不存在");
        }

        rule.setIsEnabled(rule.getIsEnabled() == 1 ? 0 : 1);
        ruleMapper.updateById(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeScan(Long taskId, LoginUser loginUser) {
        // 1. 角色权限拦截：学生禁止触发全盘扫描 (TEST-P7-09)
        if ("STUDENT".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "学生端禁止调用异常预警全盘扫描接口");
        }

        // 2. 10秒防刷流控拦截 (TEST-P7-09)
        long nowMs = System.currentTimeMillis();
        Long lastScan = USER_LAST_SCAN_TIME.get(loginUser.getUserId());
        int rateLimitSec = phase7Properties.getWarn().getScanRateLimitSeconds();
        if (lastScan != null && (nowMs - lastScan) < rateLimitSec * 1000L) {
            long waitSec = (rateLimitSec * 1000L - (nowMs - lastScan)) / 1000L + 1;
            throw new BusinessException(429, "扫描请求过于频繁，请等待 " + waitSec + " 秒后重试");
        }
        USER_LAST_SCAN_TIME.put(loginUser.getUserId(), nowMs);

        // 3. 圈定扫描学生名单 (TEST-P7-09 教师只能扫描管辖学生)
        LambdaQueryWrapper<InternshipTaskStudent> wrapper = new LambdaQueryWrapper<InternshipTaskStudent>();
        if (taskId != null) {
            wrapper.eq(InternshipTaskStudent::getTaskId, taskId);
        }
        if ("TEACHER".equals(loginUser.getUserType())) {
            wrapper.eq(InternshipTaskStudent::getTeacherId, loginUser.getUserId());
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType()) && loginUser.getDeptId() != null) {
            // 院系学生圈定
            List<InternshipTask> deptTasks = taskMapper.selectList(new LambdaQueryWrapper<InternshipTask>()
                    .eq(InternshipTask::getDeptId, loginUser.getDeptId()));
            if (!deptTasks.isEmpty()) {
                wrapper.in(InternshipTaskStudent::getTaskId, deptTasks.stream().map(InternshipTask::getId).collect(Collectors.toList()));
            }
        }

        List<InternshipTaskStudent> studentList = taskStudentMapper.selectList(wrapper);
        List<WarnRuleConfig> enabledRules = ruleMapper.selectList(new LambdaQueryWrapper<WarnRuleConfig>()
                .eq(WarnRuleConfig::getIsEnabled, 1)
                .eq(BasePhase7Entity::getIsDeleted, 0));

        int scannedStudents = studentList.size();
        int newTickets = 0;
        int upgradedCount = 0;

        for (InternshipTaskStudent student : studentList) {
            Long curTaskId = student.getTaskId();
            Long curStudentId = student.getStudentId();
            Long curTeacherId = student.getTeacherId();
            InternshipTask task = taskMapper.selectById(curTaskId);
            Long curDeptId = task != null ? task.getDeptId() : 1L;

            for (WarnRuleConfig rule : enabledRules) {
                boolean hit = checkAnomaly(rule, curTaskId, curStudentId);
                if (hit) {
                    // 双键去重机制：以 ruleCode + taskId + studentId 构造唯一特征 (TEST-P7-10)
                    String dedupKey = "DEDUP_" + rule.getRuleCode() + "_" + curTaskId + "_" + curStudentId;
                    String activeDedupKey = dedupKey;

                    // 检查活动工单是否已存在
                    WarnTicket existingActive = ticketMapper.selectOne(new LambdaQueryWrapper<WarnTicket>()
                            .eq(WarnTicket::getActiveDedupKey, activeDedupKey)
                            .eq(BasePhase7Entity::getIsDeleted, 0));

                    if (existingActive == null) {
                        String ticketNo = "WT" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                                + String.format("%06d", (System.nanoTime() % 1000000));

                        WarnTicket ticket = WarnTicket.builder()
                                .ticketNo(ticketNo)
                                .taskId(curTaskId)
                                .studentId(curStudentId)
                                .teacherId(curTeacherId)
                                .deptId(curDeptId)
                                .ruleId(rule.getId())
                                .ruleVersion(rule.getVersion())
                                .warnLevel(rule.getWarnLevel())
                                .warnTitle("触发预警: " + rule.getRuleName())
                                .evidenceSnapshotJson("{\"ruleCode\":\"" + rule.getRuleCode() + "\",\"timestamp\":" + System.currentTimeMillis() + "}")
                                .status("TRIGGERED")
                                .isUpgraded(0)
                                .currentAssigneeId(curTeacherId != null ? curTeacherId : loginUser.getUserId())
                                .currentAssigneeRole("TEACHER")
                                .dedupKey(dedupKey)
                                .activeDedupKey(activeDedupKey)
                                .build();

                        try {
                            ticketMapper.insert(ticket);
                            newTickets++;

                            WarnProcessHistory history = WarnProcessHistory.builder()
                                    .ticketId(ticket.getId())
                                    .action("TRIGGERED")
                                    .operatorId(loginUser.getUserId())
                                    .operatorName(loginUser.getRealName())
                                    .operatorRole(loginUser.getUserType())
                                    .contentRemark("系统自动扫描命中预警规则: " + rule.getRuleName())
                                    .operateTime(LocalDateTime.now())
                                    .build();
                            historyMapper.insert(history);
                        } catch (DuplicateKeyException e) {
                            // uk_active_dedup 物理阻断，保持工单唯一 (TEST-P7-10)
                            log.info("Active ticket already exists for dedup key {}, skipping duplicate creation.", dedupKey);
                        }
                    }
                }
            }
        }

        // 4. 超时自动升级至院系巡检 (TEST-P7-13)
        List<WarnTicket> openTickets = ticketMapper.selectList(new LambdaQueryWrapper<WarnTicket>()
                .eq(WarnTicket::getIsUpgraded, 0)
                .in(WarnTicket::getStatus, List.of("TRIGGERED", "DISPATCHED", "PROCESSING"))
                .eq(BasePhase7Entity::getIsDeleted, 0));

        LocalDateTime now = LocalDateTime.now();
        int timeoutDays = phase7Properties.getWarn().getHandlingTimeoutDays();
        for (WarnTicket ticket : openTickets) {
            if (ticket.getCreatedAt() != null && ticket.getCreatedAt().plusDays(timeoutDays).isBefore(now)) {
                ticket.setIsUpgraded(1);
                ticket.setUpgradedTime(now);
                ticket.setCurrentAssigneeRole("DEPT_ADMIN");
                ticket.setUpgradeReason("处置超时自动升级至院系");
                ticketMapper.updateById(ticket);
                upgradedCount++;

                WarnProcessHistory hist = WarnProcessHistory.builder()
                        .ticketId(ticket.getId())
                        .action("TIMEOUT_UPGRADE")
                        .operatorId(0L)
                        .operatorName("SYSTEM")
                        .operatorRole("SYSTEM")
                        .contentRemark("工单处置逾期 " + timeoutDays + " 天，系统自动升级至院系责任人")
                        .operateTime(now)
                        .build();
                historyMapper.insert(hist);
            }
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("scannedStudents", scannedStudents);
        resultMap.put("newTickets", newTickets);
        resultMap.put("upgradedCount", upgradedCount);
        return resultMap;
    }

    private boolean checkAnomaly(WarnRuleConfig rule, Long taskId, Long studentId) {
        String code = rule.getRuleCode();
        if ("WARN_01".equals(code)) {
            // 承诺书未签署
            SafetyCommitmentSign sign = commitmentSignMapper.selectOne(new LambdaQueryWrapper<SafetyCommitmentSign>()
                    .eq(SafetyCommitmentSign::getTaskId, taskId)
                    .eq(SafetyCommitmentSign::getStudentId, studentId));
            return sign == null;
        } else if ("WARN_03".equals(code)) {
            // 实习申报未通过或未提交
            InternshipApply apply = applyMapper.selectOne(new LambdaQueryWrapper<InternshipApply>()
                    .eq(InternshipApply::getTaskId, taskId)
                    .eq(InternshipApply::getStudentId, studentId));
            return apply == null || !"APPROVED".equals(apply.getApplyStatus());
        } else if ("WARN_04".equals(code)) {
            // 周报提交滞后: 周报总数较少
            Long count = weeklyReportMapper.selectCount(new LambdaQueryWrapper<InternshipWeeklyReport>()
                    .eq(InternshipWeeklyReport::getTaskId, taskId)
                    .eq(InternshipWeeklyReport::getStudentId, studentId)
                    .eq(InternshipWeeklyReport::getStatus, "REVIEWED"));
            return count == 0;
        } else if ("WARN_07".equals(code)) {
            // 中期检查存在突出问题
            MidtermInspection ins = inspectionMapper.selectOne(new LambdaQueryWrapper<MidtermInspection>()
                    .eq(MidtermInspection::getTaskId, taskId)
                    .eq(MidtermInspection::getStudentId, studentId)
                    .eq(MidtermInspection::getHasProblem, 1)
                    .eq(BasePhase7Entity::getIsDeleted, 0));
            return ins != null && "PENDING_RECTIFY".equals(ins.getStatus());
        } else if ("WARN_08".equals(code)) {
            // 限期整改超期未闭环
            MidtermRectification rect = rectificationMapper.selectOne(new LambdaQueryWrapper<MidtermRectification>()
                    .eq(MidtermRectification::getTaskId, taskId)
                    .eq(MidtermRectification::getStudentId, studentId)
                    .ne(MidtermRectification::getStatus, "CLOSED")
                    .eq(BasePhase7Entity::getIsDeleted, 0));
            return rect != null && rect.getDeadlineDate() != null && rect.getDeadlineDate().isBefore(LocalDate.now());
        } else if ("WARN_09".equals(code)) {
            // 总结报告未提交
            StudentMaterialItem summary = materialItemMapper.selectOne(new LambdaQueryWrapper<StudentMaterialItem>()
                    .eq(StudentMaterialItem::getTaskId, taskId)
                    .eq(StudentMaterialItem::getStudentId, studentId)
                    .eq(StudentMaterialItem::getMaterialCode, "SUMMARY_REPORT")
                    .eq(BasePhase7Entity::getIsDeleted, 0));
            return summary == null || !"APPROVED".equals(summary.getStatus());
        } else if ("WARN_10".equals(code)) {
            // 五维成绩存在不及格
            ScoreSummary score = scoreMapper.selectOne(new LambdaQueryWrapper<ScoreSummary>()
                    .eq(ScoreSummary::getTaskId, taskId)
                    .eq(ScoreSummary::getStudentId, studentId)
                    .eq(BasePhase7Entity::getIsDeleted, 0));
            return score != null && score.getFinalScore() != null && score.getFinalScore().compareTo(new BigDecimal("60.00")) < 0;
        }
        return false;
    }

    @Override
    public List<WarnTicketVO> getTicketList(Long taskId, String warnLevel, String status, Integer isUpgraded, LoginUser loginUser) {
        LambdaQueryWrapper<WarnTicket> wrapper = new LambdaQueryWrapper<WarnTicket>()
                .eq(BasePhase7Entity::getIsDeleted, 0)
                .orderByDesc(BasePhase7Entity::getCreatedAt);

        if (taskId != null) wrapper.eq(WarnTicket::getTaskId, taskId);
        if (StringUtils.hasText(warnLevel)) wrapper.eq(WarnTicket::getWarnLevel, warnLevel);
        if (StringUtils.hasText(status)) wrapper.eq(WarnTicket::getStatus, status);
        if (isUpgraded != null) wrapper.eq(WarnTicket::getIsUpgraded, isUpgraded);

        if ("STUDENT".equals(loginUser.getUserType())) {
            wrapper.eq(WarnTicket::getStudentId, loginUser.getUserId());
        } else if ("TEACHER".equals(loginUser.getUserType())) {
            wrapper.and(w -> w.eq(WarnTicket::getTeacherId, loginUser.getUserId())
                    .or().eq(WarnTicket::getCurrentAssigneeId, loginUser.getUserId()));
        } else if ("DEPT_ADMIN".equals(loginUser.getUserType()) && loginUser.getDeptId() != null) {
            wrapper.eq(WarnTicket::getDeptId, loginUser.getDeptId());
        }

        List<WarnTicket> list = ticketMapper.selectList(wrapper);
        return list.stream().map(this::convertToTicketVO).collect(Collectors.toList());
    }

    @Override
    public WarnTicketVO getTicketDetail(Long id, LoginUser loginUser) {
        WarnTicket ticket = ticketMapper.selectById(id);
        if (ticket == null || ticket.getIsDeleted() == 1) {
            throw new BusinessException(400, "预警工单不存在");
        }

        if ("STUDENT".equals(loginUser.getUserType()) && !ticket.getStudentId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权查看其他学生的预警工单详情");
        }

        WarnTicketVO vo = convertToTicketVO(ticket);

        // 加载流转历史
        List<WarnProcessHistory> histories = historyMapper.selectList(new LambdaQueryWrapper<WarnProcessHistory>()
                .eq(WarnProcessHistory::getTicketId, id)
                .orderByAsc(WarnProcessHistory::getOperateTime));
        vo.setProcessHistory(histories.stream().map(h -> WarnProcessHistoryVO.builder()
                .id(h.getId())
                .ticketId(h.getTicketId())
                .action(h.getAction())
                .operatorId(h.getOperatorId())
                .operatorName(h.getOperatorName())
                .operatorRole(h.getOperatorRole())
                .contentRemark(h.getContentRemark())
                .attachmentUrl(h.getAttachmentUrl())
                .operateTime(h.getOperateTime())
                .build()).collect(Collectors.toList()));

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatchTicket(Long id, WarnTicketDispatchDTO dto, LoginUser loginUser) {
        if (!"DEPT_ADMIN".equals(loginUser.getUserType()) && !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅院系负责人或管理端允许派发工单");
        }

        WarnTicket ticket = ticketMapper.selectById(id);
        if (ticket == null || ticket.getIsDeleted() == 1) {
            throw new BusinessException(400, "预警工单不存在");
        }

        ticket.setCurrentAssigneeId(dto.getAssigneeId());
        if (StringUtils.hasText(dto.getAssigneeRole())) {
            ticket.setCurrentAssigneeRole(dto.getAssigneeRole());
        }
        ticket.setStatus("DISPATCHED");
        ticketMapper.updateById(ticket);

        WarnProcessHistory history = WarnProcessHistory.builder()
                .ticketId(id)
                .action("DISPATCH")
                .operatorId(loginUser.getUserId())
                .operatorName(loginUser.getRealName())
                .operatorRole(loginUser.getUserType())
                .contentRemark("工单转派责任人: " + dto.getAssigneeId() + " (" + (dto.getRemark() != null ? dto.getRemark() : "") + ")")
                .operateTime(LocalDateTime.now())
                .build();
        historyMapper.insert(history);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitFeedback(Long id, WarnFeedbackDTO dto, LoginUser loginUser) {
        WarnTicket ticket = ticketMapper.selectById(id);
        if (ticket == null || ticket.getIsDeleted() == 1) {
            throw new BusinessException(400, "预警工单不存在");
        }

        if ("STUDENT".equals(loginUser.getUserType()) && !ticket.getStudentId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权申辩其他学生的预警工单");
        }

        // 学生在线申辩存证 (TEST-P7-11)
        if (!StringUtils.hasText(dto.getStudentFeedback())) {
            throw new BusinessException(400, "申辩事实说明不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        ticket.setStudentFeedback(dto.getStudentFeedback());
        ticket.setStudentFeedbackTime(now);
        ticket.setStatus("PROCESSING");
        ticketMapper.updateById(ticket);

        WarnProcessHistory history = WarnProcessHistory.builder()
                .ticketId(id)
                .action("STUDENT_FEEDBACK")
                .operatorId(loginUser.getUserId())
                .operatorName(loginUser.getRealName())
                .operatorRole(loginUser.getUserType())
                .contentRemark("学生提交在线申辩: " + dto.getStudentFeedback())
                .attachmentUrl(dto.getAttachmentUrl())
                .operateTime(now)
                .build();
        historyMapper.insert(history);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleTicket(Long id, WarnHandleDTO dto, LoginUser loginUser) {
        WarnTicket ticket = ticketMapper.selectById(id);
        if (ticket == null || ticket.getIsDeleted() == 1) {
            throw new BusinessException(400, "预警工单不存在");
        }

        // 若已升级至院系，指导教师无权关闭 (TEST-P7-13)
        if (ticket.getIsUpgraded() == 1 && "TEACHER".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "工单已升级至院系，只能由院系负责人闭环处置");
        }

        LocalDateTime now = LocalDateTime.now();
        String action = dto.getAction();

        if ("FALSE_ALARM_CLOSED".equalsIgnoreCase(action)) {
            // 误报关闭释放活动键 (TEST-P7-12)
            ticket.setStatus("FALSE_ALARM_CLOSED");
            ticket.setTeacherInvestigation(dto.getTeacherInvestigation());
            ticket.setClosedTime(now);
            ticket.setClosedBy(loginUser.getUserId());
            ticket.setActiveDedupKey(null); // 释放 active_dedup_key 为 NULL
            ticketMapper.updateById(ticket);

            WarnProcessHistory hist = WarnProcessHistory.builder()
                    .ticketId(id)
                    .action("FALSE_ALARM_CLOSED")
                    .operatorId(loginUser.getUserId())
                    .operatorName(loginUser.getRealName())
                    .operatorRole(loginUser.getUserType())
                    .contentRemark("经核实判定为误报并销号: " + dto.getTeacherInvestigation())
                    .attachmentUrl(dto.getAttachmentUrl())
                    .operateTime(now)
                    .build();
            historyMapper.insert(hist);
        } else if ("CLOSED".equalsIgnoreCase(action)) {
            ticket.setStatus("CLOSED");
            ticket.setHandlingMeasures(dto.getHandlingMeasures());
            ticket.setClosedTime(now);
            ticket.setClosedBy(loginUser.getUserId());
            ticket.setActiveDedupKey(null); // 正常闭环释放 active_dedup_key
            ticketMapper.updateById(ticket);

            WarnProcessHistory hist = WarnProcessHistory.builder()
                    .ticketId(id)
                    .action("CLOSED")
                    .operatorId(loginUser.getUserId())
                    .operatorName(loginUser.getRealName())
                    .operatorRole(loginUser.getUserType())
                    .contentRemark("已采取干预措施并完成闭环销号: " + dto.getHandlingMeasures())
                    .attachmentUrl(dto.getAttachmentUrl())
                    .operateTime(now)
                    .build();
            historyMapper.insert(hist);
        } else {
            ticket.setStatus("PROCESSING");
            if (StringUtils.hasText(dto.getTeacherInvestigation())) {
                ticket.setTeacherInvestigation(dto.getTeacherInvestigation());
            }
            if (StringUtils.hasText(dto.getHandlingMeasures())) {
                ticket.setHandlingMeasures(dto.getHandlingMeasures());
            }
            ticketMapper.updateById(ticket);

            WarnProcessHistory hist = WarnProcessHistory.builder()
                    .ticketId(id)
                    .action("PROCESSING")
                    .operatorId(loginUser.getUserId())
                    .operatorName(loginUser.getRealName())
                    .operatorRole(loginUser.getUserType())
                    .contentRemark("登记核实调查与干预中记录")
                    .attachmentUrl(dto.getAttachmentUrl())
                    .operateTime(now)
                    .build();
            historyMapper.insert(hist);
        }
    }

    private WarnTicketVO convertToTicketVO(WarnTicket t) {
        InternshipTask task = taskMapper.selectById(t.getTaskId());
        SysUser student = userMapper.selectById(t.getStudentId());
        SysUser teacher = t.getTeacherId() != null ? userMapper.selectById(t.getTeacherId()) : null;
        BaseDepartment dept = departmentMapper.selectById(t.getDeptId());
        WarnRuleConfig rule = ruleMapper.selectById(t.getRuleId());
        SysUser assignee = userMapper.selectById(t.getCurrentAssigneeId());
        SysUser closeUser = t.getClosedBy() != null ? userMapper.selectById(t.getClosedBy()) : null;

        return WarnTicketVO.builder()
                .id(t.getId())
                .ticketNo(t.getTicketNo())
                .taskId(t.getTaskId())
                .taskName(task != null ? task.getTaskName() : "")
                .studentId(t.getStudentId())
                .studentName(student != null ? student.getRealName() : "")
                .studentNo(student != null ? student.getUsername() : "")
                .teacherId(t.getTeacherId())
                .teacherName(teacher != null ? teacher.getRealName() : "")
                .deptId(t.getDeptId())
                .deptName(dept != null ? dept.getDeptName() : "")
                .ruleId(t.getRuleId())
                .ruleCode(rule != null ? rule.getRuleCode() : "")
                .ruleName(rule != null ? rule.getRuleName() : "")
                .ruleVersion(t.getRuleVersion())
                .warnLevel(t.getWarnLevel())
                .warnTitle(t.getWarnTitle())
                .evidenceSnapshotJson(t.getEvidenceSnapshotJson())
                .status(t.getStatus())
                .isUpgraded(t.getIsUpgraded())
                .upgradedTime(t.getUpgradedTime())
                .upgradeReason(t.getUpgradeReason())
                .currentAssigneeId(t.getCurrentAssigneeId())
                .currentAssigneeName(assignee != null ? assignee.getRealName() : "")
                .currentAssigneeRole(t.getCurrentAssigneeRole())
                .studentFeedback(t.getStudentFeedback())
                .studentFeedbackTime(t.getStudentFeedbackTime())
                .teacherInvestigation(t.getTeacherInvestigation())
                .handlingMeasures(t.getHandlingMeasures())
                .closedTime(t.getClosedTime())
                .closedBy(t.getClosedBy())
                .closedByName(closeUser != null ? closeUser.getRealName() : "")
                .createdAt(t.getCreatedAt())
                .build();
    }
}
