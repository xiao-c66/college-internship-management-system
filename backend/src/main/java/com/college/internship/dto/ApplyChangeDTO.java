package com.college.internship.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实习重大信息变更申请提交入参")
public class ApplyChangeDTO {

    @NotNull(message = "原实习申请ID不能为空")
    @Schema(description = "原实习申报主键ID", example = "1001")
    private Long applyId;

    @NotBlank(message = "拟变更实习单位名称不能为空")
    @Size(min = 2, max = 128, message = "单位名称长度须在2-128字符之间")
    @Schema(description = "拟变更实习单位", example = "华为技术有限公司")
    private String newCompanyName;

    @NotBlank(message = "拟变更岗位名称不能为空")
    @Size(min = 2, max = 64, message = "岗位名称长度须在2-64字符之间")
    @Schema(description = "拟变更岗位", example = "后端研发工程师")
    private String newJobPosition;

    @NotBlank(message = "拟变更工作地点不能为空")
    @Size(min = 2, max = 255, message = "工作地点长度须在2-255字符之间")
    @Schema(description = "拟变更工作地点", example = "深圳市龙岗区坂田华为基地")
    private String newJobAddress;

    @NotBlank(message = "企业联系人不能为空")
    @Size(min = 2, max = 64, message = "联系人姓名长度须在2-64字符之间")
    @Schema(description = "企业联系人", example = "王经理")
    private String newContactPerson;

    @NotBlank(message = "企业联系电话不能为空")
    @Size(max = 32, message = "联系电话长度不能超过32字符")
    @Schema(description = "企业联系电话", example = "13912345678")
    private String newContactPhone;

    @Schema(description = "企业联系邮箱", example = "hr@huawei.com")
    private String newContactEmail;

    @NotNull(message = "实习开始日期不能为空")
    @Schema(description = "拟开始日期", example = "2026-07-01")
    private LocalDate newStartDate;

    @NotNull(message = "实习结束日期不能为空")
    @Schema(description = "拟结束日期", example = "2026-10-31")
    private LocalDate newEndDate;

    @NotBlank(message = "实习组织模式不能为空")
    @Schema(description = "实习组织模式: CONCENTRATED(集中) / DISTRIBUTED(分散)", example = "DISTRIBUTED")
    private String newInternshipMode;

    @Schema(description = "拟工作职责陈述", example = "负责微服务架构接口开发与单元测试编写")
    private String newJobDuties;

    @Schema(description = "新三方协议附件URL", example = "/uploads/agreements/agreement_2026_new.pdf")
    private String newAgreementFileUrl;

    @NotBlank(message = "变更事由不能为空")
    @Size(min = 10, max = 1000, message = "变更事由陈述须在10-1000字之间，请详细说明变更背景与必要性")
    @Schema(description = "变更事由", example = "因原单位业务收缩无法提供对应技术岗位，经指导教师同意转入新单位进行专业对口实习")
    private String changeReason;

    @Schema(description = "佐证材料附件URL", example = "/uploads/proofs/proof_doc_01.pdf")
    private String proofFileUrl;
}
