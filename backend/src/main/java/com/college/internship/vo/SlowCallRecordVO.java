package com.college.internship.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 慢调用明细记录数据传输对象 (API-119)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "慢调用明细VO")
public class SlowCallRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "记录序号")
    private Long id;

    @Schema(description = "请求URI")
    private String uri;

    @Schema(description = "HTTP方法")
    private String method;

    @Schema(description = "调用耗时 (毫秒)")
    private Long costMs;

    @Schema(description = "调用发生时间")
    private LocalDateTime timestamp;

    @Schema(description = "操作人员账号/名称")
    private String operatorName;

    @Schema(description = "客户端来源IP")
    private String clientIp;

    @Schema(description = "请求参数摘要")
    private String paramSummary;
}
