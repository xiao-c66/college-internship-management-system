package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RectifyReviewDTO {
    @NotBlank(message = "复核动作不能为空")
    private String action; // PASSED, REJECTED
    @NotBlank(message = "复核评价意见不能为空")
    private String reviewComment;
}
