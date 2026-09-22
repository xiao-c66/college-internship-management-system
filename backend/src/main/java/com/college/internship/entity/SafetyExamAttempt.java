package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生安全测试作答记录实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("safety_exam_attempt")
public class SafetyExamAttempt extends BaseEntity {

    private Long taskId;
    private Long studentId;
    private Integer attemptNo;
    private BigDecimal totalScore;
    private Integer isPassed;
    private LocalDateTime startTime;
    private LocalDateTime submitTime;
}
