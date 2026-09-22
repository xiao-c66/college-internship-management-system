package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 实习批次任务主表实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("internship_task")
public class InternshipTask extends BaseEntity {

    private String taskCode;
    private String taskName;
    private Long deptId;
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
    private String gradeRulesJson;
    private String weeklyFrequency;
    private Integer weeklyDeadlineDay;
    private Integer safetyPassingScore;
    private Integer safetyMaxAttempts;
    private String status;
}
