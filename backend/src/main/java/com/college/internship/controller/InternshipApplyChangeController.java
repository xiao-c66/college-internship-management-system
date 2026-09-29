package com.college.internship.controller;

import com.college.internship.common.Result;
import com.college.internship.dto.ApplyChangeAuditDTO;
import com.college.internship.dto.ApplyChangeDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IInternshipApplyChangeService;
import com.college.internship.vo.ApplyChangeVO;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 实习重大信息变更申请与双级审批核心控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/applies")
@RequiredArgsConstructor
@Tag(name = "实习重大变更管理接口", description = "提供学生在 APPLY-009 锁定后发起重大信息变更、教师初审与院系终审全链路闭环")
public class InternshipApplyChangeController {

    private final IInternshipApplyChangeService changeService;

    @PostMapping("/changes")
    @Operation(summary = "学生提交实习重大信息变更申请", description = "仅当原申请处于 APPROVED 锁定状态时方可发起，同一申请禁止并发重复提交")
    @PreAuthorize("hasAnyAuthority('ROLE_STUDENT', 'ROLE_ADMIN')")
    public Result<ApplyChangeVO> submitChange(@Valid @RequestBody ApplyChangeDTO dto,
                                              @AuthenticationPrincipal LoginUser loginUser) {
        ApplyChangeVO vo = changeService.submitChange(dto, loginUser);
        return Result.success("实习重大变更申请已成功提交，进入指导教师初审阶段", vo);
    }

    @PostMapping("/changes/{id}/teacher-audit")
    @Operation(summary = "指导教师初审实习变更申请", description = "初审通过进入待院系终审阶段，初审驳回原数据保持原状")
    @PreAuthorize("hasAnyAuthority('ROLE_TEACHER', 'ROLE_ADMIN')")
    public Result<ApplyChangeVO> teacherInitialAudit(@PathVariable("id") Long id,
                                                     @Valid @RequestBody ApplyChangeAuditDTO dto,
                                                     @AuthenticationPrincipal LoginUser loginUser) {
        ApplyChangeVO vo = changeService.teacherInitialAudit(id, dto, loginUser);
        String msg = "APPROVE".equals(dto.getAuditAction()) ? "教师初审已通过，已流转至院系终审" : "教师初审已驳回变更申请";
        return Result.success(msg, vo);
    }

    @PostMapping("/changes/{id}/dept-audit")
    @Operation(summary = "院系管理员终审实习变更申请", description = "终审通过在同一事务内原子更新主申请表数据并记录审计，终审驳回原数据不变")
    @PreAuthorize("hasAnyAuthority('ROLE_DEPT_ADMIN', 'ROLE_ADMIN')")
    public Result<ApplyChangeVO> deptFinalAudit(@PathVariable("id") Long id,
                                                @Valid @RequestBody ApplyChangeAuditDTO dto,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        ApplyChangeVO vo = changeService.deptFinalAudit(id, dto, loginUser);
        String msg = "APPROVE".equals(dto.getAuditAction()) ? "院系终审已通过，实习变更已正式生效并更新主数据" : "院系终审已驳回变更申请";
        return Result.success(msg, vo);
    }

    @GetMapping("/changes/{id}")
    @Operation(summary = "查询实习变更申请详情与审批轨迹", description = "返回原快照、新信息、变更事由及不可篡改的审批轨迹")
    public Result<ApplyChangeVO> getChangeDetail(@PathVariable("id") Long id,
                                                 @AuthenticationPrincipal LoginUser loginUser) {
        ApplyChangeVO vo = changeService.getChangeDetail(id, loginUser);
        return Result.success("获取变更详情成功", vo);
    }

    @GetMapping("/changes")
    @Operation(summary = "多条件检索实习变更申请列表", description = "支持按原申请ID、任务ID、状态筛选，遵循行级数据隔离权限")
    public Result<List<ApplyChangeVO>> listChanges(@RequestParam(value = "applyId", required = false) Long applyId,
                                                   @RequestParam(value = "taskId", required = false) Long taskId,
                                                   @RequestParam(value = "status", required = false) String status,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        List<ApplyChangeVO> list = changeService.listChanges(applyId, taskId, status, loginUser);
        return Result.success("查询实习变更列表成功", list);
    }

    @GetMapping("/{applyId}/active-change")
    @Operation(summary = "查询指定实习申报的当前重大变更状态与进展", description = "供学生端在申请主页实时感知并展示变更审批进度条")
    public Result<ApplyChangeVO> getActiveChange(@PathVariable("applyId") Long applyId,
                                                 @AuthenticationPrincipal LoginUser loginUser) {
        ApplyChangeVO vo = changeService.getActiveChangeByApplyId(applyId, loginUser);
        return Result.success("查询当前变更状态成功", vo);
    }
}
