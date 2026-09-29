package com.college.internship.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 开发/测试专用模拟外发服务商 (MOCK PROVIDER)
 * 明确标示为模拟通道，严禁在日志中记录验证码明文
 */
@Slf4j
@Component
public class MockVerificationCodeProvider {

    public static final String PROVIDER_NAME = "MOCK_PROVIDER";

    private final AtomicInteger sentCount = new AtomicInteger(0);
    private volatile String lastTarget;
    private volatile DeliveryChannel lastChannel;

    public DeliveryResult deliver(DeliveryChannel channel, String target, String code) {
        sentCount.incrementAndGet();
        this.lastTarget = target;
        this.lastChannel = channel;
        String deliveryId = "mock-" + UUID.randomUUID().toString().substring(0, 8);

        // 核心安全红线：日志中严禁输出验证码明文，仅输出脱敏后的目标与渠道
        log.info("[MOCK外发通道] 模拟外发验证码已生成并派发: channel={}, target={}, deliveryId={}",
                channel, maskTarget(target), deliveryId);

        return DeliveryResult.success(PROVIDER_NAME, deliveryId);
    }

    public int getSentCount() {
        return sentCount.get();
    }

    public String getLastTarget() {
        return lastTarget;
    }

    public DeliveryChannel getLastChannel() {
        return lastChannel;
    }

    public void reset() {
        sentCount.set(0);
        lastTarget = null;
        lastChannel = null;
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
