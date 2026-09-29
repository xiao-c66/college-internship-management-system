package com.college.internship.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 验证码与系统通知外发通道配置属性
 * 密钥仅从环境配置读取，生产环境未配置真实 provider 时必须安全失败并提示管理员配置，不能对用户假报发送成功。
 */
@Data
@Component
@ConfigurationProperties(prefix = "notification.delivery")
public class NotificationDeliveryProperties {

    private SmsConfig sms = new SmsConfig();
    private EmailConfig email = new EmailConfig();

    @Data
    public static class SmsConfig {
        /**
         * 短信服务商标识：none(未配置/默认), mock(开发测试模拟), aliyun, tencent
         */
        private String provider = "none";
        private String accessKey;
        private String secretKey;
        private String signName;
        private String templateCode;
    }

    @Data
    public static class EmailConfig {
        /**
         * 邮件服务商标识：none(未配置/默认), mock(开发测试模拟), smtp
         */
        private String provider = "none";
        private String host;
        private Integer port = 465;
        private String username;
        private String password;
        private String from;
    }
}
