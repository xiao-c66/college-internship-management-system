package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.MaterialAuditDTO;
import com.college.internship.dto.MaterialSubmitDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IMaterialService;
import com.college.internship.vo.MaterialItemVO;
import com.college.internship.vo.MaterialVersionVO;
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
 * 阶段材料与实习总结报告控制器 (API-060 ~ API-064)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internship/materials")
@RequiredArgsConstructor
@Tag(name = "阶段材料与总结报告接口", description = "提供阶段材料规范指引、提报、查验打分及历史版本追溯")
public class MaterialController {

    private final IMaterialService materialService;

    @GetMapping
    @Operation(summary = "获取阶段材料清单与提报要求 (API-060)")
    public Result<List<MaterialItemVO>> getMaterialList(@RequestParam("taskId") Long taskId,
                                                        @RequestParam(value = "studentId", required = false) Long studentId,
                                                        @AuthenticationPrincipal LoginUser loginUser) {
        List<MaterialItemVO> list = materialService.getMaterialList(taskId, studentId, loginUser);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取阶段材料详情与规范指引 (API-061)")
    public Result<MaterialItemVO> getMaterialDetail(@PathVariable("id") Long id,
                                                    @AuthenticationPrincipal LoginUser loginUser) {
        MaterialItemVO detail = materialService.getMaterialDetail(id, loginUser);
        return Result.success(detail);
    }

    @PostMapping
    @Operation(summary = "提报/重提阶段材料 (API-062)")
    public Result<Long> submitMaterial(@Valid @RequestBody MaterialSubmitDTO dto,
                                       @AuthenticationPrincipal LoginUser loginUser) {
        Long id = materialService.submitMaterial(dto, loginUser);
        return Result.success(id);
    }

    @PostMapping("/{id}/audit")
    @Operation(summary = "导师查验阶段材料 (API-063)")
    public Result<Void> auditMaterial(@PathVariable("id") Long id,
                                      @Valid @RequestBody MaterialAuditDTO dto,
                                      @AuthenticationPrincipal LoginUser loginUser) {
        materialService.auditMaterial(id, dto, loginUser);
        return Result.success();
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "材料历史版本比对 (API-064)")
    public Result<List<MaterialVersionVO>> getMaterialVersions(@PathVariable("id") Long id,
                                                               @AuthenticationPrincipal LoginUser loginUser) {
        List<MaterialVersionVO> list = materialService.getMaterialVersions(id, loginUser);
        return Result.success(list);
    }
}
