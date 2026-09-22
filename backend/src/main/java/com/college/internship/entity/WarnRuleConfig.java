package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 全局异常预警规则配置表实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("warn_rule_config")
public class WarnRuleConfig extends BasePhase7Entity {

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
}
