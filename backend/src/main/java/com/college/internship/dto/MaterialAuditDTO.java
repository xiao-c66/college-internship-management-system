package com.college.internship.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialAuditDTO {
    @DecimalMin(value = "0.00", message = "分数不能低于0")
    @DecimalMax(value = "100.00", message = "分数不能高于100")
    private BigDecimal auditScore;

    @NotBlank(message = "查验动作不能为空")
    private String action; // APPROVED, RETURNED

    private String auditComment;
}
