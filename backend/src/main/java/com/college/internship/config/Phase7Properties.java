package com.college.internship.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 阶段7 中期检查、过程预警、五维成绩评定与归档锁定参数配置属性类
 * 严格标注为【部署可调整配置·待业务方确认】
 */
@Component
@ConfigurationProperties(prefix = "internship.phase7")
@Data
public class Phase7Properties {

    private MaterialConfig material = new MaterialConfig();
    private InspectConfig inspect = new InspectConfig();
    private WarnConfig warn = new WarnConfig();
    private ScoreConfig score = new ScoreConfig();
    private ArchiveConfig archive = new ArchiveConfig();

    @Data
    public static class MaterialConfig {
        /** 阶段材料单次允许最大重提版本数 */
        private Integer maxSubmitVersions = 5;
        /** 实习中期进展总结正文最小字数 (默认 500 字) */
        private Integer minMidtermSummaryLength = 500;
        /** 凭据附件最大允许大小 (MB) */
        private Integer maxFileSizeMb = 10;
    }

    @Data
    public static class InspectConfig {
        /** 学生整改措施说明最小字数 (默认 15 字) */
        private Integer minRectifyLength = 15;
        /** 教师复核评价最小字数 (默认 10 字) */
        private Integer minRectifyCommentLength = 10;
    }

    @Data
    public static class WarnConfig {
        /** 异常扫描防刷限流间隔 (秒) */
        private Integer scanRateLimitSeconds = 10;
        /** 预警工单默认超时升级天数 */
        private Integer handlingTimeoutDays = 3;
    }

    @Data
    public static class ScoreConfig {
        /** 毕业实习总结报告正文最小字数 (默认 1500 字) */
        private Integer minSummaryLength = 1500;
        /** 成绩异议申诉理由最小字数 (默认 10 字) */
        private Integer minAppealReasonLength = 10;
        /** 院系调分仲裁说明最小字数 (默认 10 字) */
        private Integer minArbitrateCommentLength = 10;
        /** 默认成绩公示天数 (默认 5 天) */
        private Integer defaultPublicityDays = 5;
        /** 全局默认五级制等第阈值 */
        private GradeLevelConfig gradeRules = new GradeLevelConfig();

        @Data
        public static class GradeLevelConfig {
            private BigDecimal excellentMin = new BigDecimal("90.00");
            private BigDecimal goodMin = new BigDecimal("80.00");
            private BigDecimal mediumMin = new BigDecimal("70.00");
            private BigDecimal passMin = new BigDecimal("60.00");
        }
    }

    @Data
    public static class ArchiveConfig {
        /** 电子卷宗持久化存储物理根目录 */
        private String storageDir = "data/archives";
        /** 卷宗生成临时暂存工作目录 */
        private String tempDir = "data/archives/temp";
        /** 归档周报合格提交率门槛 (%) */
        private BigDecimal minWeeklyReportRate = new BigDecimal("80.00");
        /** 归档最低指导记录次数要求 (次) */
        private Integer minGuidanceCount = 2;
        /** 特批解锁有效时限 (小时) */
        private Integer specialUnlockDurationHours = 24;
        /** 单个外部凭据附件最大允许下载大小 (MB) */
        private Integer maxSingleAttachmentSizeMb = 10;
        /** 卷宗单次导出最大容量限制 (MB) */
        private Integer exportMaxBundleSizeMb = 100;
        /** 导出防刷限流间隔 (秒) */
        private Integer exportRateLimitSeconds = 10;
    }
}
