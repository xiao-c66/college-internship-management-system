package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 安全教育试题实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("safety_test_question")
public class SafetyTestQuestion extends BaseEntity {

    private Long taskId;
    private String questionType;
    private String stem;
    private String options;
    private String correctAnswer;
    private Integer score;
    private String analysis;
    private Integer sortOrder;
    private Integer status;
}
