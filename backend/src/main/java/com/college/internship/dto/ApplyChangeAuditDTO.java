package com.college.internship.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实习重大信息变更审批入参")
public class ApplyChangeAuditDTO {

    @NotBlank(message = "审核动作不能为空")
    @Pattern(regexp = "^(APPROVE|REJECT)$", message = "审核动作必须为 APPROVE 或 REJECT")
    @Schema(description = "审批动作", example = "APPROVE")
    private String auditAction;

    @NotBlank(message = "审核意见不能为空")
    @Size(min = 5, max = 500, message = "审核意见长度须在5-500字之间")
    @Schema(description = "审核意见", example = "情况属实，新单位对口度高，同意变更申请")
    private String auditOpinion;
}
