package com.college.internship.delivery;

/**
 * 验证码外发适配层接口
 */
public interface IVerificationCodeSender {

    /**
     * 发送验证码 (严禁在返回结果、日志中明文记录验证码)
     *
     * @param channel 渠道 (SMS / EMAIL)
     * @param target  目标 (手机号 / 邮箱)
     * @param code    验证码
     * @return 交付结果
     */
    DeliveryResult sendCode(DeliveryChannel channel, String target, String code);

    /**
     * 获取指定渠道当前激活的服务商名称
     */
    String getActiveProviderName(DeliveryChannel channel);

    /**
     * 检查指定渠道是否已完成有效配置
     */
    boolean isConfigured(DeliveryChannel channel);
}
