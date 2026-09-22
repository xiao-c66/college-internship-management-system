package com.college.internship.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 签署安全承诺书请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CommitmentSignDTO extends BaseDTO {

    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    private String insuranceFileUrl;
}
