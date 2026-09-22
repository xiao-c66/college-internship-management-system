package com.college.internship.service.impl;

import com.college.internship.entity.SysOperationLog;
import com.college.internship.mapper.SysOperationLogMapper;
import com.college.internship.service.ISysOperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计日志服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOperationLogServiceImpl implements ISysOperationLogService {

    private final SysOperationLogMapper sysOperationLogMapper;

    @Override
    public void logOperation(String title, String businessType, String method, String requestMethod,
                             Long operatorId, String operatorName, String operUrl, String operIp,
                             String operParam, String jsonResult, Integer status, String errorMsg) {
        try {
            // 参数安全脱敏检查（二次防御）
            String safeParam = operParam;
            if (safeParam != null && (safeParam.contains("password") || safeParam.contains("captcha"))) {
                safeParam = "[PROTECTED SENSITIVE DATA MASKED]";
            }

            SysOperationLog operationLog = SysOperationLog.builder()
                    .title(title)
                    .businessType(businessType)
                    .method(method)
                    .requestMethod(requestMethod)
                    .operatorId(operatorId)
                    .operatorName(operatorName)
                    .operUrl(operUrl)
                    .operIp(operIp)
                    .operParam(safeParam)
                    .jsonResult(jsonResult)
                    .status(status)
                    .errorMsg(errorMsg)
                    .operTime(LocalDateTime.now())
                    .build();

            sysOperationLogMapper.insert(operationLog);
        } catch (Exception e) {
            log.error("写入审计日志异常: {}", e.getMessage());
        }
    }
}
