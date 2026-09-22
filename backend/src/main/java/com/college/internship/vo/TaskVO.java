package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 实习任务视图表现对象 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TaskVO extends BaseVO {

    private Long id;
    private String taskCode;
    private String taskName;
    private Long deptId;
    private String deptName;
    private String academicYear;
    private Integer semester;
    private String internshipMode;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal weightEnterprise;
    private BigDecimal weightTeacherProcess;
    private BigDecimal weightWeeklyReport;
    private BigDecimal weightStageMaterial;
    private BigDecimal weightSummary;
    private String materialChecklist;
    private String weeklyFrequency;
    private Integer weeklyDeadlineDay;
    private Integer safetyPassingScore;
    private Integer safetyMaxAttempts;
    private String status;
    private LocalDateTime createTime;

    private List<Long> majorIds;
    private List<String> majorNames;
    private List<Long> classIds;
    private List<String> classNames;
    private Integer studentCount;
}
