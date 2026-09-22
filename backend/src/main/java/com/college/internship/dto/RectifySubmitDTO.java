package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RectifySubmitDTO {
    @NotBlank(message = "整改措施说明不能为空")
    private String studentExplanation;
    private String evidenceAttachmentUrl;
}
