package com.college.internship.service;

import com.college.internship.dto.LoginDTO;
import com.college.internship.vo.CaptchaVO;
import com.college.internship.vo.LoginVO;
import com.college.internship.vo.UserInfoVO;

/**
 * 认证与授权核心服务接口
 */
public interface IAuthService extends IBaseService {

    /**
     * 生成验证码凭据
     */
    CaptchaVO generateCaptcha();

    /**
     * 用户登录认证
     */
    LoginVO login(LoginDTO loginDTO, String clientIp);

    /**
     * 退出登录并全端失效 Token (自增 token_version)
     */
    void logout(Long userId, String clientIp);

    /**
     * 获取当前登录用户完整资料
     */
    UserInfoVO getCurrentUserInfo(Long userId);
}
