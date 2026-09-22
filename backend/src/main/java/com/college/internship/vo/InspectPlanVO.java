package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectPlanVO {
    private Long id;
    private String planName;
    private Long taskId;
    private String taskName;
    private Long deptId;
    private String deptName;
    private String samplingMode;
    private BigDecimal samplingRatio;
    private LocalDate startDate;
    private LocalDate endDate;
    private String expertGroup;
    private String remark;
    private String status;
    private Long createdBy;
    private String createdByName;
    private Integer sampledStudentCount;
    private Integer inspectedCount;
    private LocalDateTime createdAt;
}
