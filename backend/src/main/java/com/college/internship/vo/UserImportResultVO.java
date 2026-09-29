package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 批量导入执行结果 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserImportResultVO implements Serializable {

    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private List<UserImportCredentialVO> credentials;
}
