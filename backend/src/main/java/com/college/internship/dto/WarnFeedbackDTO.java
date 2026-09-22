package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WarnFeedbackDTO {
    @NotBlank(message = "申辩事实说明不能为空")
    private String studentFeedback;
    private String attachmentUrl;
}
