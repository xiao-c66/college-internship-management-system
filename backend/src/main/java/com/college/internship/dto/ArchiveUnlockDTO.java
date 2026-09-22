package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArchiveUnlockDTO {
    @NotBlank(message = "特批解锁事由不能为空")
    private String specialUnlockReason;
    @NotBlank(message = "线下红头批文号不能为空")
    private String specialDocNo;
}
