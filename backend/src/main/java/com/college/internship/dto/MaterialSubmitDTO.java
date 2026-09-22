package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MaterialSubmitDTO {
    private Long taskId;
    @NotBlank(message = "材料编码不能为空")
    private String materialCode;
    private String contentText;
    private String attachmentUrl;
    private String fileName;
    private Long fileSize;
}
