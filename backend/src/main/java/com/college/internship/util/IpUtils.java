package com.college.internship.util;

import com.college.internship.config.SystemProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 安全客户端 IP 解析工具类
 * 严格落实可信代理边界防御：仅当直接物理连接来源 (remoteAddr) 属于受信任反向代理白名单时，
 * 才允许读取 X-Forwarded-For / X-Real-IP 等代理转发标头；
 * 否则强制采信物理连接 remoteAddr，坚决杜绝恶意客户端伪造审计 IP。
 */
@Slf4j
public final class IpUtils {

    private IpUtils() {}

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
            "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
            "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
            "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    private static final Set<String> DEFAULT_LOOPBACK_PROXIES = new HashSet<>(Arrays.asList(
            "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost"
    ));

    /**
     * 解析安全可靠的客户端真实 IP
     *
     * @param request          HTTP 请求对象
     * @param systemProperties 系统全局配置 (获取 trusted-proxies)
     * @return 经过可信代理验证与格式清洗的客户端 IP
     */
    public static String getClientIp(HttpServletRequest request, SystemProperties systemProperties) {
        if (request == null) {
            return "127.0.0.1";
        }

        String remoteAddr = request.getRemoteAddr();
        if (!StringUtils.hasText(remoteAddr)) {
            return "127.0.0.1";
        }
        remoteAddr = remoteAddr.trim();

        // 1. 获取配置的可信反向代理白名单
        Set<String> trustedProxies = getTrustedProxies(systemProperties);

        // 2. 检查直连 remoteAddr 是否为可信代理
        boolean isTrusted = trustedProxies.contains(remoteAddr.toLowerCase());
        if (!isTrusted) {
            // 直接客户端连接，非可信反向代理转发：严禁采信客户端自填标头，直接返回物理连接 IP
            return truncateIp(remoteAddr);
        }

        // 3. 直连属于可信反向代理，方可解析代理转发标头
        String ip = null;
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff) && !"unknown".equalsIgnoreCase(xff)) {
            // X-Forwarded-For 格式: client, proxy1, proxy2... 选取最左侧合法客户端IP
            String[] parts = xff.split(",");
            for (String part : parts) {
                String candidate = part.trim();
                if (isValidIp(candidate) && !"unknown".equalsIgnoreCase(candidate)) {
                    ip = candidate;
                    break;
                }
            }
        }

        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(realIp) && isValidIp(realIp.trim()) && !"unknown".equalsIgnoreCase(realIp)) {
                ip = realIp.trim();
            }
        }

        // 4. 若代理标头有效则采信，否则安全回退至 remoteAddr
        if (StringUtils.hasText(ip) && isValidIp(ip)) {
            return truncateIp(ip);
        }

        return truncateIp(remoteAddr);
    }

    /**
     * 校验 IP 字符串格式是否合法 (防止注入攻击)
     */
    public static boolean isValidIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        ip = ip.trim();
        if (IPV4_PATTERN.matcher(ip).matches()) {
            return true;
        }
        // IPv6 简单合规校验：仅包含十六进制字符与冒号，长度在合规范围内
        return ip.contains(":") && ip.matches("^[0-9a-fA-F:]+$") && ip.length() <= 45;
    }

    /**
     * 判断指定物理地址是否为受信任反向代理
     */
    public static boolean isTrustedProxy(String remoteAddr, SystemProperties systemProperties) {
        if (!StringUtils.hasText(remoteAddr)) {
            return false;
        }
        Set<String> trusted = getTrustedProxies(systemProperties);
        return trusted.contains(remoteAddr.trim().toLowerCase());
    }

    private static Set<String> getTrustedProxies(SystemProperties systemProperties) {
        Set<String> set = new HashSet<>(DEFAULT_LOOPBACK_PROXIES);
        if (systemProperties != null && systemProperties.getSecurity() != null) {
            String configProxies = systemProperties.getSecurity().getTrustedProxies();
            if (StringUtils.hasText(configProxies)) {
                for (String p : configProxies.split(",")) {
                    if (StringUtils.hasText(p)) {
                        set.add(p.trim().toLowerCase());
                    }
                }
            }
        }
        return set;
    }

    private static String truncateIp(String ip) {
        if (ip == null) {
            return "127.0.0.1";
        }
        return ip.length() > 64 ? ip.substring(0, 64) : ip;
    }
}
