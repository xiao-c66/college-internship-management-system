package com.college.internship.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarnProcessHistoryVO {
    private Long id;
    private Long ticketId;
    private String action;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String contentRemark;
    private String attachmentUrl;
    private LocalDateTime operateTime;
}
