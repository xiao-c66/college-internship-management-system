package com.college.internship.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 首次改密策略与测试沙箱白名单独立测试
 * 包含：生产环境强制阻断(即使误设白名单或开关)、配置缺失Fail-Close、白名单空/全空格/非白名单账号阻断、开发测试环境精准跳过
 */
class PasswordPolicyManagerTest {

    @Test
    @DisplayName("默认与缺省配置: 默认开启生产强阻断 (Fail-Close)")
    void testDefaultConfiguration_FailClose() {
        PasswordPolicyManager manager = new PasswordPolicyManager();

        assertTrue(manager.isForcePasswordChangeEnabled(), "默认必须启用 forcePasswordChange=true");
        assertTrue(manager.getTestSkippableUsernames().isEmpty(), "默认白名单必须为空");
        assertTrue(manager.isProductionEnvironment(), "未配置 profile 时默认锁定为生产环境");

        // status=2 的任意用户均返回 true (强制改密)
        assertTrue(manager.isMandatoryForceChange("admin", 2));
        assertTrue(manager.isMandatoryForceChange("student", 2));
        assertTrue(manager.isMandatoryForceChange("test_student", 2));

        // status=1 的用户无需改密
        assertFalse(manager.isMandatoryForceChange("admin", 1));
        assertFalse(manager.isMandatoryForceChange("student", null));
    }

    @Test
    @DisplayName("生产环境硬性安全防线: 生产环境即使误注入白名单且误设forcePasswordChange=false，也绝对强阻断(Fail-Close)")
    void testProductionMode_EvenIfMisconfigured_NeverSkips() {
        PasswordPolicyManager manager = new PasswordPolicyManager();
        manager.setProductionOverride(true); // 生产环境
        manager.setForcePasswordChange(false); // 人为误配为 false
        manager.setTestSkippableUsernames("test_student,teacher_qa,admin"); // 人为误配白名单

        assertTrue(manager.isMandatoryForceChange("test_student", 2), "生产环境下任何白名单账号均绝对不得跳过");
        assertTrue(manager.isMandatoryForceChange("teacher_qa", 2), "生产环境严防配置漂移，必须强制改密");
        assertTrue(manager.isMandatoryForceChange("admin", 2));
        assertTrue(manager.isMandatoryForceChange("other_user", 2));
    }

    @Test
    @DisplayName("生产环境环境对象探测: activeProfile=prod 时无视任何白名单设置")
    void testProductionMode_ViaSpringEnvironment_StrictBlocking() {
        MockEnvironment mockEnv = new MockEnvironment();
        mockEnv.setActiveProfiles("prod");

        PasswordPolicyManager manager = new PasswordPolicyManager();
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "environment", mockEnv);
        manager.setForcePasswordChange(false);
        manager.setTestSkippableUsernames("test_student");

        assertTrue(manager.isProductionEnvironment());
        assertTrue(manager.isMandatoryForceChange("test_student", 2), "prod profile 必须强制改密");
    }

    @Test
    @DisplayName("开发/测试环境配置: 仅白名单账号跳过，非白名单账号 Fail-Close 阻断")
    void testDevTestMode_OnlyWhitelistedCanSkip() {
        PasswordPolicyManager manager = new PasswordPolicyManager();
        manager.setProductionOverride(false); // 明确为开发/测试环境
        manager.setForcePasswordChange(false);
        manager.setTestSkippableUsernames("fixture_student,fixture_teacher");

        // 白名单测试账号允许跳过
        assertFalse(manager.isMandatoryForceChange("fixture_student", 2));
        assertFalse(manager.isMandatoryForceChange("fixture_teacher", 2));

        // 非白名单账号强制阻断
        assertTrue(manager.isMandatoryForceChange("normal_student", 2));
        assertTrue(manager.isMandatoryForceChange("attacker_account", 2));
        assertTrue(manager.isMandatoryForceChange("", 2));
        assertTrue(manager.isMandatoryForceChange(null, 2));
    }

    @Test
    @DisplayName("开发/测试环境特殊空值输入: 白名单为空、全空格、null时一律 Fail-Close 返回 mandatory(403)")
    void testDevTestMode_EmptyAndWhitespaceWhitelist_FailCloseMandatory() {
        PasswordPolicyManager manager = new PasswordPolicyManager();
        manager.setProductionOverride(false); // 开发/测试环境
        manager.setForcePasswordChange(false);

        // 1. 空字符串
        manager.setTestSkippableUsernames("");
        assertTrue(manager.isMandatoryForceChange("test_student", 2), "白名单为空时必须 Fail-Close 强阻断");

        // 2. 仅空格与制表符
        manager.setTestSkippableUsernames("   ,   \t,  \n  ");
        assertTrue(manager.isMandatoryForceChange("test_student", 2), "白名单仅有空格时必须 Fail-Close 强阻断");

        // 3. null 注入
        manager.setTestSkippableUsernames(null);
        assertTrue(manager.isMandatoryForceChange("test_student", 2), "白名单为 null 时必须 Fail-Close 强阻断");
    }

    @Test
    @DisplayName("白名单注入解析: 逗号分隔、多余空格与空白字符安全过滤")
    void testParseUsernames_CommaSeparated_TrimsProperly() {
        PasswordPolicyManager manager = new PasswordPolicyManager();
        manager.setTestSkippableUsernames("  user1 , user2, ,user3  ");

        assertEquals(Set.of("user1", "user2", "user3"), manager.getTestSkippableUsernames());

        manager.setTestSkippableUsernames(null);
        assertTrue(manager.getTestSkippableUsernames().isEmpty());

        manager.setTestSkippableUsernames("   ");
        assertTrue(manager.getTestSkippableUsernames().isEmpty());
    }
}
