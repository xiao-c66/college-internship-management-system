package com.college.internship.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录安全审计数据传输对象 (API-120)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录安全审计VO")
public class SysLoginLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "操作人员账号")
    private String operatorName;

    @Schema(description = "操作人员用户ID")
    private Long operatorId;

    @Schema(description = "业务类型 (LOGIN / LOGOUT)")
    private String businessType;

    @Schema(description = "登录IP")
    private String operIp;

    @Schema(description = "登录请求地址")
    private String operUrl;

    @Schema(description = "请求参数快照 (密码已脱敏)")
    private String operParam;

    @Schema(description = "返回结果")
    private String jsonResult;

    @Schema(description = "登录状态 (1-成功, 0-失败)")
    private Integer status;

    @Schema(description = "失败提示原因")
    private String errorMsg;

    @Schema(description = "登录/注销时间")
    private LocalDateTime operTime;
}
