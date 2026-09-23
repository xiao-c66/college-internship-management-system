package com.college.internship.service;

import com.college.internship.security.LoginUser;
import com.college.internship.vo.CacheMonitorVO;
import com.college.internship.vo.ServerMonitorVO;
import com.college.internship.vo.SlowSqlMonitorVO;

/**
 * 阶段8 性能与运行时监控服务接口 (API-116 ~ API-119)
 */
public interface IPerformanceMonitorService {

    /**
     * 获取服务器与JVM运行指标 (API-116)
     */
    ServerMonitorVO getServerMetrics();

    /**
     * 获取有界Caffeine缓存状态 (API-117)
     */
    CacheMonitorVO getCacheMetrics();

    /**
     * 清理系统缓存 (API-118)
     */
    void clearCache(String cacheName, LoginUser loginUser, String clientIp);

    /**
     * 获取慢接口与慢查询度量看板 (API-119)
     */
    SlowSqlMonitorVO getSlowSqlMetrics();

    /**
     * 采集并记录请求样本到环形缓冲区 (内存有界)
     */
    void recordRequest(String uri, String method, long costMs, int statusCode,
                       String operatorName, String clientIp, String params);

    /**
     * 手动清空环形缓冲区采样数据 (用于单元测试隔离)
     */
    void clearSamplesForTesting();

    /**
     * 获取当前慢调用判定阈值 (毫秒)
     */
    long getSlowThresholdMs();
}
