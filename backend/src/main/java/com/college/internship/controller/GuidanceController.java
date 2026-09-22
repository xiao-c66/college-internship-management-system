package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.GuidanceCreateDTO;
import com.college.internship.dto.GuidanceFeedbackDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IGuidanceRecordService;
import com.college.internship.vo.GuidanceRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 阶段6 过程指导走访台账与学生在岗反馈核心控制器 (API-065 ~ API-067)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internship/guidances")
@RequiredArgsConstructor
@Tag(name = "过程指导与走访台账接口", description = "提供指导教师登记走访记录、EasyExcel导出台账、学生CAS在岗确认反馈")
public class GuidanceController {

    private final IGuidanceRecordService guidanceService;

    @PostMapping
    @Operation(summary = "登记过程指导/实地走访记录 (API-065)", description = "指导教师专属，校验带教学生所属关系与交流要点字数")
    public Result<GuidanceRecordVO> createGuidance(@Valid @RequestBody GuidanceCreateDTO dto,
                                                  @AuthenticationPrincipal LoginUser loginUser) {
        GuidanceRecordVO vo = guidanceService.createGuidance(dto, loginUser);
        return Result.success("过程指导台账登记成功", vo);
    }

    @GetMapping
    @Operation(summary = "查询指导记录列表或导出 Excel (API-066)", description = "默认返回列表；带 export=excel 参数时直接以 EasyExcel 导出字节流 (学生调用报403)")
    public Object listOrExportGuidances(@RequestParam(value = "taskId", required = false) Long taskId,
                                        @RequestParam(value = "studentId", required = false) Long studentId,
                                        @RequestParam(value = "guidanceType", required = false) String guidanceType,
                                        @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                        @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                        @RequestParam(value = "export", required = false) String export,
                                        HttpServletResponse response,
                                        @AuthenticationPrincipal LoginUser loginUser) {
        if ("excel".equalsIgnoreCase(export)) {
            guidanceService.exportGuidanceExcel(taskId, studentId, guidanceType, startDate, endDate, response, loginUser);
            return null;
        }
        List<GuidanceRecordVO> list = guidanceService.listGuidances(taskId, studentId, guidanceType, startDate, endDate, loginUser);
        return Result.success(list);
    }

    @PutMapping("/{id}/feedback")
    @Operation(summary = "学生确认指导记录并提交在岗反馈 (API-067)", description = "仅限受访学生本人，基于CAS事务条件更新防重，非本人报403，重复报400")
    public Result<GuidanceRecordVO> submitFeedback(@PathVariable("id") Long id,
                                                   @Valid @RequestBody GuidanceFeedbackDTO dto,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        GuidanceRecordVO vo = guidanceService.submitFeedback(id, dto, loginUser);
        return Result.success("在岗反馈确认成功并已锁定", vo);
    }
}
