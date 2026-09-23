package com.college.internship.config;

import com.college.internship.service.ISysJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 阶段8受限制定时任务基准出厂初始化器
 * 在应用启动时自动落盘 3 个受限白名单调度任务基准配置
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class SysJobInitializer implements ApplicationRunner {

    private final ISysJobService sysJobService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始核验系统受限定时任务白名单基准数据...");
        sysJobService.initDefaultJobs();
        log.info("系统受限定时任务白名单基准数据核验完成");
    }
}
