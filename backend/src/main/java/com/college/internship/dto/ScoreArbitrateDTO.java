package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScoreArbitrateDTO {
    @NotBlank(message = "仲裁动作不能为空")
    private String action; // PASS, REJECT
    private BigDecimal enterpriseScore;
    private BigDecimal processScore;
    private BigDecimal weeklyScore;
    private BigDecimal materialScore;
    private BigDecimal summaryScore;
    @NotBlank(message = "调分依据或驳回理由不能为空")
    private String auditComment;
    private String approvalDocNo;
}
