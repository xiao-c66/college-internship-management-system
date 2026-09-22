package com.college.internship.controller;

import com.college.internship.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统健康检查与骨架连通性探测接口
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @Value("${system.app.name:高校实习全过程管理系统}")
    private String appName;

    @Value("${system.app.version:1.0.0-SNAPSHOT}")
    private String appVersion;

    @Value("${system.app.stage:PHASE_7_ACCEPTED}")
    private String stage;

    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("systemName", appName);
        data.put("version", appVersion);
        data.put("currentStage", stage);
        data.put("javaVersion", System.getProperty("java.version"));
        data.put("springBootVersion", "3.2.3");
        data.put("timestamp", System.currentTimeMillis());
        data.put("message", "高校实习全过程管理系统 - 后端工程骨架运行正常");
        return Result.success("服务运行健康", data);
    }
}
