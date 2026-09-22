package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.TaskCreateDTO;
import com.college.internship.dto.TaskUpdateDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IInternshipTaskService;
import com.college.internship.vo.TaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 实习批次任务核心控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "实习任务管理接口", description = "提供实习批次任务创建、五项权重配置、专业班级圈定与任务发布")
public class TaskController {

    private final IInternshipTaskService taskService;

    @PostMapping
    @Operation(summary = "创建实习任务", description = "院系负责人或管理员创建任务，严格校验五项评价成绩权重总和等于100%")
    public Result<TaskVO> createTask(@Valid @RequestBody TaskCreateDTO dto,
                                     @AuthenticationPrincipal LoginUser loginUser) {
        TaskVO vo = taskService.createTask(dto, loginUser);
        return Result.success("实习任务创建成功", vo);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑修改实习任务", description = "修改任务基本信息、起止时间与权重规则")
    public Result<TaskVO> updateTask(@PathVariable("id") Long id,
                                     @Valid @RequestBody TaskUpdateDTO dto,
                                     @AuthenticationPrincipal LoginUser loginUser) {
        TaskVO vo = taskService.updateTask(id, dto, loginUser);
        return Result.success("实习任务更新成功", vo);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "正式发布实习任务", description = "发布任务并自动根据班级专业生成学生参与范围名单")
    public Result<Void> publishTask(@PathVariable("id") Long id,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        taskService.publishTask(id, loginUser);
        return Result.success("实习任务已正式发布", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除未发布任务", description = "仅允许删除未产生业务数据的草稿任务 (TASK-011)")
    public Result<Void> deleteTask(@PathVariable("id") Long id,
                                   @AuthenticationPrincipal LoginUser loginUser) {
        taskService.deleteTask(id, loginUser);
        return Result.success("实习任务已删除", null);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取任务详情", description = "查询任务起止时间、材料规范、成绩权重与关联班级专业")
    public Result<TaskVO> getTaskDetail(@PathVariable("id") Long id,
                                        @AuthenticationPrincipal LoginUser loginUser) {
        TaskVO vo = taskService.getTaskDetail(id, loginUser);
        return Result.success(vo);
    }

    @GetMapping
    @Operation(summary = "查询实习任务列表", description = "分角色查询任务列表，学生端仅展示生效任务")
    public Result<List<TaskVO>> listTasks(@RequestParam(value = "deptId", required = false) Long deptId,
                                          @RequestParam(value = "status", required = false) String status,
                                          @AuthenticationPrincipal LoginUser loginUser) {
        List<TaskVO> list = taskService.listTasks(deptId, status, loginUser);
        return Result.success(list);
    }

    @GetMapping("/{id}/students")
    @Operation(summary = "查询任务圈定学生与导师分配名单", description = "支持按班级、指定教师或关键词筛选 (ASSIGN-001 ~ ASSIGN-003)")
    public Result<List<com.college.internship.vo.TaskStudentVO>> getTaskStudents(@PathVariable("id") Long id,
                                                                                 @RequestParam(value = "classId", required = false) Long classId,
                                                                                 @RequestParam(value = "teacherId", required = false) Long teacherId,
                                                                                 @RequestParam(value = "keyword", required = false) String keyword,
                                                                                 @AuthenticationPrincipal LoginUser loginUser) {
        List<com.college.internship.vo.TaskStudentVO> list = taskService.getTaskStudents(id, classId, teacherId, keyword, loginUser);
        return Result.success(list);
    }

    @GetMapping("/{id}/teachers")
    @Operation(summary = "获取任务所在学院可用指导教师", description = "返回本二级学院所有激活状态的指导教师列表")
    public Result<List<com.college.internship.vo.TeacherSimpleVO>> getAvailableTeachers(@PathVariable("id") Long id,
                                                                                        @AuthenticationPrincipal LoginUser loginUser) {
        List<com.college.internship.vo.TeacherSimpleVO> list = taskService.getAvailableTeachers(id, loginUser);
        return Result.success(list);
    }

    @PostMapping("/{id}/assign-teacher")
    @Operation(summary = "指派或批量调整指导教师", description = "为任务中的一个或多个学生批量分配校内指导教师 (ASSIGN-001/003)")
    public Result<Void> assignTeacher(@PathVariable("id") Long id,
                                      @Valid @RequestBody com.college.internship.dto.TeacherAssignDTO dto,
                                      @AuthenticationPrincipal LoginUser loginUser) {
        taskService.assignTeacher(id, dto, loginUser);
        return Result.success("指导教师分配成功", null);
    }
}
