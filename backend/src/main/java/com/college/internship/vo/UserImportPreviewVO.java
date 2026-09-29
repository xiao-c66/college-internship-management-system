package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 用户批量导入前置预览响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserImportPreviewVO implements Serializable {

    private Integer totalCount;
    private Integer validCount;
    private Integer invalidCount;
    private String previewToken;
    private List<UserImportRowVO> previewRows;
}
