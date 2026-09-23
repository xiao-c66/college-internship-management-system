package com.college.internship.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 本地 Caffeine 缓存运行状态监控数据传输对象 (API-117)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Caffeine缓存监控VO")
public class CacheMonitorVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "缓存名称")
    private String cacheName;

    @Schema(description = "缓存命中总次数")
    private Long hitCount;

    @Schema(description = "未命中总次数")
    private Long missCount;

    @Schema(description = "缓存命中率 (0.0~1.0)")
    private Double hitRate;

    @Schema(description = "对象驱逐总次数")
    private Long evictionCount;

    @Schema(description = "当前内存中预估缓存项数量")
    private Long estimatedSize;

    @Schema(description = "配置的最大容量上限")
    private Long maxSize;

    @Schema(description = "写入后过期时效 (分钟)")
    private Long expireAfterWriteMinutes;
}
