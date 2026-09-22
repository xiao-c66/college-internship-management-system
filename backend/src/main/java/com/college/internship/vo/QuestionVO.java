package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 学生端试卷试题 VO (脱敏隐藏正确答案与解析)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class QuestionVO extends BaseVO {

    private Long id;
    private Long taskId;
    private String questionType;
    private String stem;
    private String options;
    private Integer score;
    private Integer sortOrder;
}
