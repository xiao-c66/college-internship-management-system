package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实习成绩五维综合评定主表实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("score_summary")
public class ScoreSummary extends BasePhase7Entity {

    private Long taskId;
    private Long studentId;
    private Long teacherId;
    private Long deptId;
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
    private LocalDateTime auditedDeptTime;

    @Version
    private Integer version;
}
