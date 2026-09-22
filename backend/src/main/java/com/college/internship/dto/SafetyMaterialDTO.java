package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 安全教育资料创建/更新 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SafetyMaterialDTO extends BaseDTO {

    private Long taskId;

    @NotBlank(message = "资料标题不能为空")
    private String title;

    private String contentType;
    private String contentBody;
    private String fileUrl;
    private Integer sortOrder;
    private Integer status;
}
