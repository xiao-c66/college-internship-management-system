package com.college.internship.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 阶段8 有界单机 Caffeine 缓存配置
 * 严格依据 application.yml 绑定的 SystemProperties 声明参数，杜绝代码内硬编码
 */
@Configuration
public class CacheConfig {

    @Bean("configCaffeineCache")
    public Cache<String, String> configCaffeineCache(SystemProperties systemProperties) {
        SystemProperties.CaffeineConfig caffeine = systemProperties.getCache().getCaffeine();

        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(caffeine.getMaxSize())
                .expireAfterWrite(caffeine.getExpireAfterWriteMinutes(), TimeUnit.MINUTES);

        if (Boolean.TRUE.equals(caffeine.getRecordStats())) {
            builder.recordStats();
        }

        return builder.build();
    }
}
