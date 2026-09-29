package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员重置密码单次出参 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordResultVO implements Serializable {

    private Long userId;
    private String username;
    private String realName;
    private String temporaryPassword;
}
