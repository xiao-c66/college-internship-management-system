package com.college.internship.delivery;

import com.college.internship.common.BusinessException;
import com.college.internship.config.NotificationDeliveryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证码外发适配层与外发渠道纯内存隔离单元测试 (不触发真实外发网关，不连接数据库)
 */
class NotificationDeliveryIsolatedUnitTest {

    private NotificationDeliveryProperties properties;
    private MockVerificationCodeProvider mockProvider;
    private VerificationCodeDeliveryServiceImpl deliveryService;

    @BeforeEach
    void setUp() {
        properties = new NotificationDeliveryProperties();
        mockProvider = new MockVerificationCodeProvider();
        mockProvider.reset();
        deliveryService = new VerificationCodeDeliveryServiceImpl(properties, mockProvider);
    }

    @Test
    @DisplayName("Mock Provider: 成功模拟发送，安全记录并绝不泄露验证码")
    void testMockProvider_Success_AndZeroCodeLeakage() {
        DeliveryResult smsResult = mockProvider.deliver(DeliveryChannel.SMS, "13800138000", "123456");
        assertTrue(smsResult.isSuccess());
        assertEquals(MockVerificationCodeProvider.PROVIDER_NAME, smsResult.getProviderName());
        assertNotNull(smsResult.getDeliveryId());
        assertEquals(1, mockProvider.getSentCount());
        assertEquals("13800138000", mockProvider.getLastTarget());
        assertEquals(DeliveryChannel.SMS, mockProvider.getLastChannel());

        DeliveryResult emailResult = mockProvider.deliver(DeliveryChannel.EMAIL, "student@college.edu.cn", "654321");
        assertTrue(emailResult.isSuccess());
        assertEquals(2, mockProvider.getSentCount());
        assertEquals("student@college.edu.cn", mockProvider.getLastTarget());
        assertEquals(DeliveryChannel.EMAIL, mockProvider.getLastChannel());

        // 验证返回结果中绝不携带验证码明文
        assertFalse(smsResult.getMessage().contains("123456"));
        assertFalse(emailResult.getMessage().contains("654321"));
    }

    @Test
    @DisplayName("生产安全失败: 未配置真实服务商时安全失败(503)，杜绝向客户端假报成功")
    void testDeliveryService_UnconfiguredProvider_FailsSafely_Throws503() {
        // 默认状态 provider = "none"
        properties.getSms().setProvider("none");
        properties.getEmail().setProvider("none");

        // 短信通道未配置安全失败
        BusinessException smsEx = assertThrows(BusinessException.class, () ->
                deliveryService.sendCode(DeliveryChannel.SMS, "13911112222", "888888"));
        assertEquals(503, smsEx.getCode());
        assertTrue(smsEx.getMessage().contains("系统短信外发通道未配置"));
        assertFalse(smsEx.getMessage().contains("888888"), "异常信息严禁泄露验证码");

        // 邮件通道未配置安全失败
        BusinessException emailEx = assertThrows(BusinessException.class, () ->
                deliveryService.sendCode(DeliveryChannel.EMAIL, "user@test.com", "999999"));
        assertEquals(503, emailEx.getCode());
        assertTrue(emailEx.getMessage().contains("系统邮件外发通道未配置"));
        assertFalse(emailEx.getMessage().contains("999999"), "异常信息严禁泄露验证码");

        // 确保 mockProvider 未被意外调用
        assertEquals(0, mockProvider.getSentCount());
    }

    @Test
    @DisplayName("凭据校验安全失败: 配置真实服务商但缺失 AccessKey/SecretKey 时安全失败(503)")
    void testDeliveryService_RealProviderWithoutCredentials_FailsSafely_Throws503() {
        // 短信配置为真实服务商，但未注入密钥
        properties.getSms().setProvider("aliyun");
        properties.getSms().setAccessKey(null);
        properties.getSms().setSecretKey("");

        BusinessException smsEx = assertThrows(BusinessException.class, () ->
                deliveryService.sendCode(DeliveryChannel.SMS, "13800001111", "555555"));
        assertEquals(503, smsEx.getCode());
        assertTrue(smsEx.getMessage().contains("系统短信服务商凭据未完整配置"));
        assertFalse(smsEx.getMessage().contains("555555"));

        // 邮件配置为 SMTP，但未注入主机与账户
        properties.getEmail().setProvider("smtp");
        properties.getEmail().setHost(null);

        BusinessException emailEx = assertThrows(BusinessException.class, () ->
                deliveryService.sendCode(DeliveryChannel.EMAIL, "admin@school.cn", "666666"));
        assertEquals(503, emailEx.getCode());
        assertTrue(emailEx.getMessage().contains("系统邮件服务商凭据未完整配置"));
        assertFalse(emailEx.getMessage().contains("666666"));
    }

    @Test
    @DisplayName("开发/测试模式: 配置为 mock 时顺利调用 mock provider")
    void testDeliveryService_MockMode_Success() {
        properties.getSms().setProvider("mock");
        properties.getEmail().setProvider("mock");

        DeliveryResult smsRes = deliveryService.sendCode(DeliveryChannel.SMS, "13500002222", "112233");
        assertTrue(smsRes.isSuccess());
        assertEquals(MockVerificationCodeProvider.PROVIDER_NAME, smsRes.getProviderName());
        assertEquals(1, mockProvider.getSentCount());

        DeliveryResult emailRes = deliveryService.sendCode(DeliveryChannel.EMAIL, "tester@test.com", "445566");
        assertTrue(emailRes.isSuccess());
        assertEquals(MockVerificationCodeProvider.PROVIDER_NAME, emailRes.getProviderName());
        assertEquals(2, mockProvider.getSentCount());
    }

    @Test
    @DisplayName("配置就绪状态检查: isConfigured 准确认定 mock 与完整凭据状态")
    void testDeliveryService_IsConfigured() {
        // 初始状态
        properties.getSms().setProvider("none");
        assertFalse(deliveryService.isConfigured(DeliveryChannel.SMS));

        // mock 状态视为开发测试可用
        properties.getSms().setProvider("mock");
        assertTrue(deliveryService.isConfigured(DeliveryChannel.SMS));

        // 真实服务商缺少凭据
        properties.getSms().setProvider("aliyun");
        properties.getSms().setAccessKey(null);
        assertFalse(deliveryService.isConfigured(DeliveryChannel.SMS));

        // 真实服务商凭据齐全
        properties.getSms().setAccessKey("AK_TEST");
        properties.getSms().setSecretKey("SK_TEST");
        assertTrue(deliveryService.isConfigured(DeliveryChannel.SMS));
    }
}
