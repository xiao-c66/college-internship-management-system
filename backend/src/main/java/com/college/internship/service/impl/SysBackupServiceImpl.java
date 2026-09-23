package com.college.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.college.internship.common.BusinessException;
import com.college.internship.config.SystemProperties;
import com.college.internship.entity.SysBackupRecord;
import com.college.internship.mapper.SysBackupRecordMapper;
import com.college.internship.security.LoginUser;
import com.college.internship.service.ISysBackupService;
import com.college.internship.service.ISysConfigService;
import com.college.internship.service.ISysOperationLogService;
import com.college.internship.util.SafePathValidator;
import com.college.internship.vo.SysBackupRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 阶段8受控备份管理业务实现 (API-109 ~ API-111)
 * 严格落实安全准则：
 * 1. 仅限 SYS_ADMIN 授权访问；
 * 2. 严禁字符串拼接 shell 命令，使用 ProcessBuilder 参数数组；
 * 3. 密码通过环境变量 MYSQL_PWD 传递，不落地命令行参数；
 * 4. 仅限针对当前连接库（测试环境下强制仅限 internship_db_test）执行导出；
 * 5. SafePathValidator 防路径穿越；
 * 6. 30秒冷却限流防抖；
 * 7. 写入详细 sys_operation_log 审计日志；
 * 8. 绝对抹除 Windows 物理盘符路径，仅向前端暴露受控相对文件名。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysBackupServiceImpl implements ISysBackupService {

    private final SysBackupRecordMapper sysBackupRecordMapper;
    private final ISysOperationLogService operationLogService;
    private final SystemProperties systemProperties;
    private final ISysConfigService sysConfigService;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @Value("${spring.datasource.username:root}")
    private String dbUsername;

    @Value("${spring.datasource.password:123456}")
    private String dbPassword;

    // 内存防抖限流时间戳 (单位毫秒)
    private volatile long LAST_BACKUP_TIME = 0L;

    @Override
    public List<SysBackupRecordVO> getBackupRecords(LoginUser loginUser) {
        if (!"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅超级管理员允许查阅备份历史记录");
        }

        List<SysBackupRecord> records = sysBackupRecordMapper.selectList(
                new LambdaQueryWrapper<SysBackupRecord>()
                        .orderByDesc(SysBackupRecord::getBackupTime)
        );

        return records.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysBackupRecordVO executeBackup(LoginUser loginUser) {
        // 1. 权限拦截
        if (!"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅超级管理员允许执行受控热备份");
        }

        // 2. 严格核验当前连接数据库名称（安全防呆）
        String currentDb = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!StringUtils.hasText(currentDb)) {
            throw new BusinessException(500, "无法获取当前活动数据库名称");
        }

        boolean isTestProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (isTestProfile && !"internship_db_test".equalsIgnoreCase(currentDb)) {
            throw new BusinessException(400, "【安全阻断】测试环境仅允许对 internship_db_test 执行备份，严禁触碰正式数据库: " + currentDb);
        }

        // 3. 冷却限流防抖校验 (TEST-P8-11: 429 拦截)
        int rateLimitSec = systemProperties.getBackup().getRateLimitSeconds();
        try {
            String confVal = sysConfigService.getConfigValue("system.backup.rate-limit-seconds");
            if (StringUtils.hasText(confVal)) {
                rateLimitSec = Integer.parseInt(confVal.trim());
            }
        } catch (Exception ignored) {
        }

        long nowMs = System.currentTimeMillis();
        if (LAST_BACKUP_TIME > 0 && (nowMs - LAST_BACKUP_TIME) < rateLimitSec * 1000L) {
            long waitSec = (rateLimitSec * 1000L - (nowMs - LAST_BACKUP_TIME)) / 1000L + 1;
            throw new BusinessException(429, "备份操作过于频繁，请等待 " + waitSec + " 秒后重试");
        }
        LAST_BACKUP_TIME = nowMs;

        // 4. 查询当前库物理表总数
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?",
                Integer.class, currentDb);
        if (tableCount == null) {
            tableCount = 37;
        }

        // 5. 准备受控存储目录与相对文件名
        Path baseDir = Paths.get(systemProperties.getBackup().getStorageDir()).toAbsolutePath().normalize();
        try {
            if (!Files.exists(baseDir)) {
                Files.createDirectories(baseDir);
            }
        } catch (IOException e) {
            throw new BusinessException(500, "无法创建备份受控存储目录: " + e.getMessage());
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = currentDb + "_backup_" + timestamp + ".sql";
        Path targetFile = SafePathValidator.validateAndResolve(baseDir.toString(), fileName);

        // 6. 执行 mysqldump (使用 ProcessBuilder 参数数组，严禁 shell 拼接与命令行密码暴露)
        String mysqldumpExe = resolveMysqldumpPath();
        List<String> cmd = new ArrayList<>();
        cmd.add(mysqldumpExe);
        cmd.add("-u");
        cmd.add(dbUsername);
        cmd.add("-h");
        cmd.add("127.0.0.1");
        cmd.add("-P");
        cmd.add("3306");
        cmd.add("--default-character-set=utf8mb4");
        cmd.add("--single-transaction");
        cmd.add("--quick");
        cmd.add(currentDb);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("MYSQL_PWD", dbPassword);
            pb.redirectOutput(targetFile.toFile());
            pb.redirectError(ProcessBuilder.Redirect.PIPE);

            Process process = pb.start();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(500, "mysqldump 执行超时 (60s)");
            }
            if (process.exitValue() != 0) {
                String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                log.error("mysqldump 执行失败: code={}, error={}", process.exitValue(), err);
                throw new BusinessException(500, "数据库热备执行失败: " + err);
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("执行数据库热备份异常", e);
            throw new BusinessException(500, "数据库热备执行异常: " + e.getMessage());
        }

        // 7. 计算文件真实大小与 SHA-256 完整性摘要
        long fileSize;
        String sha256;
        try {
            fileSize = Files.size(targetFile);
            sha256 = calculateSha256(targetFile);
        } catch (IOException e) {
            throw new BusinessException(500, "计算备份文件 SHA-256 散列失败: " + e.getMessage());
        }

        // 8. 元数据入库 (sys_backup_record)
        SysBackupRecord record = SysBackupRecord.builder()
                .backupFileName(fileName)
                .fileSizeBytes(fileSize)
                .tableCount(tableCount)
                .sha256Digest(sha256)
                .isLocked(0)
                .status("SUCCESS")
                .operatorId(loginUser.getUserId())
                .backupTime(LocalDateTime.now())
                .build();
        sysBackupRecordMapper.insert(record);

        // 9. 写入系统操作审计日志 (sys_operation_log)
        String jsonResult = String.format("{\"id\":%d,\"backupFileName\":\"%s\",\"fileSizeBytes\":%d,\"tableCount\":%d,\"sha256\":\"%s\"}",
                record.getId(), fileName, fileSize, tableCount, sha256);
        operationLogService.logOperation(
                "数据库受控热备-执行",
                "BACKUP",
                "SysBackupServiceImpl.executeBackup",
                "MANUAL",
                loginUser.getUserId(),
                loginUser.getUsername(),
                "/api/v1/system/backup/execute",
                "127.0.0.1",
                String.format("{\"database\":\"%s\"}", currentDb),
                jsonResult,
                1,
                null
        );

        log.info("【受控备份】成功生成数据库热备份: id={}, file={}, size={}, sha256={}",
                record.getId(), fileName, fileSize, sha256);

        return toVO(record);
    }

    @Override
    public ResponseEntity<Resource> downloadBackup(Long id, LoginUser loginUser) {
        // 1. 权限拦截
        if (!"SYS_ADMIN".equals(loginUser.getUserType())) {
            throw new BusinessException(403, "仅超级管理员允许下载数据库备份");
        }

        // 2. 检索备份元数据记录
        SysBackupRecord record = sysBackupRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(404, "备份记录不存在: id=" + id);
        }

        // 3. 强制使用 SafePathValidator 进行防穿越安全校验 (TEST-P8-12)
        SafePathValidator.validateFileName(record.getBackupFileName());
        Path baseDir = Paths.get(systemProperties.getBackup().getStorageDir()).toAbsolutePath().normalize();
        Path targetFile = SafePathValidator.validateAndResolve(baseDir.toString(), record.getBackupFileName());

        if (!Files.exists(targetFile) || !Files.isRegularFile(targetFile)) {
            throw new BusinessException(404, "备份物理文件不存在或已被安全策略移除");
        }

        // 4. 写入安全下载审计日志
        operationLogService.logOperation(
                "数据库受控热备-下载",
                "BACKUP",
                "SysBackupServiceImpl.downloadBackup",
                "MANUAL",
                loginUser.getUserId(),
                loginUser.getUsername(),
                "/api/v1/system/backup/download/" + id,
                "127.0.0.1",
                String.format("{\"id\":%d,\"fileName\":\"%s\"}", id, record.getBackupFileName()),
                String.format("{\"fileSizeBytes\":%d}", record.getFileSizeBytes()),
                1,
                null
        );

        try {
            ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(targetFile));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + record.getBackupFileName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(targetFile))
                    .body(resource);
        } catch (IOException e) {
            log.error("读取备份文件流异常: id={}, file={}", id, targetFile, e);
            throw new BusinessException(500, "读取备份文件流失败: " + e.getMessage());
        }
    }

    @Override
    public void clearRateLimitCache() {
        LAST_BACKUP_TIME = 0L;
        log.info("【受控备份】冷却限流缓存已重置");
    }

    private String resolveMysqldumpPath() {
        try {
            Process p = new ProcessBuilder("mysqldump", "--version").start();
            if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) {
                return "mysqldump";
            }
        } catch (Exception ignored) {
        }

        String[] candidates = {
                "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe",
                "C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysqldump.exe",
                "C:\\Program Files (x86)\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe"
        };
        for (String c : candidates) {
            if (Files.exists(Paths.get(c))) {
                return c;
            }
        }
        return "mysqldump";
    }

    private String calculateSha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }

    private SysBackupRecordVO toVO(SysBackupRecord record) {
        // 严格确保仅暴露受控相对文件名，绝不返回包含盘符的绝对路径
        String safeFileName = record.getBackupFileName();
        if (safeFileName != null && (safeFileName.contains("/") || safeFileName.contains("\\"))) {
            safeFileName = Paths.get(safeFileName).getFileName().toString();
        }
        return SysBackupRecordVO.builder()
                .id(record.getId())
                .backupFileName(safeFileName)
                .fileSizeBytes(record.getFileSizeBytes())
                .tableCount(record.getTableCount())
                .sha256Digest(record.getSha256Digest())
                .isLocked(record.getIsLocked())
                .status(record.getStatus())
                .errorMessage(record.getErrorMessage())
                .operatorId(record.getOperatorId())
                .backupTime(record.getBackupTime())
                .build();
    }
}
