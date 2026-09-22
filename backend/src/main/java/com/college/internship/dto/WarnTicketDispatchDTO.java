package com.college.internship.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WarnTicketDispatchDTO {
    @NotNull(message = "新责任人ID不能为空")
    private Long assigneeId;
    private String assigneeRole;
    private String remark;
}
