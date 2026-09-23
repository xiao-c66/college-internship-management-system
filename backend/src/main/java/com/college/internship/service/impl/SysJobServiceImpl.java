package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.SystemProperties;
import com.college.internship.constant.JobCodeEnum;
import com.college.internship.entity.BasePhase7Entity;
import com.college.internship.entity.InternshipArchive;
import com.college.internship.entity.SysBackupRecord;
import com.college.internship.entity.SysJob;
import com.college.internship.mapper.InternshipArchiveMapper;
import com.college.internship.mapper.SysBackupRecordMapper;
import com.college.internship.mapper.SysJobMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysConfigService;
import com.college.internship.service.ISysJobService;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.service.IWarnService;
import com.college.internship.vo.SysJobVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 阶段8受限制定时任务业务实现
 * 严格遵循企业级调度规约：
 * 1. 纯白名单枚举受控调度，严禁反射或执行任意外部类/方法；
 * 2. 进程内互斥锁防止任务重入；
 * 3. 窗口幂等键机制 (JOB_CODE_yyyyMMdd_HHmm) 防抖跳过；
 * 4. 统一绑定 Asia/Shanghai 业务时区；
 * 5. 硬超时与指数退避重试 (最多2次)；
 * 6. 每次执行结构化记录 sys_operation_log (business_type = SCHEDULE, oper_url = job://编码)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysJobServiceImpl implements ISysJobService {

    private final SysJobMapper sysJobMapper;
    private final SysBackupRecordMapper sysBackupRecordMapper;
    private final InternshipArchiveMapper archiveMapper;
    private final IWarnService warnService;
    private final ISysOperationLogService operationLogService;
    private final SystemProperties systemProperties;
    private final ISysConfigService sysConfigService;

    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    // 进程内并发互斥锁：防止同一任务编码并发重入
    private final ConcurrentHashMap<String, AtomicBoolean> RUNNING_MUTEX = new ConcurrentHashMap<>();

    // 业务幂等追踪键缓存：同窗口期 (分钟级) 重复触发自动跳过
    private final ConcurrentHashMap<String, Long> IDEMPOTENT_WINDOW_CACHE = new ConcurrentHashMap<>();

    @Override
    public List<SysJobVO> getJobList(LoginUser loginUser) {
        if (!"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅超级管理员允许查看定时任务配置列表");
        }

        List<String> whitelist = Arrays.stream(JobCodeEnum.values())
                .map(JobCodeEnum::getCode)
                .collect(Collectors.toList());

        List<SysJob> jobs = sysJobMapper.selectList(new LambdaQueryWrapper<SysJob>()
                .in(SysJob::getJobCode, whitelist)
                .eq(SysJob::getIsDeleted, 0)
                .orderByAsc(SysJob::getId));

        return jobs.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysJobVO toggleJob(Long id, LoginUser loginUser) {
        if (!"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅超级管理员允许操作定时任务启停");
        }

        SysJob job = sysJobMapper.selectById(id);
        if (job == null || job.getIsDeleted() == 1) {
            throw new BusinessException(400, "定时任务不存在");
        }

        // 受限白名单强校验 (TEST-P8-06)
        if (!JobCodeEnum.isValid(job.getJobCode())) {
            throw new BusinessException(400, "非受限白名单任务编码，拒绝操作: " + job.getJobCode());
        }

        int newStatus = (job.getStatus() == 1) ? 0 : 1;
        job.setStatus(newStatus);
        job.setUpdatedBy(loginUser.getUsername());
        job.setUpdatedTime(LocalDateTime.now(SHANGHAI_ZONE));
        sysJobMapper.updateById(job);

        // 写入审计日志
        operationLogService.logOperation(
                "切换定时任务状态-" + job.getJobName(),
                "UPDATE",
                "SysJobServiceImpl.toggleJob",
                "PUT",
                loginUser.getUserId(),
                loginUser.getUsername(),
                "/api/v1/system/jobs/" + id + "/toggle",
                "127.0.0.1",
                String.format("{\"id\":%d,\"jobCode\":\"%s\",\"status\":%d}", id, job.getJobCode(), newStatus),
                String.format("{\"status\":%d}", newStatus),
                1,
                null
        );

        return toVO(job);
    }

    @Override
    public Map<String, Object> triggerJob(Long id, LoginUser loginUser) {
        if (!"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅超级管理员允许手动触发定时任务执行");
        }

        SysJob job = sysJobMapper.selectById(id);
        if (job == null || job.getIsDeleted() == 1) {
            throw new BusinessException(400, "定时任务不存在");
        }

        // 受限白名单强校验 (TEST-P8-06)
        if (!JobCodeEnum.isValid(job.getJobCode())) {
            throw new BusinessException(400, "非受限白名单任务编码，拒绝执行: " + job.getJobCode());
        }

        return executeJobInternal(job, loginUser, true);
    }

    @Override
    public Map<String, Object> executeJobDirect(String jobCode, LoginUser loginUser, boolean isManual) {
        if (!JobCodeEnum.isValid(jobCode)) {
            throw new BusinessException(400, "非受限白名单任务编码，拒绝执行: " + jobCode);
        }

        SysJob job = sysJobMapper.selectOne(new LambdaQueryWrapper<SysJob>()
                .eq(SysJob::getJobCode, jobCode)
                .eq(SysJob::getIsDeleted, 0));

        if (job == null) {
            throw new BusinessException(400, "定时任务未在系统中初始化: " + jobCode);
        }

        return executeJobInternal(job, loginUser, isManual);
    }

    @Override
    public synchronized void initDefaultJobs() {
        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        for (JobCodeEnum enumItem : JobCodeEnum.values()) {
            Long count = sysJobMapper.selectCount(new LambdaQueryWrapper<SysJob>()
                    .eq(SysJob::getJobCode, enumItem.getCode())
                    .eq(SysJob::getIsDeleted, 0));

            if (count == null || count == 0) {
                String remark;
                switch (enumItem) {
                    case WARN_SCAN_JOB:
                        remark = "每日凌晨2点全盘扫描学业预警并自动升级逾期工单";
                        break;
                    case ARCHIVE_EXPIRE_RECLOCK_JOB:
                        remark = "每30分钟扫描特批解锁逾期的电子卷宗并自动重置为已归档锁定";
                        break;
                    case BACKUP_CLEANUP_JOB:
                        remark = "每日凌晨3点清理超过保留期限的非锁定热备份记录与物理文件";
                        break;
                    default:
                        remark = enumItem.getName();
                }

                SysJob newJob = SysJob.builder()
                        .jobCode(enumItem.getCode())
                        .jobName(enumItem.getName())
                        .cronExpression(enumItem.getDefaultCron())
                        .status(1)
                        .remark(remark)
                        .createdBy("SYSTEM_INIT")
                        .createdTime(now)
                        .updatedBy("SYSTEM_INIT")
                        .updatedTime(now)
                        .isDeleted(0)
                        .build();

                sysJobMapper.insert(newJob);
                log.info("【定时任务初始化】已初始化白名单调度任务: code={}, name={}", enumItem.getCode(), enumItem.getName());
            }
        }
    }

    @Override
    public void clearIdempotencyCache() {
        IDEMPOTENT_WINDOW_CACHE.clear();
        log.info("【定时调度】幂等窗口缓存已清除");
    }

    /**
     * 调度任务执行核心受控模板
     */
    private Map<String, Object> executeJobInternal(SysJob job, LoginUser loginUser, boolean isManual) {
        ZonedDateTime nowZoned = ZonedDateTime.now(SHANGHAI_ZONE);
        LocalDateTime now = nowZoned.toLocalDateTime();
        String jobCode = job.getJobCode();

        // 1. 进程内互斥锁：避免同一任务重入 (落实要求8)
        AtomicBoolean mutex = RUNNING_MUTEX.computeIfAbsent(jobCode, k -> new AtomicBoolean(false));
        if (!mutex.compareAndSet(false, true)) {
            log.warn("任务 [{}] 当前正在执行中，互斥拦截重入", jobCode);
            Map<String, Object> skipped = new LinkedHashMap<>();
            skipped.put("jobCode", jobCode);
            skipped.put("status", "SKIPPED");
            skipped.put("reason", "MUTEX_RUNNING");
            skipped.put("costMs", 0L);
            skipped.put("processedCount", 0);
            return skipped;
        }

        try {
            // 2. 幂等键检查：JOB_CODE_yyyyMMdd_HHmm (落实要求9)
            String windowStr = nowZoned.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            String idempotencyKey = jobCode + "_" + windowStr;
            Long prevTime = IDEMPOTENT_WINDOW_CACHE.putIfAbsent(idempotencyKey, System.currentTimeMillis());
            if (prevTime != null) {
                log.info("任务 [{}] 在窗口 [{}] 已执行，幂等机制自动跳过", jobCode, idempotencyKey);
                Map<String, Object> skipped = new LinkedHashMap<>();
                skipped.put("jobCode", jobCode);
                skipped.put("status", "SKIPPED");
                skipped.put("reason", "IDEMPOTENT_WINDOW");
                skipped.put("idempotencyKey", idempotencyKey);
                skipped.put("costMs", 0L);
                skipped.put("processedCount", 0);
                return skipped;
            }

            // 3. 超时与重试控制 (落实要求10)
            int timeoutSec = systemProperties.getJob().getExecutionTimeoutSeconds();
            int maxRetries = systemProperties.getJob().getMaxRetries();

            long startMs = System.currentTimeMillis();
            int processedCount = 0;
            String statusStr = "SUCCESS";
            String errorMsg = null;
            Exception lastException = null;

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    final LocalDateTime execTime = now;
                    CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() ->
                            dispatchSpecificJob(jobCode, execTime, loginUser)
                    );
                    processedCount = future.get(timeoutSec, TimeUnit.SECONDS);
                    lastException = null;
                    break;
                } catch (TimeoutException te) {
                    lastException = te;
                    log.warn("任务 [{}] 第 {} 次执行超时 (超时限制: {}s)", jobCode, attempt + 1, timeoutSec);
                    if (attempt < maxRetries) {
                        sleepExponential(attempt);
                    }
                } catch (Exception ex) {
                    Throwable root = ex instanceof ExecutionException ? ex.getCause() : ex;
                    lastException = root instanceof Exception ? (Exception) root : new RuntimeException(root);
                    log.warn("任务 [{}] 第 {} 次执行失败: {}", jobCode, attempt + 1, lastException.getMessage());
                    if (attempt < maxRetries) {
                        sleepExponential(attempt);
                    }
                }
            }

            long costMs = System.currentTimeMillis() - startMs;
            if (lastException != null) {
                statusStr = "FAILED";
                errorMsg = lastException.getMessage() != null ? lastException.getMessage() : lastException.toString();
                if (errorMsg.length() > 2000) {
                    errorMsg = errorMsg.substring(0, 2000);
                }
            }

            // 4. 写入系统审计日志 (落实要求11、12)
            String jsonResult = String.format(
                    "{\"costMs\":%d,\"processedCount\":%d,\"status\":\"%s\"}",
                    costMs, processedCount, statusStr
            );

            operationLogService.logOperation(
                    "定时任务调度-" + job.getJobName(),
                    "SCHEDULE",
                    "SysJobServiceImpl.executeJob",
                    isManual ? "MANUAL" : "SYSTEM",
                    isManual && loginUser != null ? loginUser.getUserId() : 0L,
                    isManual && loginUser != null ? loginUser.getUsername() : "SYSTEM_SCHEDULER",
                    "job://" + jobCode,
                    "127.0.0.1",
                    String.format("{\"jobCode\":\"%s\",\"isManual\":%b}", jobCode, isManual),
                    jsonResult,
                    "SUCCESS".equals(statusStr) ? 1 : 0,
                    errorMsg
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("jobCode", jobCode);
            result.put("status", statusStr);
            result.put("costMs", costMs);
            result.put("processedCount", processedCount);
            result.put("idempotencyKey", idempotencyKey);
            if (errorMsg != null) {
                result.put("errorMsg", errorMsg);
            }
            return result;
        } finally {
            mutex.set(false);
        }
    }

    /**
     * 严格禁止反射，受控单一分发执行 (落实要求2、3)
     */
    private int dispatchSpecificJob(String jobCode, LocalDateTime now, LoginUser loginUser) {
        JobCodeEnum jobEnum = JobCodeEnum.fromCode(jobCode);
        if (jobEnum == null) {
            throw new BusinessException(400, "非受限白名单任务编码，拒绝分发: " + jobCode);
        }

        switch (jobEnum) {
            case ARCHIVE_EXPIRE_RECLOCK_JOB:
                return handleArchiveExpireReclock(now);
            case BACKUP_CLEANUP_JOB:
                return handleBackupCleanup(now);
            case WARN_SCAN_JOB:
                return handleWarnScan(loginUser);
            default:
                throw new BusinessException(400, "未定义处理器的白名单任务: " + jobCode);
        }
    }

    /**
     * 落实要求4、5：
     * ARCHIVE_EXPIRE_RECLOCK_JOB 只能处理：
     * status = SPECIAL_UNLOCKED 且 unlock_expire_time <= NOW() 的逾期记录，并将其恢复为 ARCHIVED。
     * 严禁扫描或修改其他 ARCHIVED 历史卷宗。
     */
    private int handleArchiveExpireReclock(LocalDateTime now) {
        List<InternshipArchive> overdueList = archiveMapper.selectList(
                new LambdaQueryWrapper<InternshipArchive>()
                        .eq(InternshipArchive::getStatus, "SPECIAL_UNLOCKED")
                        .le(InternshipArchive::getUnlockExpireTime, now)
                        .eq(BasePhase7Entity::getIsDeleted, 0)
        );

        int count = 0;
        for (InternshipArchive archive : overdueList) {
            archive.setStatus("ARCHIVED");
            archive.setUpdatedAt(now);
            archiveMapper.updateById(archive);
            count++;
            log.info("【特批解锁到期重锁】卷宗 ID={}, archiveNo={} 已恢复为 ARCHIVED 锁定状态",
                    archive.getId(), archive.getArchiveNo());
        }
        return count;
    }

    /**
     * 落实要求6：
     * BACKUP_CLEANUP_JOB 只能清理超过保留期且 is_locked=0 的备份文件和记录，
     * 锁定备份、基线备份、里程碑备份绝对不能删除。
     */
    private int handleBackupCleanup(LocalDateTime now) {
        int retentionDays = 30;
        try {
            String configVal = sysConfigService.getConfigValue("system.backup.retention-days");
            if (StringUtils.hasText(configVal)) {
                retentionDays = Integer.parseInt(configVal.trim());
            }
        } catch (Exception e) {
            retentionDays = systemProperties.getBackup().getRetentionDays();
        }
        if (retentionDays <= 0) {
            retentionDays = 30;
        }

        LocalDateTime cutoffTime = now.minusDays(retentionDays);

        // 仅检索超过保留期且 is_locked = 0 的记录
        List<SysBackupRecord> candidates = sysBackupRecordMapper.selectList(
                new LambdaQueryWrapper<SysBackupRecord>()
                        .lt(SysBackupRecord::getBackupTime, cutoffTime)
                        .eq(SysBackupRecord::getIsLocked, 0)
        );

        int count = 0;
        String storageDirStr = systemProperties.getBackup().getStorageDir();
        Path baseDir = Paths.get(storageDirStr != null ? storageDirStr : "./backup").toAbsolutePath().normalize();

        for (SysBackupRecord record : candidates) {
            // 一级防御：锁定备份绝对不能删除
            if (record.getIsLocked() != null && record.getIsLocked() == 1) {
                continue;
            }

            // 二级防御：基线备份、里程碑备份绝对不能删除
            String fileName = record.getBackupFileName();
            if (fileName != null) {
                String lower = fileName.toLowerCase();
                if (lower.contains("baseline") || lower.contains("milestone")
                        || lower.contains("contract_fix") || lower.contains("initial")) {
                    log.info("【备份保护】跳过受保护的基线/里程碑备份: id={}, fileName={}", record.getId(), fileName);
                    continue;
                }
            }

            // 安全删除物理文件 (防目录穿越)
            if (StringUtils.hasText(fileName)) {
                try {
                    Path target = baseDir.resolve(fileName).normalize();
                    if (target.startsWith(baseDir) && Files.exists(target)) {
                        Files.delete(target);
                        log.info("【备份清理】物理文件已删除: {}", target);
                    }
                } catch (Exception e) {
                    log.warn("【备份清理】删除物理备份文件失败: {}", fileName, e);
                }
            }

            sysBackupRecordMapper.deleteById(record.getId());
            count++;
            log.info("【备份清理】已清理过期未锁定备份记录: ID={}, fileName={}", record.getId(), fileName);
        }
        return count;
    }

    /**
     * 预警全盘扫描联动执行
     */
    private int handleWarnScan(LoginUser loginUser) {
        // 定时/受控调度任务统一定义为系统调度身份 (userId=0)，完全与人类管理员交互界面的防刷流控解耦
        LoginUser scanUser = new LoginUser();
        scanUser.setUserId(0L);
        scanUser.setUsername("SYSTEM_SCHEDULER");
        scanUser.setRealName("系统定时调度");
        scanUser.setUserType("SYS_ADMIN");

        Map<String, Object> resultMap = warnService.executeScan(null, scanUser);
        int newTickets = 0;
        if (resultMap != null && resultMap.containsKey("newTickets")) {
            Object val = resultMap.get("newTickets");
            if (val instanceof Number) {
                newTickets = ((Number) val).intValue();
            }
        }
        return newTickets;
    }

    private void sleepExponential(int attempt) {
        try {
            long sleepMs = 1000L * (1 << attempt); // 1s, 2s
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private SysJobVO toVO(SysJob job) {
        return SysJobVO.builder()
                .id(job.getId())
                .jobCode(job.getJobCode())
                .jobName(job.getJobName())
                .cronExpression(job.getCronExpression())
                .status(job.getStatus())
                .remark(job.getRemark())
                .createdTime(job.getCreatedTime())
                .updatedTime(job.getUpdatedTime())
                .build();
    }
}
