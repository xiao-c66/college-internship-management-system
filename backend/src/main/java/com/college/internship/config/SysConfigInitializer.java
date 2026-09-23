package com.college.internship.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.entity.SysConfig;
import com.college.internship.mapper.SysConfigMapper;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.service.impl.SysConfigServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 阶段8 系统全局运维参数出厂独立初始化机制
 * 在应用启动时完成 4 个白名单参数的底表核验与出厂落盘，
 * 彻底解除 API-103 (GET /api/v1/system/configs) 的隐式写入副作用，保证 GET 查询方法的纯只读与幂等性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysConfigInitializer implements ApplicationRunner {

    private final SysConfigMapper sysConfigMapper;
    private final SystemProperties systemProperties;
    private final ISysOperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        initDefaultConfigs();
    }

    /**
     * 幂等执行出厂参数核验与补充初始化
     */
    public synchronized void initDefaultConfigs() {
        log.info("开始核验系统全局运维参数出厂基准数据...");
        LocalDateTime now = LocalDateTime.now();

        initParamIfAbsent(
                SysConfigServiceImpl.KEY_SLOW_SQL_THRESHOLD,
                "慢接口与慢SQL判定告警阈值",
                String.valueOf(systemProperties.getSlowSql().getThresholdMs()),
                "慢接口与慢SQL判定告警阈值 (毫秒，范围 100~5000)",
                now
        );

        initParamIfAbsent(
                SysConfigServiceImpl.KEY_BACKUP_RETENTION_DAYS,
                "数据库热备文件保留周期",
                String.valueOf(systemProperties.getBackup().getRetentionDays()),
                "数据库热备文件保留周期 (天，范围 7~365)",
                now
        );

        initParamIfAbsent(
                SysConfigServiceImpl.KEY_BACKUP_RATE_LIMIT,
                "数据库热备执行防抖限流",
                String.valueOf(systemProperties.getBackup().getRateLimitSeconds()),
                "数据库热备执行与下载防刷限流冷却 (秒，范围 5~120)",
                now
        );

        initParamIfAbsent(
                SysConfigServiceImpl.KEY_JOB_TIMEOUT,
                "定时调度批处理任务执行超时",
                String.valueOf(systemProperties.getJob().getExecutionTimeoutSeconds()),
                "定时调度批处理任务单次执行硬超时 (秒，范围 10~300)",
                now
        );

        log.info("系统全局运维参数出厂基准数据核验完成");
    }

    private void initParamIfAbsent(String key, String name, String defaultValue, String remark, LocalDateTime now) {
        Long count = sysConfigMapper.selectCount(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .eq(SysConfig::getIsDeleted, 0));

        if (count == null || count == 0) {
            SysConfig config = SysConfig.builder()
                    .configName(name)
                    .configKey(key)
                    .configValue(defaultValue)
                    .isSystem(1)
                    .remark(remark)
                    .createdBy("SYSTEM_INIT")
                    .createdTime(now)
                    .updatedBy("SYSTEM_INIT")
                    .updatedTime(now)
                    .isDeleted(0)
                    .build();
            sysConfigMapper.insert(config);

            // 明确记录独立初始化审计日志
            operationLogService.logOperation(
                    "系统运维参数出厂初始化",
                    "INIT",
                    "SysConfigInitializer.initParamIfAbsent",
                    "SYSTEM",
                    0L,
                    "SYSTEM_INIT",
                    "system://sys_config/init",
                    "127.0.0.1",
                    String.format("{\"key\":\"%s\",\"defaultValue\":\"%s\"}", key, defaultValue),
                    "{\"status\":\"INITIALIZED\"}",
                    1,
                    null
            );
            log.info("已完成出厂初始化写入运维参数: key={}, value={}", key, defaultValue);
        }
    }
}
