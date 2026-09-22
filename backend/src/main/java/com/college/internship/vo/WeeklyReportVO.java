package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 周报列表项视图 VO (API-056)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WeeklyReportVO extends BaseVO {

    private Long id;
    private Long taskId;
    private String taskName;
    private Long studentId;
    private String studentName;
    private String studentNumber;
    private String className;
    private Long teacherId;
    private String teacherName;
    private Long deptId;
    private String deptName;
    private Integer weekNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime deadlineTime;
    private String status; // DRAFT, SUBMITTED, REVIEWED, RETURNED
    private Integer isOverdue;
    private Integer overdueDays;
    private LocalDateTime submitTime;
    private BigDecimal score;
    private String reviewComment;
    private Integer version;
    private LocalDateTime updateTime;
}
