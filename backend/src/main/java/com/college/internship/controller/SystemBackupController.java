package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysBackupService;
import com.college.internship.vo.SysBackupRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阶段8受控备份管理控制器 (API-109 ~ API-111)
 * 严格按照 implementation_plan.md 原始契约暴露固定端点，不添加任何别名路径
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/backup")
@RequiredArgsConstructor
@Tag(name = "受控备份管理接口", description = "提供数据库受控热备触发、元数据审计检索与防穿越安全下载")
public class SystemBackupController {

    private final ISysBackupService sysBackupService;

    @GetMapping("/records")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "查询数据库备份历史记录 (API-109)")
    public Result<List<SysBackupRecordVO>> getBackupRecords(@AuthenticationPrincipal LoginUser loginUser) {
        List<SysBackupRecordVO> list = sysBackupService.getBackupRecords(loginUser);
        return Result.success(list);
    }

    @PostMapping("/execute")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "手动触发数据库全量热备份 (API-110)")
    public Result<SysBackupRecordVO> executeBackup(@AuthenticationPrincipal LoginUser loginUser) {
        SysBackupRecordVO vo = sysBackupService.executeBackup(loginUser);
        return Result.success(vo);
    }

    @GetMapping("/download/{id}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @Operation(summary = "统一备份安全下载端点 (API-111)")
    public ResponseEntity<Resource> downloadBackup(@PathVariable("id") Long id,
                                                  @AuthenticationPrincipal LoginUser loginUser) {
        return sysBackupService.downloadBackup(id, loginUser);
    }
}
