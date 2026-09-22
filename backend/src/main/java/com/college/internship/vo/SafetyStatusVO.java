package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生安全教育准入五阶段状态 VO
 * (NOT_STARTED, STUDYING, PENDING_TEST, PASSED, COMPLETED)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SafetyStatusVO extends BaseVO {

    private Long taskId;
    private Long studentId;
    private String statusCode; // NOT_STARTED, STUDYING, PENDING_TEST, PASSED, COMPLETED
    private String statusDesc;
    private Integer materialsTotal;
    private Integer materialsRead;
    private String readMaterialIds;
    private LocalDateTime studyStartTime;
    private LocalDateTime studyCompleteTime;
    private Integer examAttempts;
    private Integer maxAttempts;
    private BigDecimal highestScore;
    private Integer passingScore;
    private Integer isPassed;
    private Integer isCommitmentSigned;
    private LocalDateTime signTime;
    private String insuranceFileUrl;
}
