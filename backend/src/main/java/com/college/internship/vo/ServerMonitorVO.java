package com.college.internship.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 服务器与运行时环境监控数据传输对象 (API-116)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "服务器与JVM运行指标监控VO")
public class ServerMonitorVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "可用CPU核心数")
    private Integer cpuCores;

    @Schema(description = "系统CPU负载百分比 (0~100)")
    private Double systemCpuLoad;

    @Schema(description = "JVM已分配总内存 (MB)")
    private Long jvmTotalMemoryMB;

    @Schema(description = "JVM空闲内存 (MB)")
    private Long jvmFreeMemoryMB;

    @Schema(description = "JVM已用内存 (MB)")
    private Long jvmUsedMemoryMB;

    @Schema(description = "JVM最大可用内存 (MB)")
    private Long jvmMaxMemoryMB;

    @Schema(description = "Java版本")
    private String jvmVersion;

    @Schema(description = "Java安装路径")
    private String jvmHome;

    @Schema(description = "系统持续运行时间 (秒)")
    private Long upTimeSeconds;

    @Schema(description = "操作系统名称")
    private String osName;

    @Schema(description = "操作系统架构")
    private String osArch;

    @Schema(description = "磁盘总容量 (GB)")
    private Long totalDiskSpaceGB;

    @Schema(description = "磁盘剩余容量 (GB)")
    private Long freeDiskSpaceGB;

    @Schema(description = "磁盘已用容量 (GB)")
    private Long usedDiskSpaceGB;

    @Schema(description = "服务运行状态 (UP)")
    private String status;

    @Schema(description = "采样时间戳")
    private LocalDateTime timestamp;
}
