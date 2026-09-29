package com.college.internship.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 首次改密与测试沙箱白名单策略安全管理器 (API-125 / 默认 Fail-Close 阻断)
 * <p>
 * 规则：
 * 1. 生产环境安全锁定：处于生产环境 (含 prod/production profile、缺省 profile 或非 dev/test profile) 时，
 *    即使误设了白名单或误设 forcePasswordChange=false，也必须 Fail-Close 强阻断，绝不允许跳过；
 * 2. 仅在明确的开发/独立测试环境 (dev 或 test profile) 且 forcePasswordChange=false 时，
 *    且账号名明确在白名单配置中，才允许跳过改密；
 * 3. 非白名单账号即使在测试环境中也一律执行 Fail-Close 强阻断 (403)；
 * 4. 白名单严禁硬编码在生产代码中，必须通过外部配置项或环境变量注入。
 */
@Slf4j
@Component
public class PasswordPolicyManager {

    @Autowired(required = false)
    private Environment environment;

    private Boolean productionOverride = null;

    /**
     * 首次登录强制改密全局开关 (生产环境默认 true；配置缺失或解析异常时强制 fail-close 开启阻断)
     */
    @Value("${system.security.force-password-change:true}")
    private boolean forcePasswordChange = true;

    /**
     * 独立测试沙箱专用测试账号白名单 (仅在 forcePasswordChange=false 且非生产环境时生效)
     */
    private Set<String> testSkippableUsernames = Collections.emptySet();

    @Value("${system.security.test-skippable-usernames:}")
    public void setTestSkippableUsernames(String usernamesStr) {
        if (StringUtils.hasText(usernamesStr)) {
            this.testSkippableUsernames = Arrays.stream(usernamesStr.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toUnmodifiableSet());
        } else {
            this.testSkippableUsernames = Collections.emptySet();
        }
    }

    public void setForcePasswordChange(boolean forcePasswordChange) {
        this.forcePasswordChange = forcePasswordChange;
    }

    public void setTestSkippableUsernamesSet(Set<String> usernames) {
        this.testSkippableUsernames = (usernames != null) ? Set.copyOf(usernames) : Collections.emptySet();
    }

    public void setProductionOverride(Boolean productionOverride) {
        this.productionOverride = productionOverride;
    }

    /**
     * 判定当前运行态是否属于生产环境
     * 采用保守 Fail-Close 策略：缺省或未明确声明为 dev/test 一律判定为生产环境
     */
    public boolean isProductionEnvironment() {
        if (productionOverride != null) {
            return productionOverride;
        }
        if (environment == null) {
            // 未注入环境对象时默认视为生产环境 (Fail-Close)
            return true;
        }
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles == null || activeProfiles.length == 0) {
            // 无 active profile (默认环境) 视为生产环境，防范默认未配置泄露
            return true;
        }
        boolean hasDevOrTest = false;
        for (String profile : activeProfiles) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                // 显式声明为 prod/production，坚决判定为生产环境
                return true;
            }
            if ("dev".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile)) {
                hasDevOrTest = true;
            }
        }
        return !hasDevOrTest;
    }

    /**
     * 判定指定用户是否被强制阻断必须改密 (默认 Fail-Close 机制)
     *
     * @param username 用户名
     * @param userStatus 用户状态 (2: 待首次改密)
     * @return true: 必须强制改密 (返回 403 阻断业务访问)；false: 允许跳过 (仅限于测试沙箱白名单账号)
     */
    public boolean isMandatoryForceChange(String username, Integer userStatus) {
        // 非待改密状态账号无需阻断
        if (userStatus == null || userStatus != 2) {
            return false;
        }

        // 生产环境安全绝对防御：处于生产环境时，即便误设了白名单或误设了 forcePasswordChange=false，也必须 Fail-Close 强阻断！
        if (isProductionEnvironment()) {
            log.debug("生产运行环境检测生效：用户 [{}] 强制首次改密，拒绝任何白名单跳过", username);
            return true;
        }

        // 配置显式要求强制改密 -> 全量账号强制改密，绝不跳过
        if (forcePasswordChange) {
            return true;
        }

        // 仅在明确的开发/测试环境 (dev 或 test profile) 且 forcePasswordChange=false 时，严格匹配白名单测试账号
        if (StringUtils.hasText(username) && testSkippableUsernames != null && !testSkippableUsernames.isEmpty()
                && testSkippableUsernames.contains(username.trim())) {
            log.info("测试沙箱白名单账号 [{}] 命中跳过首次改密策略", username);
            return false;
        }

        // 非白名单账号或白名单为空，一律 Fail-Close 强阻断
        log.warn("用户 [{}] 处于待改密状态且不在测试白名单中，执行 Fail-Close 403 强阻断", username);
        return true;
    }

    public boolean isForcePasswordChangeEnabled() {
        return forcePasswordChange;
    }

    public Set<String> getTestSkippableUsernames() {
        return testSkippableUsernames;
    }
}
