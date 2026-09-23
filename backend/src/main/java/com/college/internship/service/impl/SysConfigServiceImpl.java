package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.SystemProperties;
import com.college.internship.dto.ConfigUpdateDTO;
import com.college.internship.entity.SysConfig;
import com.college.internship.mapper.SysConfigMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysConfigService;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.vo.ConfigVO;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 阶段8 系统全局运维参数配置服务实现类
 * 严格落实：白名单强约束、阶段6/7代码只读保护、强类型配置、Caffeine有界缓存及事务提交后逐出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements ISysConfigService {

    public static final String KEY_SLOW_SQL_THRESHOLD = "system.slow-sql-threshold-ms";
    public static final String KEY_BACKUP_RETENTION_DAYS = "system.backup.retention-days";
    public static final String KEY_BACKUP_RATE_LIMIT = "system.backup.rate-limit-seconds";
    public static final String KEY_JOB_TIMEOUT = "system.job.execution-timeout-seconds";

    // 合法业务边界常数规约 (对齐实施方案第5版 4.2 节业务规范定义)
    public static final long SLOW_SQL_MIN_MS = 100L;
    public static final long SLOW_SQL_MAX_MS = 5000L;
    public static final long BACKUP_RETENTION_MIN_DAYS = 7L;
    public static final long BACKUP_RETENTION_MAX_DAYS = 365L;
    public static final long BACKUP_RATE_LIMIT_MIN_SEC = 5L;
    public static final long BACKUP_RATE_LIMIT_MAX_SEC = 120L;
    public static final long JOB_TIMEOUT_MIN_SEC = 10L;
    public static final long JOB_TIMEOUT_MAX_SEC = 300L;

    private final SysConfigMapper sysConfigMapper;
    private final ISysOperationLogService operationLogService;
    private final SystemProperties systemProperties;
    private final Cache<String, String> configCaffeineCache;

    public static class WhitelistMeta {
        private final String key;
        private final String name;
        private final long min;
        private final long max;
        private final String remark;
        private final Supplier<String> defaultSupplier;

        public WhitelistMeta(String key, String name, long min, long max, String remark, Supplier<String> defaultSupplier) {
            this.key = key;
            this.name = name;
            this.min = min;
            this.max = max;
            this.remark = remark;
            this.defaultSupplier = defaultSupplier;
        }

        public String getKey() { return key; }
        public String getName() { return name; }
        public long getMin() { return min; }
        public long getMax() { return max; }
        public String getRemark() { return remark; }
        public Supplier<String> getDefaultSupplier() { return defaultSupplier; }
    }

    public Map<String, WhitelistMeta> getWhitelistMap() {
        Map<String, WhitelistMeta> map = new LinkedHashMap<>();
        map.put(KEY_SLOW_SQL_THRESHOLD, new WhitelistMeta(
                KEY_SLOW_SQL_THRESHOLD,
                "慢接口与慢SQL判定告警阈值",
                SLOW_SQL_MIN_MS, SLOW_SQL_MAX_MS,
                "慢接口与慢SQL判定告警阈值 (毫秒，范围 100~5000)",
                () -> String.valueOf(systemProperties.getSlowSql().getThresholdMs())
        ));
        map.put(KEY_BACKUP_RETENTION_DAYS, new WhitelistMeta(
                KEY_BACKUP_RETENTION_DAYS,
                "数据库热备文件保留周期",
                BACKUP_RETENTION_MIN_DAYS, BACKUP_RETENTION_MAX_DAYS,
                "数据库热备文件保留周期 (天，范围 7~365)",
                () -> String.valueOf(systemProperties.getBackup().getRetentionDays())
        ));
        map.put(KEY_BACKUP_RATE_LIMIT, new WhitelistMeta(
                KEY_BACKUP_RATE_LIMIT,
                "数据库热备执行防抖限流",
                BACKUP_RATE_LIMIT_MIN_SEC, BACKUP_RATE_LIMIT_MAX_SEC,
                "数据库热备执行与下载防刷限流冷却 (秒，范围 5~120)",
                () -> String.valueOf(systemProperties.getBackup().getRateLimitSeconds())
        ));
        map.put(KEY_JOB_TIMEOUT, new WhitelistMeta(
                KEY_JOB_TIMEOUT,
                "定时调度批处理任务执行超时",
                JOB_TIMEOUT_MIN_SEC, JOB_TIMEOUT_MAX_SEC,
                "定时调度批处理任务单次执行硬超时 (秒，范围 10~300)",
                () -> String.valueOf(systemProperties.getJob().getExecutionTimeoutSeconds())
        ));
        return map;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfigVO> getConfigList(LoginUser loginUser) {
        checkSysAdmin(loginUser);
        Map<String, WhitelistMeta> whitelist = getWhitelistMap();
        List<ConfigVO> result = new ArrayList<>();

        for (WhitelistMeta meta : whitelist.values()) {
            SysConfig config = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, meta.getKey())
                    .eq(SysConfig::getIsDeleted, 0));

            String defaultVal = meta.getDefaultSupplier().get();
            String currentVal = (config != null && StringUtils.hasText(config.getConfigValue()))
                    ? config.getConfigValue()
                    : defaultVal;

            result.add(ConfigVO.builder()
                    .id(config != null ? config.getId() : null)
                    .configName(config != null ? config.getConfigName() : meta.getName())
                    .configKey(meta.getKey())
                    .configValue(currentVal)
                    .defaultValue(defaultVal)
                    .isSystem(config != null ? config.getIsSystem() : 1)
                    .remark(config != null ? config.getRemark() : meta.getRemark())
                    .updatedTime(config != null ? config.getUpdatedTime() : null)
                    .updatedBy(config != null ? config.getUpdatedBy() : null)
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(String key, ConfigUpdateDTO dto, String clientIp, LoginUser loginUser) {
        checkSysAdmin(loginUser);
        WhitelistMeta meta = checkWhitelistKey(key);

        if (dto == null || !StringUtils.hasText(dto.getConfigValue())) {
            throw new BusinessException(400, "配置值不能为空");
        }

        String newValue = dto.getConfigValue().trim();
        validateValue(meta, newValue);

        SysConfig config = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .eq(SysConfig::getIsDeleted, 0));

        LocalDateTime now = LocalDateTime.now();
        String oldValue = (config != null) ? config.getConfigValue() : meta.getDefaultSupplier().get();

        if (config == null) {
            config = SysConfig.builder()
                    .configName(meta.getName())
                    .configKey(key)
                    .configValue(newValue)
                    .isSystem(1)
                    .remark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : meta.getRemark())
                    .createdBy(loginUser.getUsername())
                    .createdTime(now)
                    .updatedBy(loginUser.getUsername())
                    .updatedTime(now)
                    .isDeleted(0)
                    .build();
            sysConfigMapper.insert(config);
        } else {
            config.setConfigValue(newValue);
            if (StringUtils.hasText(dto.getRemark())) {
                config.setRemark(dto.getRemark());
            }
            config.setUpdatedBy(loginUser.getUsername());
            config.setUpdatedTime(now);
            sysConfigMapper.updateById(config);
        }

        // 审计留痕
        String operParam = String.format("{\"key\":\"%s\",\"oldValue\":\"%s\",\"newValue\":\"%s\",\"remark\":\"%s\"}",
                key, oldValue, newValue, dto.getRemark() != null ? dto.getRemark() : "");
        operationLogService.logOperation("系统运维参数更新", "UPDATE", "updateConfig", "PUT",
                loginUser.getUserId(), loginUser.getUsername(), "/api/v1/system/configs/" + key,
                clientIp != null ? clientIp : "127.0.0.1", operParam, "{\"status\":\"SUCCESS\"}", 1, null);

        // 事务提交后再逐出本地缓存，失败时保持旧配置
        evictCacheAfterCommit(key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetConfig(String key, String clientIp, LoginUser loginUser) {
        checkSysAdmin(loginUser);
        WhitelistMeta meta = checkWhitelistKey(key);

        String defaultValue = meta.getDefaultSupplier().get();
        SysConfig config = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .eq(SysConfig::getIsDeleted, 0));

        LocalDateTime now = LocalDateTime.now();
        String oldValue = (config != null) ? config.getConfigValue() : defaultValue;

        if (config == null) {
            config = SysConfig.builder()
                    .configName(meta.getName())
                    .configKey(key)
                    .configValue(defaultValue)
                    .isSystem(1)
                    .remark(meta.getRemark())
                    .createdBy(loginUser.getUsername())
                    .createdTime(now)
                    .updatedBy(loginUser.getUsername())
                    .updatedTime(now)
                    .isDeleted(0)
                    .build();
            sysConfigMapper.insert(config);
        } else {
            config.setConfigValue(defaultValue);
            config.setRemark(meta.getRemark());
            config.setUpdatedBy(loginUser.getUsername());
            config.setUpdatedTime(now);
            sysConfigMapper.updateById(config);
        }

        // 审计留痕
        String operParam = String.format("{\"key\":\"%s\",\"oldValue\":\"%s\",\"resetDefaultValue\":\"%s\"}",
                key, oldValue, defaultValue);
        operationLogService.logOperation("系统运维参数出厂重置", "UPDATE", "resetConfig", "POST",
                loginUser.getUserId(), loginUser.getUsername(), "/api/v1/system/configs/" + key + "/reset",
                clientIp != null ? clientIp : "127.0.0.1", operParam, "{\"status\":\"SUCCESS\"}", 1, null);

        // 事务提交后再逐出本地缓存
        evictCacheAfterCommit(key);
    }

    @Override
    public String getConfigValue(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }

        // 1. 优先检索 Caffeine 本地缓存
        String cached = configCaffeineCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        // 2. Cache Miss 检索 DB
        SysConfig config = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .eq(SysConfig::getIsDeleted, 0));

        if (config != null && StringUtils.hasText(config.getConfigValue())) {
            configCaffeineCache.put(key, config.getConfigValue());
            return config.getConfigValue();
        }

        // 3. DB 不存在则回退 application.yml 默认值
        WhitelistMeta meta = getWhitelistMap().get(key);
        if (meta != null) {
            String defaultVal = meta.getDefaultSupplier().get();
            configCaffeineCache.put(key, defaultVal);
            return defaultVal;
        }

        return null;
    }

    @Override
    public int getIntValue(String key, int defaultValue) {
        String val = getConfigValue(key);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 [{}] 非合法整数，回退默认值 {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public long getLongValue(String key, long defaultValue) {
        String val = getConfigValue(key);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 [{}] 非合法长整数，回退默认值 {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    private WhitelistMeta checkWhitelistKey(String key) {
        Map<String, WhitelistMeta> whitelist = getWhitelistMap();
        if (key == null || !whitelist.containsKey(key)) {
            throw new BusinessException(400, "该配置项属于阶段6/7封板只读保护或非白名单运维项，严禁通过动态参数修改");
        }
        return whitelist.get(key);
    }

    private void validateValue(WhitelistMeta meta, String value) {
        try {
            long val = Long.parseLong(value);
            if (val < meta.getMin() || val > meta.getMax()) {
                throw new BusinessException(400, String.format("参数值超出合规范围 [%d ~ %d]，当前传入值: %d", meta.getMin(), meta.getMax(), val));
            }
        } catch (NumberFormatException e) {
            throw new BusinessException(400, String.format("参数值必须为合法整型数值，范围 [%d ~ %d]", meta.getMin(), meta.getMax()));
        }
    }

    private void checkSysAdmin(LoginUser loginUser) {
        if (loginUser == null || !"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "无权访问运维配置模块，仅限系统管理员操作");
        }
    }

    private void evictCacheAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    configCaffeineCache.invalidate(key);
                    log.info("事务已成功提交，已自动逐出本地 Caffeine 缓存: key={}", key);
                }
            });
        } else {
            configCaffeineCache.invalidate(key);
            log.info("无活动事务环境，直接逐出本地 Caffeine 缓存: key={}", key);
        }
    }
}
