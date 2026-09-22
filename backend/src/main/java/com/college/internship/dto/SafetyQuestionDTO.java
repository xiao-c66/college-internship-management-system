package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 安全教育试题新增/更新 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SafetyQuestionDTO extends BaseDTO {

    private Long taskId;

    @NotBlank(message = "题型不能为空")
    private String questionType;

    @NotBlank(message = "题干不能为空")
    private String stem;

    @NotBlank(message = "选项内容不能为空")
    private String options;

    @NotBlank(message = "正确答案不能为空")
    private String correctAnswer;

    @NotNull(message = "分值不能为空")
    private Integer score;

    private String analysis;
    private Integer sortOrder;
    private Integer status;
}
