package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScoreAppealDTO {
    @NotNull(message = "成绩ID不能为空")
    private Long scoreId;
    @NotBlank(message = "申诉理由不能为空")
    private String appealReason;
    private String appealAttachmentUrl;
}
