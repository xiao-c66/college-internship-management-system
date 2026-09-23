package com.college.internship.service;

import com.college.internship.security.LoginUser;
import com.college.internship.vo.SysLoginLogVO;
import com.college.internship.vo.SysOperationLogVO;

import java.util.List;

/**
 * 阶段8 审计日志查询服务接口 (API-120 ~ API-121)
 */
public interface ISysLogQueryService {

    /**
     * 登录安全审计日志检索 (API-120，仅限SYS_ADMIN)
     */
    List<SysLoginLogVO> getLoginLogs(Integer pageNum, Integer pageSize, String username, Integer status, LoginUser loginUser);

    /**
     * 全盘高危业务操作日志检索 (API-121，SYS_ADMIN全校，DEPT_ADMIN仅本院系并脱敏)
     */
    List<SysOperationLogVO> getOperationLogs(Integer pageNum, Integer pageSize, String businessType, Integer status, LoginUser loginUser);
}
