package com.college.internship.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阶段8 系统运维、单机缓存、定时调度与监控全局强类型配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "system")
public class SystemProperties {

    private AppConfig app = new AppConfig();
    private SecurityConfig security = new SecurityConfig();
    private CacheConfig cache = new CacheConfig();
    private JobConfig job = new JobConfig();
    private BackupConfig backup = new BackupConfig();
    private SlowSqlConfig slowSql = new SlowSqlConfig();

    @Data
    public static class AppConfig {
        private String name = "高校实习全过程管理系统";
        private String version = "1.0.0-SNAPSHOT";
        private String stage = "PHASE_7_ACCEPTED";
    }

    @Data
    public static class SecurityConfig {
        /**
         * 允许反向代理转发客户端IP的可信来源白名单 (多个以逗号分隔，如 "127.0.0.1,::1,0:0:0:0:0:0:0:1")
         * 非可信来源连接严禁采信 X-Forwarded-For / X-Real-IP 等代理标头
         */
        private String trustedProxies = "127.0.0.1,::1,0:0:0:0:0:0:0:1,localhost";
    }

    @Data
    public static class CacheConfig {
        private CaffeineConfig caffeine = new CaffeineConfig();
    }

    @Data
    public static class CaffeineConfig {
        private Integer maxSize = 1000;
        private Long expireAfterWriteMinutes = 30L;
        private Boolean recordStats = true;
    }

    @Data
    public static class JobConfig {
        private Integer executionTimeoutSeconds = 60;
        private Integer maxRetries = 2;
        private String timezone = "Asia/Shanghai";
    }

    @Data
    public static class BackupConfig {
        private Integer retentionDays = 30;
        private Integer rateLimitSeconds = 30;
        private String storageDir = "./backup";
    }

    @Data
    public static class SlowSqlConfig {
        private Long thresholdMs = 500L;
        private Integer ringBufferSize = 1000;
        private Integer retentionHours = 24;
        private Integer maxStoredRecords = 500;
    }
}
