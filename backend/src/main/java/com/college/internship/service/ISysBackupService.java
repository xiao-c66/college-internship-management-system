package com.college.internship.service;

import com.college.internship.security.LoginUser;
import com.college.internship.vo.SysBackupRecordVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * 阶段8受控备份管理业务接口 (API-109 ~ API-111)
 * 严格按照 implementation_plan.md 原生契约
 */
public interface ISysBackupService {

    /**
     * 查询备份历史记录列表 (API-109: GET /api/v1/system/backup/records)
     */
    List<SysBackupRecordVO> getBackupRecords(LoginUser loginUser);

    /**
     * 手动触发受控热备份执行 (API-110: POST /api/v1/system/backup/execute)
     */
    SysBackupRecordVO executeBackup(LoginUser loginUser);

    /**
     * 安全受控下载备份文件 (API-111: GET /api/v1/system/backup/download/{id})
     */
    ResponseEntity<Resource> downloadBackup(Long id, LoginUser loginUser);

    /**
     * 清理防刷冷却限流缓存 (单元测试隔离辅助)
     */
    void clearRateLimitCache();
}
