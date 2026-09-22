package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.WarnFeedbackDTO;
import com.college.internship.dto.WarnHandleDTO;
import com.college.internship.dto.WarnRuleUpdateDTO;
import com.college.internship.dto.WarnTicketDispatchDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IWarnService;
import com.college.internship.vo.WarnRuleVO;
import com.college.internship.vo.WarnTicketVO;
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
import java.util.Map;

/**
 * 异常预警全景引擎控制器 (API-082 ~ API-090)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/warn")
@RequiredArgsConstructor
@Tag(name = "过程异常预警接口", description = "提供预警规则配置、全盘扫描、工单多维检索、申辩存证与闭环销号")
public class WarnController {

    private final IWarnService warnService;

    @GetMapping("/rules")
    @Operation(summary = "查询预警规则配置列表 (API-082)")
    public Result<List<WarnRuleVO>> getRuleList(@AuthenticationPrincipal LoginUser loginUser) {
        List<WarnRuleVO> list = warnService.getRuleList(loginUser);
        return Result.success(list);
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "更新预警规则参数 (API-083)")
    public Result<Void> updateRule(@PathVariable("id") Long id,
                                   @Valid @RequestBody WarnRuleUpdateDTO dto,
                                   @AuthenticationPrincipal LoginUser loginUser) {
        warnService.updateRule(id, dto, loginUser);
        return Result.success();
    }

    @PutMapping("/rules/{id}/toggle")
    @Operation(summary = "启用/停用预警规则 (API-084)")
    public Result<Void> toggleRule(@PathVariable("id") Long id,
                                   @AuthenticationPrincipal LoginUser loginUser) {
        warnService.toggleRule(id, loginUser);
        return Result.success();
    }

    @PostMapping("/scan")
    @Operation(summary = "手动触发全盘异常扫描 (API-085)")
    public Result<Map<String, Object>> executeScan(@RequestParam(value = "taskId", required = false) Long taskId,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        Map<String, Object> summary = warnService.executeScan(taskId, loginUser);
        return Result.success(summary);
    }

    @GetMapping("/tickets")
    @Operation(summary = "预警工单分页检索 (API-086)")
    public Result<List<WarnTicketVO>> getTicketList(@RequestParam(value = "taskId", required = false) Long taskId,
                                                    @RequestParam(value = "warnLevel", required = false) String warnLevel,
                                                    @RequestParam(value = "status", required = false) String status,
                                                    @RequestParam(value = "isUpgraded", required = false) Integer isUpgraded,
                                                    @AuthenticationPrincipal LoginUser loginUser) {
        List<WarnTicketVO> list = warnService.getTicketList(taskId, warnLevel, status, isUpgraded, loginUser);
        return Result.success(list);
    }

    @GetMapping("/tickets/{id}")
    @Operation(summary = "预警工单详情与证据链 (API-087)")
    public Result<WarnTicketVO> getTicketDetail(@PathVariable("id") Long id,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        WarnTicketVO detail = warnService.getTicketDetail(id, loginUser);
        return Result.success(detail);
    }

    @PostMapping("/tickets/{id}/dispatch")
    @Operation(summary = "预警工单派发/转派 (API-088)")
    public Result<Void> dispatchTicket(@PathVariable("id") Long id,
                                       @Valid @RequestBody WarnTicketDispatchDTO dto,
                                       @AuthenticationPrincipal LoginUser loginUser) {
        warnService.dispatchTicket(id, dto, loginUser);
        return Result.success();
    }

    @PostMapping("/tickets/{id}/feedback")
    @Operation(summary = "学生提交在线申辩说明 (API-089)")
    public Result<Void> submitFeedback(@PathVariable("id") Long id,
                                       @Valid @RequestBody WarnFeedbackDTO dto,
                                       @AuthenticationPrincipal LoginUser loginUser) {
        warnService.submitFeedback(id, dto, loginUser);
        return Result.success();
    }

    @PostMapping("/tickets/{id}/handle")
    @Operation(summary = "预警处置与闭环销号 (API-090)")
    public Result<Void> handleTicket(@PathVariable("id") Long id,
                                     @Valid @RequestBody WarnHandleDTO dto,
                                     @AuthenticationPrincipal LoginUser loginUser) {
        warnService.handleTicket(id, dto, loginUser);
        return Result.success();
    }
}
