package com.college.internship.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 全局教学通知公告视图对象 (API-112 ~ API-115)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "教学通知公告视图")
public class SysNoticeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "公告ID")
    private Long id;

    @Schema(description = "业务防重键")
    private String dedupKey;

    @Schema(description = "公告标题")
    private String noticeTitle;

    @Schema(description = "类型 (NOTICE-通知, ANNOUNCE-公告)")
    private String noticeType;

    @Schema(description = "公告正文 (富文本已清洗)")
    private String noticeContent;

    @Schema(description = "发布范围 (ALL-全校, DEPT-本院系)")
    private String targetScope;

    @Schema(description = "限定院系ID")
    private Long targetDeptId;

    @Schema(description = "状态 (0-关闭/撤回, 1-正常发布)")
    private Integer status;

    @Schema(description = "发布人ID")
    private Long publisherId;

    @Schema(description = "发布人姓名")
    private String publisherName;

    @Schema(description = "发布时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    @Schema(description = "当前登录用户是否已读")
    private Boolean isRead;

    @Schema(description = "当前登录用户阅读时间戳")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readTime;
}
