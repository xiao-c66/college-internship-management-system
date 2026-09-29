package com.college.internship.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.college.internship.common.Result;
import com.college.internship.dto.ChangePasswordDTO;
import com.college.internship.dto.ForgotPasswordResetDTO;
import com.college.internship.dto.ForgotPasswordSendCodeDTO;
import com.college.internship.dto.UserImportExecuteDTO;
import com.college.internship.dto.UserQueryDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.service.IUserService;
import com.college.internship.vo.ResetPasswordResultVO;
import com.college.internship.vo.UserImportPreviewVO;
import com.college.internship.vo.UserImportResultVO;
import com.college.internship.vo.UserManageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * 教师/学生批量导入与系统用户账号密码管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "用户账号管理接口", description = "提供用户列表查询、Excel批量导入预览与执行、独立密码重置、首次改密与忘记密码找回")
public class UserController {

    private final IUserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'DEPT_ADMIN')")
    @Operation(summary = "分页查询用户账号列表", description = "支持多维度筛选及院系数据范围隔离")
    public Result<IPage<UserManageVO>> getUserPage(UserQueryDTO queryDTO,
                                                   @RequestParam(value = "current", defaultValue = "1") long current,
                                                   @RequestParam(value = "size", defaultValue = "10") long size,
                                                   @AuthenticationPrincipal LoginUser loginUser) {
        IPage<UserManageVO> page = userService.getUserPage(queryDTO, loginUser, current, size);
        return Result.success(page);
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'DEPT_ADMIN')")
    @Operation(summary = "下载 Excel 导入模板", description = "区分学生与教师标准表头规范")
    public void downloadTemplate(@RequestParam(value = "userType", defaultValue = "STUDENT") String userType,
                                 HttpServletResponse response) {
        userService.downloadImportTemplate(userType, response);
    }

    @PostMapping("/import-preview")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'DEPT_ADMIN')")
    @Operation(summary = "上传并预览解析导入数据", description = "解析 Excel/CSV 文件并逐行进行格式、重复性与组织架构校验")
    public Result<UserImportPreviewVO> previewImport(@RequestParam("file") MultipartFile file,
                                                     @RequestParam(value = "userType", defaultValue = "STUDENT") String userType,
                                                     @AuthenticationPrincipal LoginUser loginUser) {
        UserImportPreviewVO previewVO = userService.previewImport(file, userType, loginUser);
        return Result.success("文件解析与校验完成", previewVO);
    }

    @PostMapping("/import-execute")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'DEPT_ADMIN')")
    @Operation(summary = "确认执行批量导入", description = "为合规用户批量生成账号与高熵独立临时密码，强制首次改密")
    public Result<UserImportResultVO> executeImport(@Valid @RequestBody UserImportExecuteDTO executeDTO,
                                                    @AuthenticationPrincipal LoginUser loginUser) {
        UserImportResultVO resultVO = userService.executeImport(executeDTO, loginUser);
        return Result.success("批量导入处理完成", resultVO);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'DEPT_ADMIN')")
    @Operation(summary = "管理员重置指定用户密码", description = "生成单次独立临时密码并置为强制改密状态，踢下线历史会话")
    public Result<ResetPasswordResultVO> resetPassword(@PathVariable("id") Long id,
                                                       @AuthenticationPrincipal LoginUser loginUser) {
        ResetPasswordResultVO resultVO = userService.resetUserPassword(id, loginUser);
        return Result.success("密码已成功重置为临时密码，用户下次登录时必须修改密码", resultVO);
    }

    @PostMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'DEPT_ADMIN')")
    @Operation(summary = "切换用户启用/停用状态", description = "停用账号将即时踢下线其所有在线令牌")
    public Result<Void> toggleStatus(@PathVariable("id") Long id,
                                     @AuthenticationPrincipal LoginUser loginUser) {
        userService.toggleUserStatus(id, loginUser);
        return Result.success("用户状态已切换", null);
    }

    @PostMapping("/change-password")
    @Operation(summary = "用户修改密码", description = "支持首次登录强制改密与日常密码更新，校验复杂度")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto,
                                       @AuthenticationPrincipal LoginUser loginUser) {
        userService.changePassword(dto, loginUser);
        return Result.success("密码修改成功，历史设备登录凭证已失效，请使用新密码重新登录", null);
    }

    @PostMapping("/forgot-password/send-code")
    @Operation(summary = "忘记密码：发送安全验证码", description = "向已绑定的安全手机或邮箱发送时效验证码，严禁仅凭学号找回")
    public Result<Void> sendForgotPasswordCode(@Valid @RequestBody ForgotPasswordSendCodeDTO dto) {
        userService.sendForgotPasswordCode(dto);
        return Result.success("安全验证码已发送，请查收（5分钟内有效）", null);
    }

    @PostMapping("/forgot-password/verify-and-reset")
    @Operation(summary = "忘记密码：凭验证码重置密码", description = "通过核验验证码完成新密码设置")
    public Result<Void> resetForgotPassword(@Valid @RequestBody ForgotPasswordResetDTO dto) {
        userService.resetForgotPassword(dto);
        return Result.success("密码重置成功，请使用新密码登录", null);
    }
}
