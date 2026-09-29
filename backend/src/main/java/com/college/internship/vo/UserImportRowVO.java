package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 导入预览单行校验结果 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserImportRowVO implements Serializable {

    private Integer rowNum;
    private String userNumber;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String deptName;
    private String majorName;
    private String className;
    private Boolean isValid;
    private String errorMessage;
}
