package com.college.internship.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务操作审计数据传输对象 (API-121)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "业务操作审计VO")
public class SysOperationLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "操作模块/标题")
    private String title;

    @Schema(description = "业务类型")
    private String businessType;

    @Schema(description = "调用方法")
    private String method;

    @Schema(description = "请求方式")
    private String requestMethod;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人账号")
    private String operatorName;

    @Schema(description = "操作人院系ID")
    private Long deptId;

    @Schema(description = "操作人院系名称")
    private String deptName;

    @Schema(description = "请求URI")
    private String operUrl;

    @Schema(description = "操作IP (院系管理员查阅时已掩码)")
    private String operIp;

    @Schema(description = "请求参数快照 (敏感字段已脱敏)")
    private String operParam;

    @Schema(description = "返回结果/执行摘要")
    private String jsonResult;

    @Schema(description = "执行耗时 (毫秒，解析自jsonResult)")
    private Long costMs;

    @Schema(description = "操作状态 (1-成功, 0-失败)")
    private Integer status;

    @Schema(description = "错误原因 (院系管理员查阅时已隐藏底层堆栈)")
    private String errorMsg;

    @Schema(description = "操作时间")
    private LocalDateTime operTime;
}
