package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionVO {
    private Long id;
    private Long planId;
    private String planName;
    private Long taskId;
    private String taskName;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private String className;
    private Long teacherId;
    private String teacherName;
    private Long inspectorId;
    private String inspectorName;
    private String samplingBatchNo;
    private String inspectionType;
    private LocalDateTime inspectionDate;
    private String companySituation;
    private String studentPerformance;
    private String guidanceFulfillment;
    private BigDecimal score;
    private String attachmentUrl;
    private Integer hasProblem;
    private String problemDesc;
    private String status;
    private Long rectificationId;
    private String rectificationStatus;
    private LocalDateTime createdAt;
}
