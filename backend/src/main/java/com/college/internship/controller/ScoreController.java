package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.ScoreAppealDTO;
import com.college.internship.dto.ScoreArbitrateDTO;
import com.college.internship.dto.ScoreSubmitDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IScoreService;
import com.college.internship.vo.ScoreSummaryVO;
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
 * 实习成绩五维综合评定与异议申诉控制器 (API-091 ~ API-097)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/score")
@RequiredArgsConstructor
@Tag(name = "实习成绩评定与申诉接口", description = "提供五维成绩录入汇算、规则快照固化、公示发布、成绩申诉与调分仲裁")
public class ScoreController {

    private final IScoreService scoreService;

    @PostMapping("/summaries")
    @Operation(summary = "录入/汇算五维成绩 (API-091)")
    public Result<Long> submitScore(@Valid @RequestBody ScoreSubmitDTO dto,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        Long id = scoreService.submitScore(dto, loginUser);
        return Result.success(id);
    }

    @GetMapping("/summaries")
    @Operation(summary = "成绩列表检索 (API-092)")
    public Result<List<ScoreSummaryVO>> getScoreList(@RequestParam(value = "taskId", required = false) Long taskId,
                                                     @RequestParam(value = "deptId", required = false) Long deptId,
                                                     @RequestParam(value = "majorId", required = false) Long majorId,
                                                     @RequestParam(value = "classId", required = false) Long classId,
                                                     @RequestParam(value = "scoreLevel", required = false) String scoreLevel,
                                                     @RequestParam(value = "status", required = false) String status,
                                                     @AuthenticationPrincipal LoginUser loginUser) {
        List<ScoreSummaryVO> list = scoreService.getScoreList(taskId, deptId, majorId, classId, scoreLevel, status, loginUser);
        return Result.success(list);
    }

    @GetMapping("/summaries/{id}")
    @Operation(summary = "获取学生五维成绩单 (API-093)")
    public Result<ScoreSummaryVO> getScoreDetail(@PathVariable("id") Long id,
                                                 @AuthenticationPrincipal LoginUser loginUser) {
        ScoreSummaryVO detail = scoreService.getScoreDetail(id, loginUser);
        return Result.success(detail);
    }

    @GetMapping("/my")
    @Operation(summary = "学生获取本人五维成绩")
    public Result<ScoreSummaryVO> getMyScore(@RequestParam("taskId") Long taskId,
                                             @AuthenticationPrincipal LoginUser loginUser) {
        ScoreSummaryVO vo = scoreService.getStudentScore(taskId, loginUser.getUserId(), loginUser);
        return Result.success(vo);
    }

    @PostMapping("/summaries/{id}/audit")
    @Operation(summary = "院系复核实习成绩 (API-094)")
    public Result<Void> auditScore(@PathVariable("id") Long id,
                                   @AuthenticationPrincipal LoginUser loginUser) {
        scoreService.auditScore(id, loginUser);
        return Result.success();
    }

    @PostMapping("/tasks/{taskId}/publicity")
    @Operation(summary = "批量发布成绩公示 (API-095)")
    public Result<Void> publishScores(@PathVariable("taskId") Long taskId,
                                      @AuthenticationPrincipal LoginUser loginUser) {
        scoreService.publishScores(taskId, loginUser);
        return Result.success();
    }

    @PostMapping("/appeals")
    @Operation(summary = "公示期提交成绩申诉 (API-096)")
    public Result<Long> submitAppeal(@Valid @RequestBody ScoreAppealDTO dto,
                                     @AuthenticationPrincipal LoginUser loginUser) {
        Long id = scoreService.submitAppeal(dto, loginUser);
        return Result.success(id);
    }

    @PostMapping("/appeals/{id}/arbitrate")
    @Operation(summary = "成绩申诉裁决与调分 (API-097)")
    public Result<Void> arbitrateAppeal(@PathVariable("id") Long id,
                                        @Valid @RequestBody ScoreArbitrateDTO dto,
                                        @AuthenticationPrincipal LoginUser loginUser) {
        scoreService.arbitrateAppeal(id, dto, loginUser);
        return Result.success();
    }
}
