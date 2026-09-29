package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 确认执行批量导入入参 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserImportExecuteDTO implements Serializable {

    @NotBlank(message = "导入账号类型不能为空 (STUDENT 或 TEACHER)")
    private String userType;

    /**
     * 绑定的服务端预览防篡改 Token (推荐)
     */
    private String previewToken;

    /**
     * 待导入用户记录列表 (当未传 previewToken 时降级读取)
     */
    private List<UserImportRowDTO> rows;
}
