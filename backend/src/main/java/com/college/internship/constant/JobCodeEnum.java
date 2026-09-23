package com.college.internship.constant;

/**
 * 阶段8受限制定时任务白名单枚举
 * 严格禁止反射执行，仅允许白名单枚举定义受控执行分发
 */
public enum JobCodeEnum {

    WARN_SCAN_JOB("WARN_SCAN_JOB", "预警定时全盘扫描", "0 0 2 * * ?"),
    ARCHIVE_EXPIRE_RECLOCK_JOB("ARCHIVE_EXPIRE_RECLOCK_JOB", "特批解锁逾期自动重锁", "0 0/30 * * * ?"),
    BACKUP_CLEANUP_JOB("BACKUP_CLEANUP_JOB", "过期非锁定热备文件清理", "0 0 3 * * ?");

    private final String code;
    private final String name;
    private final String defaultCron;

    JobCodeEnum(String code, String name, String defaultCron) {
        this.code = code;
        this.name = name;
        this.defaultCron = defaultCron;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDefaultCron() {
        return defaultCron;
    }

    public static boolean isValid(String code) {
        if (code == null) return false;
        for (JobCodeEnum e : values()) {
            if (e.code.equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    public static JobCodeEnum fromCode(String code) {
        if (code == null) return null;
        for (JobCodeEnum e : values()) {
            if (e.code.equalsIgnoreCase(code.trim())) {
                return e;
            }
        }
        return null;
    }
}
