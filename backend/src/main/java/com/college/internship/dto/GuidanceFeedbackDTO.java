package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 学生确认指导记录并提交在岗反馈入参 DTO (API-067)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GuidanceFeedbackDTO extends BaseDTO {

    @NotBlank(message = "在岗反馈内容不能为空")
    private String studentFeedback;
}
