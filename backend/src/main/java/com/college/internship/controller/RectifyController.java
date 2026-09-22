package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.RectifyCreateDTO;
import com.college.internship.dto.RectifyReviewDTO;
import com.college.internship.dto.RectifySubmitDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IInspectService;
import com.college.internship.vo.RectifyVO;
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
 * 中期检查限期整改控制器 (API-078 ~ API-081)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internship/rectifications")
@RequiredArgsConstructor
@Tag(name = "中期检查限期整改接口", description = "提供限期整改下达、学生提交反馈、导师复核与院系闭环销号")
public class RectifyController {

    private final IInspectService inspectService;

    @PostMapping
    @Operation(summary = "下达限期整改通知 (API-078)")
    public Result<Long> createRectification(@Valid @RequestBody RectifyCreateDTO dto,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        Long id = inspectService.createRectification(dto, loginUser);
        return Result.success(id);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "学生提交整改报告 (API-079)")
    public Result<Void> submitRectification(@PathVariable("id") Long id,
                                            @Valid @RequestBody RectifySubmitDTO dto,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        inspectService.submitRectification(id, dto, loginUser);
        return Result.success();
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "教师复核整改成效 (API-080)")
    public Result<Void> reviewRectification(@PathVariable("id") Long id,
                                            @Valid @RequestBody RectifyReviewDTO dto,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        inspectService.reviewRectification(id, dto, loginUser);
        return Result.success();
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "院系终审销号闭环 (API-081)")
    public Result<Void> closeRectification(@PathVariable("id") Long id,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        inspectService.closeRectification(id, loginUser);
        return Result.success();
    }

    @GetMapping
    @Operation(summary = "查询整改记录列表")
    public Result<List<RectifyVO>> getRectificationList(@RequestParam(value = "taskId", required = false) Long taskId,
                                                        @RequestParam(value = "status", required = false) String status,
                                                        @AuthenticationPrincipal LoginUser loginUser) {
        List<RectifyVO> list = inspectService.getRectificationList(taskId, status, loginUser);
        return Result.success(list);
    }
}
