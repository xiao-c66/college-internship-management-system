package com.college.internship.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 全局教学通知公告实体 (阶段8切片4)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_notice")
public class SysNotice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务防重键 (人工批次号或事件唯一标识)
     */
    private String dedupKey;

    /**
     * 公告标题
     */
    private String noticeTitle;

    /**
     * 类型 (NOTICE-通知, ANNOUNCE-公告)
     */
    private String noticeType;

    /**
     * 公告正文 (服务端 Jsoup 白名单净化存储)
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

    /**
     * 发布人ID
     */
    private Long publisherId;

    /**
     * 发布人姓名
     */
    private String publisherName;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 逻辑删除标识 (0-未删除, 1-已删除)
     */
    private Integer isDeleted;
}
