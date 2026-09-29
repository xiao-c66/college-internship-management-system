package com.college.internship.delivery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码外发交付结果 (绝不包含验证码明文)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryResult {
    private boolean success;
    private String providerName;
    private String message;
    private String deliveryId;

    public static DeliveryResult success(String providerName, String deliveryId) {
        return DeliveryResult.builder()
                .success(true)
                .providerName(providerName)
                .deliveryId(deliveryId)
                .message("交付成功")
                .build();
    }

    public static DeliveryResult failure(String providerName, String message) {
        return DeliveryResult.builder()
                .success(false)
                .providerName(providerName)
                .message(message)
                .build();
    }
}
