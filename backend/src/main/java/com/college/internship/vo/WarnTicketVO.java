package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarnTicketVO {
    private Long id;
    private String ticketNo;
    private Long taskId;
    private String taskName;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private String className;
    private Long teacherId;
    private String teacherName;
    private Long deptId;
    private String deptName;
    private Long ruleId;
    private String ruleCode;
    private String ruleName;
    private Integer ruleVersion;
    private String warnLevel;
    private String warnTitle;
    private String evidenceSnapshotJson;
    private String status;
    private Integer isUpgraded;
    private LocalDateTime upgradedTime;
    private String upgradeReason;
    private Long currentAssigneeId;
    private String currentAssigneeName;
    private String currentAssigneeRole;
    private String studentFeedback;
    private LocalDateTime studentFeedbackTime;
    private String teacherInvestigation;
    private String handlingMeasures;
    private LocalDateTime closedTime;
    private Long closedBy;
    private String closedByName;
    private LocalDateTime createdAt;
    private List<WarnProcessHistoryVO> processHistory;
}
