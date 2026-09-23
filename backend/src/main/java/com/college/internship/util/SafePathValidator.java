package com.college.internship.util;

import com.college.internship.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 阶段8受控备份安全路径防穿越校验器 (API-111 & TEST-P8-12)
 * 严格落实三重安全防线：
 * 1. 文件名正则表达式白名单 (仅允许字母、数字、下划线、短横线与 .sql 后缀)；
 * 2. 规范化路径与根目录严格前缀校验 (targetFile.startsWith(baseDir))；
 * 3. 拦截 ../、/、\、%00 截断与非法绝对路径。
 */
@Slf4j
public class SafePathValidator {

    private static final Pattern SAFE_FILE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+\\.sql$");

    /**
     * 校验相对文件名格式合法性
     */
    public static void validateFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(400, "备份文件名不能为空");
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\") || fileName.contains("%00")) {
            log.warn("【安全拦截】检测到潜在路径穿越字符: {}", fileName);
            throw new BusinessException(400, "非法文件名格式，拒绝跨目录访问");
        }
        if (!SAFE_FILE_NAME_PATTERN.matcher(fileName).matches()) {
            log.warn("【安全拦截】文件名不符合受控安全命名规范: {}", fileName);
            throw new BusinessException(400, "文件名格式不合规，仅允许数字、字母、下划线及.sql后缀");
        }
    }

    /**
     * 校验物理路径规范化与目录边界
     */
    public static Path validateAndResolve(String storageDir, String fileName) {
        validateFileName(fileName);
        Path baseDir = Paths.get(storageDir).toAbsolutePath().normalize();
        Path targetFile = baseDir.resolve(fileName).normalize();

        if (!targetFile.startsWith(baseDir)) {
            log.warn("【安全拦截】文件路径越界逃逸: target={}, base={}", targetFile, baseDir);
            throw new BusinessException(400, "非法下载请求，拒绝跨目录访问");
        }
        return targetFile;
    }
}
