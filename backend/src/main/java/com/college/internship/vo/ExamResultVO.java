package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试交卷评分结果 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExamResultVO extends BaseVO {

    private Long attemptId;
    private Integer attemptNo;
    private BigDecimal totalScore;
    private Integer passingScore;
    private Integer isPassed;
    private String resultDesc;
    private LocalDateTime submitTime;
}
