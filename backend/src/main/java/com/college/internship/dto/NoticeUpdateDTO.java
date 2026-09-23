package com.college.internship.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 修改/撤回通知公告请求参数 (API-115)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 公告标题
     */
    @Size(max = 200, message = "公告标题长度不能超过200字符")
    private String noticeTitle;

    /**
     * 类型 (NOTICE-通知, ANNOUNCE-公告)
     */
    private String noticeType;

    /**
     * 公告正文 (支持富文本HTML，服务端强制Jsoup过滤)
     */
    private String noticeContent;

    /**
     * 发布范围 (ALL-全校, DEPT-本院系)
     */
    private String targetScope;

    /**
     * 限定院系ID
     */
    private Long targetDeptId;

    /**
     * 状态 (0-关闭/撤回, 1-正常发布)
     */
    private Integer status;
}
