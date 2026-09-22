package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.WeeklyReportReviewDTO;
import com.college.internship.dto.WeeklyReportSaveDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IWeeklyReportService;
import com.college.internship.vo.WeeklyMonitorSummaryVO;
import com.college.internship.vo.WeeklyReportDetailVO;
import com.college.internship.vo.WeeklyReportVO;
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
 * 阶段6 实习周报核心业务控制器 (API-056 ~ API-059)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internship/weekly-reports")
@RequiredArgsConstructor
@Tag(name = "实习周报管理接口", description = "提供周报草稿暂存、正式提交、教师打分批阅、退回整改及监控看板")
public class WeeklyReportController {

    private final IWeeklyReportService weeklyReportService;

    @GetMapping
    @Operation(summary = "查询周报列表 (API-056)", description = "支持行级权限隔离，学生查本人，教师查带教，院系查全院大盘")
    public Result<List<WeeklyReportVO>> listReports(@RequestParam(value = "taskId", required = false) Long taskId,
                                                    @RequestParam(value = "studentId", required = false) Long studentId,
                                                    @RequestParam(value = "status", required = false) String status,
                                                    @AuthenticationPrincipal LoginUser loginUser) {
        List<WeeklyReportVO> list = weeklyReportService.listReports(taskId, studentId, status, loginUser);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询周报详情及版本快照 (API-057)", description = "返回周报正文、得分评语及历史版本流转快照")
    public Result<WeeklyReportDetailVO> getReportDetail(@PathVariable("id") Long id,
                                                        @AuthenticationPrincipal LoginUser loginUser) {
        WeeklyReportDetailVO detail = weeklyReportService.getReportDetail(id, loginUser);
        return Result.success(detail);
    }

    @PostMapping
    @Operation(summary = "学生撰写/暂存草稿/正式提交周报 (API-058)", description = "action为DRAFT允许NULL暂存；SUBMIT执行字数阈值强校验并计算逾期")
    public Result<WeeklyReportVO> saveOrSubmitReport(@Valid @RequestBody WeeklyReportSaveDTO dto,
                                                     @AuthenticationPrincipal LoginUser loginUser) {
        WeeklyReportVO vo = weeklyReportService.saveOrSubmitReport(dto, loginUser);
        String msg = "DRAFT".equalsIgnoreCase(dto.getAction()) ? "周报草稿暂存成功" : "周报已成功正式提交";
        return Result.success(msg, vo);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "指导教师批阅或退回周报 (API-059)", description = "action为APPROVE打分并归档锁定；RETURN退回修改必填意见并存快照")
    public Result<WeeklyReportDetailVO> reviewReport(@PathVariable("id") Long id,
                                                     @Valid @RequestBody WeeklyReportReviewDTO dto,
                                                     @AuthenticationPrincipal LoginUser loginUser) {
        WeeklyReportDetailVO vo = weeklyReportService.reviewReport(id, dto, loginUser);
        String msg = "APPROVE".equalsIgnoreCase(dto.getAction()) ? "周报批阅成功" : "周报已退回学生整改";
        return Result.success(msg, vo);
    }

    @GetMapping("/monitor")
    @Operation(summary = "周报过程管理与提交率监控看板", description = "供院系/校管监控全院应交数、实交数、按期率与批阅率")
    public Result<WeeklyMonitorSummaryVO> getMonitorSummary(@RequestParam(value = "taskId", required = false) Long taskId,
                                                            @RequestParam(value = "deptId", required = false) Long deptId,
                                                            @AuthenticationPrincipal LoginUser loginUser) {
        WeeklyMonitorSummaryVO vo = weeklyReportService.getMonitorSummary(taskId, deptId, loginUser);
        return Result.success(vo);
    }
}
