package com.college.internship.util;

import com.college.internship.common.BusinessException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 外部凭据 URL 安全校验与 SSRF 深度防护工具类
 * 覆盖阶段材料、中期整改、成绩凭据、预警凭据及电子卷宗归档导出
 */
@Slf4j
public class SafeUrlValidator {

    private static final int TIMEOUT_MS = 3000;
    private static final long MAX_SINGLE_FILE_BYTES = 10 * 1024 * 1024L; // 10MB

    // 文件魔数 (Magic Numbers)
    private static final byte[] MAGIC_JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] MAGIC_PDF = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF

    /**
     * 校验 URL 协议与 SSRF 内部地址 (仅允许 http/https, DNS 解析后严格拦截私网、回环、link-local、云元数据)
     */
    public static void validateUrl(String urlStr) {
        if (!StringUtils.hasText(urlStr)) {
            return;
        }
        urlStr = urlStr.trim();
        URI uri;
        try {
            uri = URI.create(urlStr);
        } catch (Exception e) {
            throw new BusinessException(400, "URL 格式不合法: " + urlStr);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new BusinessException(400, "非法的附件凭证链接协议，仅支持 HTTP/HTTPS 协议");
        }

        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new BusinessException(400, "URL 缺少有效主机名");
        }

        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (lowerHost.equals("localhost") || lowerHost.endsWith(".localhost") || lowerHost.equals("metadata.google.internal")) {
            throw new BusinessException(400, "禁止引用本地回环或云元数据地址: " + host);
        }

        // 检查字面量 IP
        checkIpLiteral(host);

        // DNS 解析并检查所有返回的 IP 地址
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            log.debug("DNS resolution unresolvable for host: {}", host);
            return;
        }

        for (InetAddress addr : addresses) {
            checkInetAddress(addr);
        }
    }

    private static void checkIpLiteral(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            checkInetAddress(addr);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ignored) {
            // 非 IP 字面量，继续由 DNS 解析验证
        }
    }

    public static void checkInetAddress(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()) {
            throw new BusinessException(400, "禁止引用内部回环地址: " + addr.getHostAddress());
        }
        if (addr.isSiteLocalAddress()) {
            throw new BusinessException(400, "禁止引用私有内网地址 (10/8, 172.16/12, 192.168/16): " + addr.getHostAddress());
        }
        if (addr.isLinkLocalAddress()) {
            throw new BusinessException(400, "禁止引用链路本地/云元数据地址 (169.254/16): " + addr.getHostAddress());
        }
        if (addr.isMulticastAddress()) {
            throw new BusinessException(400, "禁止引用组播地址: " + addr.getHostAddress());
        }

        byte[] ip = addr.getAddress();
        if (ip.length == 4) { // IPv4
            int b0 = ip[0] & 0xFF;
            int b1 = ip[1] & 0xFF;
            // 127.0.0.0/8
            if (b0 == 127) {
                throw new BusinessException(400, "禁止引用 127.0.0.0/8 回环地址: " + addr.getHostAddress());
            }
            // 10.0.0.0/8
            if (b0 == 10) {
                throw new BusinessException(400, "禁止引用 10.0.0.0/8 私网地址: " + addr.getHostAddress());
            }
            // 172.16.0.0/12 (172.16.x.x ~ 172.31.x.x)
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) {
                throw new BusinessException(400, "禁止引用 172.16.0.0/12 私网地址: " + addr.getHostAddress());
            }
            // 192.168.0.0/16
            if (b0 == 192 && b1 == 168) {
                throw new BusinessException(400, "禁止引用 192.168.0.0/16 私网地址: " + addr.getHostAddress());
            }
            // 169.254.0.0/16
            if (b0 == 169 && b1 == 254) {
                throw new BusinessException(400, "禁止引用 169.254.0.0/16 链路本地/云元数据地址: " + addr.getHostAddress());
            }
            // 100.100.100.200 (Alibaba Cloud Metadata)
            if (b0 == 100 && b1 == 100) {
                throw new BusinessException(400, "禁止引用云服务元数据地址: " + addr.getHostAddress());
            }
        } else if (addr instanceof Inet6Address) { // IPv6
            String hostAddr = addr.getHostAddress().toLowerCase(Locale.ROOT);
            if (hostAddr.equals("0:0:0:0:0:0:0:1") || hostAddr.equals("::1") || hostAddr.startsWith("fe80:")
                    || hostAddr.startsWith("fc") || hostAddr.startsWith("fd")) {
                throw new BusinessException(400, "禁止引用 IPv6 本地回环或私有内网地址: " + hostAddr);
            }
        }
    }

    @Data
    @Builder
    public static class DownloadResult {
        private String originalUrl;
        private byte[] data;
        private String contentType;
        private String fileExtension;
        private String sha256Hex;
        private boolean success;
        private String errorMessage;
    }

    /**
     * 安全下载外部凭据资源 (单文件不超过 10MB, 超时 3 秒, 严格校验 Content-Type 与魔数)
     * 获取失败时不中断流程，返回失败详情以供存入 manifest.json
     */
    public static DownloadResult downloadSafeResource(String urlStr) {
        try {
            validateUrl(urlStr);

            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false); // 禁止自动重定向
            conn.setConnectTimeout(TIMEOUT_MS);    // 3 秒连接超时
            conn.setReadTimeout(TIMEOUT_MS);       // 3 秒读取超时
            conn.setRequestMethod("GET");
            conn.connect();

            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                throw new BusinessException(400, "禁止外部凭据链接重定向 (HTTP " + code + ")");
            }
            if (code != 200) {
                throw new BusinessException(400, "获取外部凭据失败 (HTTP " + code + ")");
            }

            String contentType = conn.getContentType();
            if (contentType != null) {
                contentType = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            }
            if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("application/pdf"))) {
                throw new BusinessException(400, "凭据 Content-Type 非法，仅允许 image/jpeg、image/png、application/pdf，当前为: " + contentType);
            }

            long contentLength = conn.getContentLengthLong();
            if (contentLength > MAX_SINGLE_FILE_BYTES) {
                throw new BusinessException(400, "凭据单文件大小超过 10MB 限制");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                long totalRead = 0;
                while ((n = in.read(buffer)) != -1) {
                    totalRead += n;
                    if (totalRead > MAX_SINGLE_FILE_BYTES) {
                        throw new BusinessException(400, "凭据单文件传输体积超出 10MB 限制");
                    }
                    baos.write(buffer, 0, n);
                }
            }

            byte[] data = baos.toByteArray();
            if (data.length < 4) {
                throw new BusinessException(400, "凭据内容过小或损坏");
            }

            // 校验文件魔数
            String ext;
            if (matchMagic(data, MAGIC_JPEG)) {
                ext = ".jpg";
            } else if (matchMagic(data, MAGIC_PNG)) {
                ext = ".png";
            } else if (matchMagic(data, MAGIC_PDF)) {
                ext = ".pdf";
            } else {
                throw new BusinessException(400, "文件魔数校验不匹配，非合规的 JPEG/PNG/PDF 格式");
            }

            // 计算 SHA-256 完整性摘要
            String sha256Hex = calculateSha256(data);

            return DownloadResult.builder()
                    .originalUrl(urlStr)
                    .data(data)
                    .contentType(contentType)
                    .fileExtension(ext)
                    .sha256Hex(sha256Hex)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.warn("Safe download skipped or failed for {}: {}", urlStr, e.getMessage());
            return DownloadResult.builder()
                    .originalUrl(urlStr)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private static boolean matchMagic(byte[] data, byte[] magic) {
        if (data.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) return false;
        }
        return true;
    }

    public static String calculateSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 摘要计算失败", e);
        }
    }
}
