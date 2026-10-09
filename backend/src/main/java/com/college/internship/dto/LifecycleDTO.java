package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LifecycleDTO {
    @NotBlank(message = "动作不能为空")
    private String action;
    @NotBlank(message = "原因不能为空")
    private String reason;
}
