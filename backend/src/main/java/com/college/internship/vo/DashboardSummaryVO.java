package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 角色工作台动态摘要数据 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DashboardSummaryVO extends BaseVO {

    private String userType;
    private String roleCode;
    private String realName;
    private String deptName;

    /**
     * 业务指标映射字典 (不同角色适配专属的统计聚合字段)
     */
    private Map<String, Object> metrics;
}
