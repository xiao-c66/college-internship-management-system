package com.college.internship.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.college.internship.dto.ChangePasswordDTO;
import com.college.internship.dto.ForgotPasswordResetDTO;
import com.college.internship.dto.ForgotPasswordSendCodeDTO;
import com.college.internship.dto.UserImportExecuteDTO;
import com.college.internship.dto.UserQueryDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.ResetPasswordResultVO;
import com.college.internship.vo.UserImportPreviewVO;
import com.college.internship.vo.UserImportResultVO;
import com.college.internship.vo.UserManageVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 教师/学生批量导入与系统用户账号密码管理服务接口
 */
public interface IUserService {

    /**
     * 分页查询用户账号列表 (支持院系数据范围隔离)
     */
    IPage<UserManageVO> getUserPage(UserQueryDTO queryDTO, LoginUser loginUser, long current, long size);

    /**
     * 下载 Excel 批量导入模板 (区分学生 / 教师)
     */
    void downloadImportTemplate(String userType, HttpServletResponse response);

    /**
     * 解析上传的 Excel/CSV 文件并逐行执行合规性与排重校验
     */
    UserImportPreviewVO previewImport(MultipartFile file, String userType, LoginUser loginUser);

    /**
     * 执行批量账号创建与独立临时密码签发
     */
    UserImportResultVO executeImport(UserImportExecuteDTO executeDTO, LoginUser loginUser);

    /**
     * 管理员重置指定用户密码为独立临时密码并强制首次改密
     */
    ResetPasswordResultVO resetUserPassword(Long userId, LoginUser loginUser);

    /**
     * 用户修改密码 (支持首次登录强制改密与日常修改)
     */
    void changePassword(ChangePasswordDTO dto, LoginUser loginUser);

    /**
     * 忘记密码：向已验证的安全手机或邮箱发送有效时限验证码
     */
    void sendForgotPasswordCode(ForgotPasswordSendCodeDTO dto);

    /**
     * 忘记密码：通过安全验证码重置密码
     */
    void resetForgotPassword(ForgotPasswordResetDTO dto);

    /**
     * 启用/停用指定用户账号
     */
    void toggleUserStatus(Long userId, LoginUser loginUser);
}
