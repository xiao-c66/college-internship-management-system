package com.college.internship.delivery;

import com.college.internship.common.BusinessException;
import com.college.internship.config.NotificationDeliveryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 验证码外发适配层实现
 * 密钥仅从环境配置读取；开发环境使用明确标记的 mock provider，
 * 生产环境未配置真实 provider 时必须安全失败并提示管理员配置，严禁对用户假报发送成功。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeDeliveryServiceImpl implements IVerificationCodeSender {

    private final NotificationDeliveryProperties properties;
    private final MockVerificationCodeProvider mockProvider;

    @Override
    public DeliveryResult sendCode(DeliveryChannel channel, String target, String code) {
        if (channel == DeliveryChannel.SMS) {
            return sendSms(target, code);
        } else if (channel == DeliveryChannel.EMAIL) {
            return sendEmail(target, code);
        } else {
            throw new BusinessException(400, "不支持的外发通道类型");
        }
    }

    private DeliveryResult sendSms(String target, String code) {
        NotificationDeliveryProperties.SmsConfig config = properties.getSms();
        String provider = config.getProvider() != null ? config.getProvider().trim().toLowerCase() : "none";

        // 开发与测试环境：使用明确标记的 mock provider
        if ("mock".equals(provider)) {
            return mockProvider.deliver(DeliveryChannel.SMS, target, code);
        }

        // 生产或未配置环境：安全失败，明确提示管理员配置，杜绝假报成功
        if ("none".equals(provider) || !StringUtils.hasText(provider)) {
            log.warn("短信外发通道未配置: target={}", maskTarget(target));
            throw new BusinessException(503, "系统短信外发通道未配置，请联系系统管理员配置相关服务商凭证");
        }

        // 真实服务商凭据校验：密钥仅从环境变量/配置文件读取，缺少必填凭据时立即安全失败
        if (!StringUtils.hasText(config.getAccessKey()) || !StringUtils.hasText(config.getSecretKey())) {
            log.error("真实短信服务商凭据不完整: provider={}", provider);
            throw new BusinessException(503, "系统短信服务商凭据未完整配置，请联系系统管理员");
        }

        // 提示服务商驱动未接入，严禁编造凭证或假报成功
        throw new BusinessException(501, "已配置真实短信服务商 [" + provider + "]，但驱动组件待接入，请联系管理员处理");
    }

    private DeliveryResult sendEmail(String target, String code) {
        NotificationDeliveryProperties.EmailConfig config = properties.getEmail();
        String provider = config.getProvider() != null ? config.getProvider().trim().toLowerCase() : "none";

        if ("mock".equals(provider)) {
            return mockProvider.deliver(DeliveryChannel.EMAIL, target, code);
        }

        if ("none".equals(provider) || !StringUtils.hasText(provider)) {
            log.warn("邮件外发通道未配置: target={}", maskTarget(target));
            throw new BusinessException(503, "系统邮件外发通道未配置，请联系系统管理员配置相关服务商凭证");
        }

        if (!StringUtils.hasText(config.getHost()) || !StringUtils.hasText(config.getUsername()) || !StringUtils.hasText(config.getPassword())) {
            log.error("真实邮件服务商凭据不完整: provider={}", provider);
            throw new BusinessException(503, "系统邮件服务商凭据未完整配置，请联系系统管理员");
        }

        throw new BusinessException(501, "已配置真实邮件服务商 [" + provider + "]，但驱动组件待接入，请联系管理员处理");
    }

    @Override
    public String getActiveProviderName(DeliveryChannel channel) {
        if (channel == DeliveryChannel.SMS) {
            return properties.getSms().getProvider();
        } else {
            return properties.getEmail().getProvider();
        }
    }

    @Override
    public boolean isConfigured(DeliveryChannel channel) {
        if (channel == DeliveryChannel.SMS) {
            String p = properties.getSms().getProvider();
            return "mock".equalsIgnoreCase(p) || (StringUtils.hasText(p) && !"none".equalsIgnoreCase(p)
                    && StringUtils.hasText(properties.getSms().getAccessKey())
                    && StringUtils.hasText(properties.getSms().getSecretKey()));
        } else {
            String p = properties.getEmail().getProvider();
            return "mock".equalsIgnoreCase(p) || (StringUtils.hasText(p) && !"none".equalsIgnoreCase(p)
                    && StringUtils.hasText(properties.getEmail().getHost())
                    && StringUtils.hasText(properties.getEmail().getUsername())
                    && StringUtils.hasText(properties.getEmail().getPassword()));
        }
    }

    private String maskTarget(String target) {
        if (target == null) return "***";
        if (target.contains("@")) {
            int atIndex = target.indexOf("@");
            return (atIndex > 2 ? target.substring(0, 2) + "***" : "***") + target.substring(atIndex);
        } else if (target.length() >= 7) {
            return target.substring(0, 3) + "****" + target.substring(target.length() - 4);
        }
        return "***";
    }
}
