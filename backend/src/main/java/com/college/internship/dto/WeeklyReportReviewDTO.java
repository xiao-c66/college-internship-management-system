package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 教师周报批阅/退回入参 DTO (API-059)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WeeklyReportReviewDTO extends BaseDTO {

    @NotBlank(message = "批阅动作不能为空 (APPROVE 或 RETURN)")
    private String action; // APPROVE, RETURN

    private BigDecimal score; // 批阅通过时必填 (0.00-100.00)

    private String reviewComment; // 评语或退回原因

    private String reviewAnnotations; // 逐项批注 (可选JSON)
}
