package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class StudentAdjustDTO {
    @NotBlank(message = "操作类型不能为空")
    private String action;
    
    @NotEmpty(message = "学生列表不能为空")
    private List<Long> studentIds;
    
    @NotBlank(message = "原因不能为空")
    private String reason;
}
