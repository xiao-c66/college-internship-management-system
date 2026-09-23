package com.college.internship.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 接口耗时与慢SQL度量监控看板数据传输对象 (API-119)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "慢查询与耗时分析监控看板VO")
public class SlowSqlMonitorVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "慢调用判定基准阈值 (毫秒)")
    private Long thresholdMs;

    @Schema(description = "内存滑动采样窗口最大容量 (上限1000)")
    private Integer ringBufferSize;

    @Schema(description = "当前有效采样样本数")
    private Integer sampleCount;

    @Schema(description = "P95分位数耗时 (毫秒)")
    private Long p95CostMs;

    @Schema(description = "P99分位数耗时 (毫秒)")
    private Long p99CostMs;

    @Schema(description = "最小耗时 (毫秒)")
    private Long minCostMs;

    @Schema(description = "最大耗时 (毫秒)")
    private Long maxCostMs;

    @Schema(description = "平均耗时 (毫秒)")
    private Double avgCostMs;

    @Schema(description = "当前滑动窗口吞吐量 QPS")
    private Double qps;

    @Schema(description = "慢调用明细列表 (最多保留500条)")
    private List<SlowCallRecordVO> slowCalls;
}
