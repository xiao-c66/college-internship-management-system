package com.college.internship.service;

import com.college.internship.security.LoginUser;
import com.college.internship.vo.SysActiveTokenVO;

import java.util.List;

/**
 * 阶段8 在线活跃会话与Token管理服务接口 (API-122)
 */
public interface ISysSecurityTokenService {

    /**
     * 查询在线活跃会话列表 (API-122，仅限SYS_ADMIN)
     */
    List<SysActiveTokenVO> getActiveTokens(LoginUser loginUser);

    /**
     * 超管强制踢下线指定用户Token (API-122踢下线，原子递增token_version)
     */
    void kickUserToken(Long userId, LoginUser loginUser, String clientIp);
}
