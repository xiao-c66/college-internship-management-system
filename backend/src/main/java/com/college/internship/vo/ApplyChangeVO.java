package com.college.internship.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实习重大信息变更申请VO")
public class ApplyChangeVO {

    @Schema(description = "变更申请主键ID")
    private Long id;

    @Schema(description = "原实习申请ID")
    private Long applyId;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "学生ID")
    private Long studentId;

    @Schema(description = "学号")
    private String studentNumber;

    @Schema(description = "学生姓名")
    private String studentName;

    @Schema(description = "院系ID")
    private Long deptId;

    @Schema(description = "指导教师ID")
    private Long teacherId;

    @Schema(description = "指导教师姓名")
    private String teacherName;

    // 原信息快照
    @Schema(description = "原实习单位")
    private String origCompanyName;

    @Schema(description = "原岗位")
    private String origJobPosition;

    @Schema(description = "原工作地点")
    private String origJobAddress;

    @Schema(description = "原联系人")
    private String origContactPerson;

    @Schema(description = "原联系电话")
    private String origContactPhone;

    @Schema(description = "原联系邮箱")
    private String origContactEmail;

    @Schema(description = "原开始日期")
    private LocalDate origStartDate;

    @Schema(description = "原结束日期")
    private LocalDate origEndDate;

    @Schema(description = "原组织模式")
    private String origInternshipMode;

    @Schema(description = "原工作职责")
    private String origJobDuties;

    @Schema(description = "原协议文件URL")
    private String origAgreementFileUrl;

    // 拟变更的新信息
    @Schema(description = "拟变更实习单位")
    private String newCompanyName;

    @Schema(description = "拟变更岗位")
    private String newJobPosition;

    @Schema(description = "拟变更地点")
    private String newJobAddress;

    @Schema(description = "拟变更联系人")
    private String newContactPerson;

    @Schema(description = "拟变更联系电话")
    private String newContactPhone;

    @Schema(description = "拟变更联系邮箱")
    private String newContactEmail;

    @Schema(description = "拟开始日期")
    private LocalDate newStartDate;

    @Schema(description = "拟结束日期")
    private LocalDate newEndDate;

    @Schema(description = "拟组织模式")
    private String newInternshipMode;

    @Schema(description = "拟工作职责")
    private String newJobDuties;

    @Schema(description = "新协议文件URL")
    private String newAgreementFileUrl;

    // 变更事由与补充材料
    @Schema(description = "变更事由")
    private String changeReason;

    @Schema(description = "证明材料附件URL")
    private String proofFileUrl;

    // 状态机
    @Schema(description = "状态: PENDING_TEACHER, PENDING_DEPT, APPROVED, REJECTED")
    private String changeStatus;

    @Schema(description = "当前审批节点: TEACHER_INITIAL, DEPT_FINAL, FINISHED")
    private String currentStep;

    @Schema(description = "提交时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "审批流转历史轨迹列表")
    private List<ApplyChangeHistoryVO> histories;
}
