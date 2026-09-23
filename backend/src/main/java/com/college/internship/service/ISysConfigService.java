package com.college.internship.service;

import com.college.internship.dto.ConfigUpdateDTO;
import com.college.internship.security.LoginUser;
import com.college.internship.vo.ConfigVO;

import java.util.List;

/**
 * 阶段8 系统全局运维参数配置服务接口 (API-103 ~ API-105)
 */
public interface ISysConfigService extends IBaseService {

    /**
     * API-103: 查询系统全局运维参数列表 (仅限阶段8自身4个参数)
     */
    List<ConfigVO> getConfigList(LoginUser loginUser);

    /**
     * API-104: 修改系统全局运维参数 (受限白名单，更新快照审计，提交后刷新缓存)
     */
    void updateConfig(String key, ConfigUpdateDTO dto, String clientIp, LoginUser loginUser);

    /**
     * API-105: 一键出厂重置指定运维参数至 application.yml 默认值
     */
    void resetConfig(String key, String clientIp, LoginUser loginUser);

    /**
     * 获取指定配置项键值 (优先读取 Caffeine 缓存，Miss 则读取 DB，最后回退 application.yml 默认值)
     */
    String getConfigValue(String key);

    /**
     * 获取整数类型配置值
     */
    int getIntValue(String key, int defaultValue);

    /**
     * 获取长整型配置值
     */
    long getLongValue(String key, long defaultValue);
}
