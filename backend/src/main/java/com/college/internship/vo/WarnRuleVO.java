package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarnRuleVO {
    private Long id;
    private String ruleCode;
    private String ruleName;
    private String anomalyCategory;
    private String warnLevel;
    private String thresholdParamsJson;
    private String dispatchedRole;
    private Integer handlingTimeoutDays;
    private Integer isEnabled;
    private Integer version;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
