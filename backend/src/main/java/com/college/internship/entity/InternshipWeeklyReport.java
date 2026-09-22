package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学生实习周报主表实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("internship_weekly_report")
public class InternshipWeeklyReport extends BaseEntity {

    private Long taskId;
    private Long studentId;
    private Long teacherId;
    private Long deptId;
    private Integer weekNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime deadlineTime;
    private String workContent;
    private String workSummary;
    private String problemEncountered;
    private String nextWeekPlan;
    private String attachmentUrl;
    private Integer version;
    private String status;
    private Integer isOverdue;
    private Integer overdueDays;
    private LocalDateTime submitTime;
    private BigDecimal score;
    private String reviewComment;
    private String reviewAnnotations;
    private Long reviewerId;
    private LocalDateTime reviewTime;
}
