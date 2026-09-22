package com.college.internship.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InspectionSubmitDTO {
    @NotNull(message = "检查方案ID不能为空")
    private Long planId;
    @NotNull(message = "学生ID不能为空")
    private Long studentId;
    private String inspectionType;
    private LocalDateTime inspectionDate;
    private String companySituation;
    private String studentPerformance;
    private String guidanceFulfillment;
    @NotNull(message = "评分不能为空")
    @DecimalMin(value = "0.00", message = "评分不能低于0")
    @DecimalMax(value = "100.00", message = "评分不能超过100")
    private BigDecimal score;
    private String attachmentUrl;
    private Integer hasProblem;
    private String problemDesc;
}
