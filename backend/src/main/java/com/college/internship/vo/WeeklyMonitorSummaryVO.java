package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 院系/校管周报过程监控大盘数据 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WeeklyMonitorSummaryVO extends BaseVO {

    private Long taskId;
    private String taskName;
    private Long deptId;
    private String deptName;

    private Integer totalStudents;        // 总参与学生数
    private Integer totalExpectedReports; // 累计应交周报数
    private Integer totalSubmittedReports;// 实际已交周报数
    private Integer totalOnTimeReports;   // 按期提交数
    private Integer totalOverdueReports;  // 逾期提交数
    private Integer totalReviewedReports; // 导师已批阅数
    private Integer totalPendingReports;  // 待批阅数

    private BigDecimal submissionRate;    // 提交率 (%)
    private BigDecimal reviewRate;        // 批阅率 (%)
    private BigDecimal onTimeRate;        // 按期率 (%)

    private List<WeeklyWeekStatVO> weekStats; // 各周次明细

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyWeekStatVO implements Serializable {
        private Integer weekNumber;
        private Integer expectedCount;
        private Integer submittedCount;
        private Integer reviewedCount;
        private Integer overdueCount;
        private BigDecimal submitRate;
    }
}
