package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.InspectPlanCreateDTO;
import com.college.internship.dto.InspectionSubmitDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IInspectService;
import com.college.internship.vo.InspectPlanVO;
import com.college.internship.vo.InspectionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 中期检查方案与督导记录控制器 (API-074 ~ API-077)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internship/inspections")
@RequiredArgsConstructor
@Tag(name = "中期检查督导接口", description = "提供中期检查方案编制、抽样名单生成、督导检查记录录入与查询")
public class InspectController {

    private final IInspectService inspectService;

    @PostMapping("/plans")
    @Operation(summary = "编制中期检查方案 (API-074)")
    public Result<Long> createInspectPlan(@Valid @RequestBody InspectPlanCreateDTO dto,
                                          @AuthenticationPrincipal LoginUser loginUser) {
        Long id = inspectService.createInspectPlan(dto, loginUser);
        return Result.success(id);
    }

    @PostMapping("/plans/{id}/sample")
    @Operation(summary = "执行抽样名单生成 (API-075)")
    public Result<Integer> executeSampling(@PathVariable("id") Long id,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        Integer count = inspectService.executeSampling(id, loginUser);
        return Result.success(count);
    }

    @GetMapping("/plans")
    @Operation(summary = "查询检查方案列表")
    public Result<List<InspectPlanVO>> getInspectPlans(@RequestParam(value = "taskId", required = false) Long taskId,
                                                       @AuthenticationPrincipal LoginUser loginUser) {
        List<InspectPlanVO> list = inspectService.getInspectPlans(taskId, loginUser);
        return Result.success(list);
    }

    @PostMapping
    @Operation(summary = "录入督导检查记录 (API-076)")
    public Result<Long> submitInspection(@Valid @RequestBody InspectionSubmitDTO dto,
                                         @AuthenticationPrincipal LoginUser loginUser) {
        Long id = inspectService.submitInspection(dto, loginUser);
        return Result.success(id);
    }

    @GetMapping
    @Operation(summary = "查询检查记录列表 (API-077)")
    public Result<List<InspectionVO>> getInspectionList(@RequestParam(value = "planId", required = false) Long planId,
                                                        @RequestParam(value = "taskId", required = false) Long taskId,
                                                        @RequestParam(value = "status", required = false) String status,
                                                        @AuthenticationPrincipal LoginUser loginUser) {
        List<InspectionVO> list = inspectService.getInspectionList(planId, taskId, status, loginUser);
        return Result.success(list);
    }
}
