package com.college.internship.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户批量导入单行数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserImportRowDTO implements Serializable {

    private Integer rowNum;
    private String userNumber;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String deptName;
    private String majorName;
    private String className;
}
