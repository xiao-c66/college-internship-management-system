package com.college.internship.service;

import com.college.internship.security.LoginUser;
import com.college.internship.vo.DashboardSummaryVO;

/**
 * 角色工作台动态指标统计服务接口
 */
public interface IDashboardService extends IBaseService {

    /**
     * 根据当前已认证登录用户的身份与角色，查询并装配工作台指标摘要
     *
     * @param loginUser 当前已认证登录用户上下文
     * @return 工作台统计摘要 VO
     */
    DashboardSummaryVO getSummary(LoginUser loginUser);
}
