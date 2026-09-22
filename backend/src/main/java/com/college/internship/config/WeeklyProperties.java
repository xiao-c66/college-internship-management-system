package com.college.internship.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阶段6 实习周报与过程指导参数配置属性类
 * 严格标注为【部署可调整配置·待业务方确认】
 */
@Component
@ConfigurationProperties(prefix = "internship.weekly")
@Data
public class WeeklyProperties {

    /** [部署可调整配置·待业务方确认] 周报四大要素每项最小字数 (默认: 15字) */
    private Integer minContentLength = 15;

    /** [部署可调整配置·待业务方确认] 导师退回原因最小字数 (默认: 10字) */
    private Integer minReturnReasonLength = 10;

    /** [部署可调整配置·待业务方确认] 导师批阅评语最小字数 (默认: 10字) */
    private Integer minReviewCommentLength = 10;

    /** [部署可调整配置·待业务方确认] 过程指导交流要点最小字数 (默认: 15字) */
    private Integer minGuidanceLength = 15;

    /** [部署可调整配置·待业务方确认] 学生在岗反馈最小字数 (默认: 5字) */
    private Integer minFeedbackLength = 5;

    /** [部署可调整配置·待业务方确认] API-066 导出走访台账单次最大允许行数 (默认: 5000条) */
    private Integer maxExportRows = 5000;

    /** [部署可调整配置·待业务方确认] API-066 导出走访台账最大时间跨度天数 (默认: 365天) */
    private Integer maxExportDays = 365;

    /** [部署可调整配置·待业务方确认] API-066 导出防刷限流时间 (默认: 10秒) */
    private Integer exportRateLimitSeconds = 10;

    /** [部署可调整配置·待业务方确认] 过程指导凭证文件大小限制预留项 (默认: 10MB, 阶段6不执行物理文件校验) */
    private Integer guidanceVoucherMaxSizeMb = 10;

    /** [部署可调整配置·待业务方确认] 任务最小持续天数限制 (默认: 0表示不强制限制) */
    private Integer minTaskDurationDays = 0;

    /** [部署可调整配置·待业务方确认] 系统全局业务时区 (默认: Asia/Shanghai) */
    private String systemTimezone = "Asia/Shanghai";
}
