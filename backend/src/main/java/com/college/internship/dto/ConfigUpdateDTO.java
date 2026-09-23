package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 系统运维参数更新 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "配置值不能为空")
    private String configValue;

    private String remark;
}
