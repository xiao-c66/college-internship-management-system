package com.college.internship.service;

/**
 * 操作与审计日志服务接口
 */
public interface ISysOperationLogService extends IBaseService {

    /**
     * 异步/同步记录安全审计日志 (脱敏处理，密码与密钥禁止入库)
     */
    void logOperation(String title, String businessType, String method, String requestMethod,
                      Long operatorId, String operatorName, String operUrl, String operIp,
                      String operParam, String jsonResult, Integer status, String errorMsg);
}
