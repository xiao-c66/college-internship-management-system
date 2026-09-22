package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 安全测试逐题作答明细实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("safety_exam_answer_detail")
public class SafetyExamAnswerDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long attemptId;
    private Long questionId;
    private String studentAnswer;
    private Integer isCorrect;
    private BigDecimal scoreObtained;
    private LocalDateTime createTime;
}
