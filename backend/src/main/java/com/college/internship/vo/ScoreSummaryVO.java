package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreSummaryVO {
    private Long id;
    private Long taskId;
    private String taskName;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private String majorName;
    private String className;
    private Long teacherId;
    private String teacherName;
    private Long deptId;
    private String deptName;
    private BigDecimal enterpriseScore;
    private BigDecimal processScore;
    private BigDecimal weeklyScore;
    private BigDecimal materialScore;
    private BigDecimal summaryScore;
    private BigDecimal finalScore;
    private String scoreLevel;
    private String gradeRuleSnapshotJson;
    private String evaluationComment;
    private String enterpriseEvaluationUrl;
    private String status;
    private LocalDateTime publicityStartTime;
    private LocalDateTime publicityEndTime;
    private LocalDateTime confirmedTeacherTime;
    private Long auditedDeptUserId;
    private String auditedDeptUserName;
    private LocalDateTime auditedDeptTime;
    private Integer version;
    private LocalDateTime createdAt;
    private List<ScoreAuditHistoryVO> auditHistory;
}
