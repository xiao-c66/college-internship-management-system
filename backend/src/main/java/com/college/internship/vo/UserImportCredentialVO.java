package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 批量导入生成的账号与一次性独立临时密码凭据
 * 仅在导入成功时单次返回给管理员，数据库只存哈希，不落盘明文日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserImportCredentialVO implements Serializable {

    private Integer rowNum;
    private String userNumber;
    private String username;
    private String realName;
    private String temporaryPassword;
    private String status;
}
