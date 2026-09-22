package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WarnHandleDTO {
    @NotBlank(message = "处置动作不能为空")
    private String action; // PROCESSING, CLOSED, FALSE_ALARM_CLOSED
    private String teacherInvestigation;
    private String handlingMeasures;
    private String attachmentUrl;
}
