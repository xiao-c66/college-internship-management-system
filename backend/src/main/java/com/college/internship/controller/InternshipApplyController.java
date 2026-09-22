package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.ApplyDTO;
import com.college.internship.dto.AuditDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IInternshipApplyService;
import com.college.internship.vo.ApplyVO;
import com.college.internship.vo.AuditHistoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生实习申报与双级审核核心控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/applies")
@RequiredArgsConstructor
@Tag(name = "实习申报与审核接口", description = "提供学生申报暂存与提交、APPLY-009生效后只读保护、教师初审与院系终审流转")
public class InternshipApplyController {

    private final IInternshipApplyService applyService;

    @PostMapping("/draft")
    @Operation(summary = "暂存实习申报草稿", description = "学生暂存申报信息，状态置为 DRAFT (APPLY-006)")
    public Result<ApplyVO> saveDraft(@Valid @RequestBody ApplyDTO dto,
                                     @AuthenticationPrincipal LoginUser loginUser) {
        ApplyVO vo = applyService.saveDraft(dto, loginUser);
        return Result.success("申报草稿暂存成功", vo);
    }

    @PostMapping("/submit")
    @Operation(summary = "正式提交实习申报", description = "学生提交申报并进入指导教师初审队列 (APPLY-006)")
    public Result<ApplyVO> submitApply(@Valid @RequestBody ApplyDTO dto,
                                       @AuthenticationPrincipal LoginUser loginUser) {
        ApplyVO vo = applyService.submitApply(dto, loginUser);
        return Result.success("实习申报已成功提交审核", vo);
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改实习申报", description = "APPLY-009规则：若申报已审核通过生效(APPROVED)，后端Service层一票否决普通更新！")
    public Result<ApplyVO> updateApply(@PathVariable("id") Long id,
                                       @Valid @RequestBody ApplyDTO dto,
                                       @AuthenticationPrincipal LoginUser loginUser) {
        ApplyVO vo = applyService.updateApply(id, dto, loginUser);
        return Result.success("申报信息已更新", vo);
    }

    @GetMapping("/my")
    @Operation(summary = "学生获取当前任务申报", description = "查询本人在当前实习任务中的申报状态、表单明细与审批轨迹")
    public Result<ApplyVO> getMyApply(@RequestParam("taskId") Long taskId,
                                      @AuthenticationPrincipal LoginUser loginUser) {
        ApplyVO vo = applyService.getMyApply(taskId, loginUser);
        return Result.success(vo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取指定申报详情", description = "根据申请ID查询完整申报信息与审批历史")
    public Result<ApplyVO> getApplyById(@PathVariable("id") Long id,
                                        @AuthenticationPrincipal LoginUser loginUser) {
        ApplyVO vo = applyService.getApplyById(id, loginUser);
        return Result.success(vo);
    }

    @GetMapping
    @Operation(summary = "查询待审/已审申请列表", description = "指导教师与院系负责人查询管辖范围内的实习申报单据")
    public Result<List<ApplyVO>> listApplies(@RequestParam(value = "taskId", required = false) Long taskId,
                                             @RequestParam(value = "status", required = false) String status,
                                             @AuthenticationPrincipal LoginUser loginUser) {
        List<ApplyVO> list = applyService.listAppliesForAudit(taskId, status, loginUser);
        return Result.success(list);
    }

    @PostMapping("/{id}/audit")
    @Operation(summary = "实习申报审核流转 (初审与终审)", description = "教师初审(TEACHER_APPROVED/REJECTED)，院系终审(APPROVED/REJECTED)。退回修改时强制校验退回原因不少于5个字符！")
    public Result<Void> auditApply(@PathVariable("id") Long id,
                                   @Valid @RequestBody AuditDTO dto,
                                   @AuthenticationPrincipal LoginUser loginUser) {
        applyService.auditApply(id, dto, loginUser);
        return Result.success("审核操作已完成", null);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "获取申报审批流转历史轨迹", description = "查询该申报从初审到终审的所有操作人、时间、意见与版本快照 (REVIEW-006)")
    public Result<List<AuditHistoryVO>> getAuditHistories(@PathVariable("id") Long id,
                                                          @AuthenticationPrincipal LoginUser loginUser) {
        List<AuditHistoryVO> histories = applyService.getAuditHistories(id, loginUser);
        return Result.success(histories);
    }
}
