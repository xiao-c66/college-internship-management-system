package com.college.internship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 学生实习申报表单请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ApplyDTO extends BaseDTO {

    private Long id;

    @NotNull(message = "实习任务ID不能为空")
    private Long taskId;

    @NotBlank(message = "实习单位全称不能为空")
    private String companyName;

    @NotBlank(message = "实习岗位名称不能为空")
    private String jobPosition;

    @NotBlank(message = "工作详细地址不能为空")
    private String jobAddress;

    @NotBlank(message = "企业指导教师/联系人姓名不能为空")
    private String companyContactPerson;

    @NotBlank(message = "联系人电话不能为空")
    private String companyContactPhone;

    private String companyContactEmail;

    @NotNull(message = "实习开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "实习结束日期不能为空")
    private LocalDate endDate;

    @NotBlank(message = "实习组织模式不能为空")
    private String internshipMode;

    private String jobDuties;
    private String agreementFileUrl;
}
