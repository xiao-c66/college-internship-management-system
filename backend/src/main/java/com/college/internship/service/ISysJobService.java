package com.college.internship.service;

import com.college.internship.security.LoginUser;
import com.college.internship.vo.SysJobVO;

import java.util.List;
import java.util.Map;

/**
 * 阶段8受限制定时任务业务服务接口 (API-106 ~ API-108)
 */
public interface ISysJobService {

    /**
     * 查询受限白名单定时任务列表 (API-106)
     */
    List<SysJobVO> getJobList(LoginUser loginUser);

    /**
     * 手动单次触发定时调度任务 (API-107)
     */
    Map<String, Object> triggerJob(Long id, LoginUser loginUser);

    /**
     * 启动/暂停定时调度任务 (API-108)
     */
    SysJobVO toggleJob(Long id, LoginUser loginUser);

    /**
     * 按任务编码直接执行调度 (用于后台调度或定向联动)
     */
    Map<String, Object> executeJobDirect(String jobCode, LoginUser loginUser, boolean isManual);

    /**
     * 初始化 3 个白名单基准任务入库
     */
    void initDefaultJobs();

    /**
     * 清理幂等窗口缓存 (主要用于测试环境精确隔离)
     */
    void clearIdempotencyCache();
}
