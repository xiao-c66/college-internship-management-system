package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 验证码出参 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CaptchaVO extends BaseVO {

    private String captchaKey;
    private String captchaCode;
    private String captchaBase64;
    private Long expireSeconds;
}
