package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RectifyCreateDTO {
    @NotNull(message = "检查记录ID不能为空")
    private Long inspectionId;
    @NotBlank(message = "整改具体要求不能为空")
    private String rectifyRequirements;
    @NotNull(message = "整改截止日期不能为空")
    private LocalDate deadlineDate;
}
