package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.ArchiveUnlockDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IArchiveService;
import com.college.internship.vo.ArchivePrecheckVO;
import com.college.internship.vo.ArchiveVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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
 * 实习电子卷宗归档、锁定与特批解锁控制器 (API-098 ~ API-102)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/archives")
@RequiredArgsConstructor
@Tag(name = "电子卷宗归档接口", description = "提供归档前置硬条件诊断核验、归档锁定、总库检索、ZIP导出与特批解锁")
public class ArchiveController {

    private final IArchiveService archiveService;

    @GetMapping("/precheck")
    @Operation(summary = "归档9项前置硬条件诊断核验 (API-098)")
    public Result<ArchivePrecheckVO> precheckArchive(@RequestParam("taskId") Long taskId,
                                                    @RequestParam("studentId") Long studentId,
                                                    @AuthenticationPrincipal LoginUser loginUser) {
        ArchivePrecheckVO precheck = archiveService.precheckArchive(taskId, studentId, loginUser);
        return Result.success(precheck);
    }

    @PostMapping("/freeze")
    @Operation(summary = "执行归档锁定 (API-099)")
    public Result<Long> freezeArchive(@RequestParam("taskId") Long taskId,
                                      @RequestParam("studentId") Long studentId,
                                      @AuthenticationPrincipal LoginUser loginUser) {
        Long id = archiveService.freezeArchive(taskId, studentId, loginUser);
        return Result.success(id);
    }

    @GetMapping
    @Operation(summary = "归档卷宗跨学年/院系检索 (API-100)")
    public Result<List<ArchiveVO>> getArchiveList(@RequestParam(value = "taskId", required = false) Long taskId,
                                                  @RequestParam(value = "deptId", required = false) Long deptId,
                                                  @RequestParam(value = "academicYear", required = false) String academicYear,
                                                  @RequestParam(value = "status", required = false) String status,
                                                  @AuthenticationPrincipal LoginUser loginUser) {
        List<ArchiveVO> list = archiveService.getArchiveList(taskId, deptId, academicYear, status, loginUser);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询归档卷宗详情")
    public Result<ArchiveVO> getArchiveDetail(@PathVariable("id") Long id,
                                              @AuthenticationPrincipal LoginUser loginUser) {
        ArchiveVO detail = archiveService.getArchiveDetail(id, loginUser);
        return Result.success(detail);
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "导出归档电子卷宗 ZIP (API-101)")
    public void exportArchiveBundle(@PathVariable("id") Long id,
                                    HttpServletResponse response,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        archiveService.exportArchiveBundle(id, response, loginUser);
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "超管特批解锁 (API-102)")
    public Result<Void> unlockArchive(@PathVariable("id") Long id,
                                      @Valid @RequestBody ArchiveUnlockDTO dto,
                                      @AuthenticationPrincipal LoginUser loginUser) {
        archiveService.unlockArchive(id, dto, loginUser);
        return Result.success();
    }
}
