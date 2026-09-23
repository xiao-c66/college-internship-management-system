package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.NoticeCreateDTO;
import com.college.internship.dto.NoticeUpdateDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysNoticeService;
import com.college.internship.vo.SysNoticeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 阶段8教学通知公告控制器 (API-112 ~ API-115)
 * 严格按照 implementation_plan.md 原始契约暴露固定端点，不添加任何别名路径
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/notices")
@RequiredArgsConstructor
@Tag(name = "通知公告管理接口", description = "提供通知公告分级查阅、已读幂等标记、人工发布防重与撤回修改")
public class SystemNoticeController {

    private final ISysNoticeService sysNoticeService;

    @GetMapping
    @Operation(summary = "查阅通知公告列表 (API-112)")
    public Result<List<SysNoticeVO>> getNoticeList(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "noticeType", required = false) String noticeType,
            @AuthenticationPrincipal LoginUser loginUser) {
        List<SysNoticeVO> list = sysNoticeService.getNoticeList(pageNum, pageSize, status, noticeType, loginUser);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取通知详情并记录已读 (API-113)")
    public Result<SysNoticeVO> getNoticeDetail(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal LoginUser loginUser) {
        SysNoticeVO vo = sysNoticeService.getNoticeDetail(id, loginUser);
        return Result.success(vo);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'DEPT_ADMIN')")
    @Operation(summary = "人工发布通知公告 (API-114)")
    public Result<SysNoticeVO> createNotice(
            @Valid @RequestBody NoticeCreateDTO dto,
            @AuthenticationPrincipal LoginUser loginUser) {
        SysNoticeVO vo = sysNoticeService.createNotice(dto, loginUser);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'DEPT_ADMIN')")
    @Operation(summary = "修改/撤回通知公告 (API-115)")
    public Result<SysNoticeVO> updateNotice(
            @PathVariable("id") Long id,
            @RequestBody NoticeUpdateDTO dto,
            @AuthenticationPrincipal LoginUser loginUser) {
        SysNoticeVO vo = sysNoticeService.updateNotice(id, dto, loginUser);
        return Result.success(vo);
    }
}
