package com.college.internship.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JJWT 0.12.x 令牌签发与校验组件
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${security.jwt.secret-key:}")
    private String configuredSecretKey;

    @Value("${security.jwt.expiration-seconds:7200}")
    private Long expirationSeconds;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        // 如果环境变量未注入，采用安全开发临时兜底密钥
        String keyStr = StringUtils.hasText(configuredSecretKey) 
                ? configuredSecretKey 
                : "college-internship-default-dev-secret-key-at-least-256-bits-length-2026";
        this.secretKey = Keys.hmacShaKeyFor(keyStr.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 JWT 令牌
     *
     * @param userId       用户ID
     * @param username     登录名
     * @param userType     用户类型
     * @param roleCode     角色标识
     * @param tokenVersion Token版本号
     * @return 签名后的 JWT 字符串
     */
    public String createToken(Long userId, String username, String userType, String roleCode, Long tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationSeconds * 1000);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("userType", userType);
        claims.put("roleCode", roleCode);
        claims.put("tokenVersion", tokenVersion);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验 Claims
     */
    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 签名解析或过期异常: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) {
            return false;
        }
        return claims.getExpiration().after(new Date());
    }

    public Long getExpirationSeconds() {
        return expirationSeconds;
    }
}
