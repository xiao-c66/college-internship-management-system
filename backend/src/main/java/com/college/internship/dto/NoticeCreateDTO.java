package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 创建/发布通知公告请求参数 (API-114)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务防重键 (人工批次号或事件唯一标识)
     */
    @NotBlank(message = "业务防重键不能为空")
    @Size(max = 128, message = "业务防重键长度不能超过128字符")
    private String dedupKey;

    /**
     * 公告标题
     */
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 200, message = "公告标题长度不能超过200字符")
    private String noticeTitle;

    /**
     * 类型 (NOTICE-通知, ANNOUNCE-公告)
     */
    @NotBlank(message = "公告类型不能为空")
    private String noticeType;

    /**
     * 公告正文 (支持富文本HTML，服务端强制Jsoup过滤)
     */
    @NotBlank(message = "公告正文不能为空")
    private String noticeContent;

    /**
     * 发布范围 (ALL-全校, DEPT-本院系)
     */
    @NotBlank(message = "发布范围不能为空")
    private String targetScope;

    /**
     * 限定院系ID
     */
    private Long targetDeptId;
}
