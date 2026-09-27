package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.CommitmentSignDTO;
import com.college.internship.dto.ExamSubmitDTO;
import com.college.internship.dto.SafetyMaterialDTO;
import com.college.internship.dto.SafetyQuestionDTO;
import com.college.internship.entity.SafetyMaterialItem;
import com.college.internship.entity.SafetyTestQuestion;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISafetyEducationService;
import com.college.internship.vo.ExamResultVO;
import com.college.internship.vo.QuestionVO;
import com.college.internship.vo.RemindResultVO;
import com.college.internship.vo.SafetyStatusVO;
import com.college.internship.vo.StudentSafetyProgressVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 安全教育准入与考试核心控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/safety")
@RequiredArgsConstructor
@Tag(name = "安全教育准入接口", description = "提供资料学习、安全测试题库配置、在线答题评分、安全承诺书签署与状态监控")
public class SafetyController {

    private final ISafetyEducationService safetyService;

    @PostMapping("/materials")
    @Operation(summary = "配置安全教育资料", description = "全校通用资料限管理员(SAFE-001)，任务专属资料限院系负责人(SAFE-002)")
    public Result<SafetyMaterialItem> saveMaterial(@Valid @RequestBody SafetyMaterialDTO dto,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        SafetyMaterialItem item = safetyService.saveMaterial(dto, loginUser);
        return Result.success("安全资料配置成功", item);
    }

    @DeleteMapping("/materials/{id}")
    @Operation(summary = "删除安全资料", description = "移除指定安全教育资料清单项")
    public Result<Void> deleteMaterial(@PathVariable("id") Long id,
                                       @AuthenticationPrincipal LoginUser loginUser) {
        safetyService.deleteMaterial(id, loginUser);
        return Result.success("安全资料已删除", null);
    }

    @GetMapping("/materials")
    @Operation(summary = "获取安全教育资料列表", description = "获取全校通用或关联任务的安全图文规范列表")
    public Result<List<SafetyMaterialItem>> listMaterials(@RequestParam(value = "taskId", required = false) Long taskId,
                                                          @AuthenticationPrincipal LoginUser loginUser) {
        List<SafetyMaterialItem> list = safetyService.listMaterials(taskId, loginUser);
        return Result.success(list);
    }

    @PostMapping("/materials/{id}/read")
    @Operation(summary = "学生标记安全资料已读 (驱动五阶段状态流转)", description = "驱动 NOT_STARTED -> STUDYING -> PENDING_TEST")
    public Result<Void> markMaterialRead(@PathVariable("id") Long id,
                                         @RequestParam("taskId") Long taskId,
                                         @AuthenticationPrincipal LoginUser loginUser) {
        safetyService.markMaterialRead(taskId, id, loginUser);
        return Result.success("资料已标记完成阅读", null);
    }

    @PostMapping("/questions")
    @Operation(summary = "录入安全测试试题", description = "配置客观题单选、多选与判断题 (SAFE-003)")
    public Result<Void> saveQuestion(@Valid @RequestBody SafetyQuestionDTO dto,
                                     @AuthenticationPrincipal LoginUser loginUser) {
        safetyService.saveQuestion(dto, loginUser);
        return Result.success("安全试题已添加", null);
    }

    @DeleteMapping("/questions/{id}")
    @Operation(summary = "删除安全试题", description = "删除题库中指定的试题")
    public Result<Void> deleteQuestion(@PathVariable("id") Long id,
                                       @AuthenticationPrincipal LoginUser loginUser) {
        safetyService.deleteQuestion(id, loginUser);
        return Result.success("试题已删除", null);
    }

    @GetMapping("/questions")
    @Operation(summary = "查询安全测试题库列表 (SAFE-003)", description = "供管理人员维护客观题试题")
    public Result<List<SafetyTestQuestion>> listQuestions(@RequestParam(value = "taskId", required = false) Long taskId,
                                                          @AuthenticationPrincipal LoginUser loginUser) {
        List<SafetyTestQuestion> list = safetyService.listQuestions(taskId, loginUser);
        return Result.success(list);
    }

    @GetMapping("/exam/paper")
    @Operation(summary = "获取在线考试试卷", description = "学生端获取脱敏客观题试卷题目 (隐藏正确答案与解析)")
    public Result<List<QuestionVO>> getExamPaper(@RequestParam("taskId") Long taskId,
                                                 @AuthenticationPrincipal LoginUser loginUser) {
        List<QuestionVO> paper = safetyService.getExamPaper(taskId, loginUser);
        return Result.success(paper);
    }

    @PostMapping("/exam/submit")
    @Operation(summary = "学生在线交卷评分", description = "提交答卷并由系统即时自动评分 (SAFE-005)")
    public Result<ExamResultVO> submitExam(@Valid @RequestBody ExamSubmitDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        ExamResultVO result = safetyService.submitExam(dto, loginUser);
        return Result.success("交卷完成", result);
    }

    @PostMapping("/commitment/sign")
    @Operation(summary = "签署安全责任承诺书", description = "学生在线阅读并确认签署承诺书，可选上传商业险保单 (SAFE-006/007)")
    public Result<Void> signCommitment(@Valid @RequestBody CommitmentSignDTO dto,
                                       HttpServletRequest request,
                                       @AuthenticationPrincipal LoginUser loginUser) {
        String clientIp = request != null ? request.getRemoteAddr() : "127.0.0.1";
        safetyService.signCommitment(dto, clientIp, loginUser);
        return Result.success("安全责任承诺书已成功签署", null);
    }

    @GetMapping("/status")
    @Operation(summary = "查询安全教育五阶段状态 (API-125)", description = "返回完整五阶段状态: NOT_STARTED, STUDYING, PENDING_TEST, PASSED, COMPLETED")
    public Result<SafetyStatusVO> getSafetyStatus(@RequestParam("taskId") Long taskId,
                                                  @RequestParam(value = "studentId", required = false) Long studentId,
                                                  @AuthenticationPrincipal LoginUser loginUser) {
        SafetyStatusVO status = safetyService.getSafetyStatus(taskId, studentId, loginUser);
        return Result.success(status);
    }

    @GetMapping("/statistics")
    @Operation(summary = "院系安全教育完成率监控 (SAFE-009)", description = "院系负责人监控本院学生安全达标进度")
    public Result<Map<String, Object>> getDeptSafetyStatistics(@RequestParam(value = "taskId", required = false) Long taskId,
                                                               @RequestParam(value = "deptId", required = false) Long deptId,
                                                               @AuthenticationPrincipal LoginUser loginUser) {
        Map<String, Object> stats = safetyService.getDeptSafetyStatistics(taskId, deptId, loginUser);
        return Result.success(stats);
    }

    @PostMapping("/remind")
    @Operation(summary = "一键催办未达标学生 (API-123 & SAFE-008)", description = "针对未达标学生记录催办指令至系统安全审计日志")
    public Result<RemindResultVO> remindStudents(@RequestParam("taskId") Long taskId,
                                                 @RequestParam(value = "studentId", required = false) Long studentId,
                                                 @AuthenticationPrincipal LoginUser loginUser) {
        RemindResultVO result = safetyService.remindStudents(taskId, studentId, loginUser);
        return Result.success("催办指令已成功写入系统安全审计日志", result);
    }

    @GetMapping("/students")
    @Operation(summary = "指导教师与负责人查询学生安全教育进度 (SAFE-008 & API-125)", description = "支持全部、未完成及特定五阶段状态筛选与模糊检索")
    public Result<List<StudentSafetyProgressVO>> getStudentsSafetyProgress(@RequestParam(value = "taskId", required = false) Long taskId,
                                                                          @RequestParam(value = "status", required = false) String status,
                                                                          @RequestParam(value = "keyword", required = false) String keyword,
                                                                          @AuthenticationPrincipal LoginUser loginUser) {
        List<StudentSafetyProgressVO> list = safetyService.getStudentsSafetyProgress(taskId, status, keyword, loginUser);
        return Result.success(list);
    }
}
