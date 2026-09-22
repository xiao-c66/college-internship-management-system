package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 实习申报审核操作入参 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AuditDTO extends BaseDTO {

    @NotBlank(message = "审核动作不能为空 (APPROVED 或 REJECTED)")
    private String action; // APPROVED, REJECTED

    private String opinion;
}
