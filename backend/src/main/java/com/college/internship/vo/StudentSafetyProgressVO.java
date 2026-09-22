package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指导教师与院系监控学生安全教育进度 VO (SAFE-008 & API-125)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StudentSafetyProgressVO extends BaseVO {

    private Long studentId;
    private String studentNumber;
    private String studentName;
    private Long deptId;
    private String deptName;
    private Long classId;
    private String className;

    /**
     * 资料阅读进度文本 (如 "1/2 (50%)")
     */
    private String materialProgress;
    private Integer materialsRead;
    private Integer materialsTotal;
    private LocalDateTime studyStartTime;
    private LocalDateTime studyCompleteTime;

    /**
     * 考试情况
     */
    private BigDecimal examScore;
    private Integer examAttempts;
    private Integer isPassed;

    /**
     * 承诺书签署与保险
     */
    private Integer isCommitmentSigned;
    private LocalDateTime signTime;
    private String insuranceFileUrl;

    /**
     * 五阶段安全教育业务主状态
     * NOT_STARTED, STUDYING, PENDING_TEST, PASSED, COMPLETED
     */
    private String statusCode;
    private String statusText;
    private String statusTag;
}
