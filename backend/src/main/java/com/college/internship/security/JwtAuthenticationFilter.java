package com.college.internship.security;

import com.college.internship.entity.SysUser;
import com.college.internship.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器与全端退出 token_version 版本校验
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserMapper sysUserMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Claims claims = jwtTokenProvider.parseClaims(token);

            if (claims != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Long userId = claims.get("userId", Long.class);
                Long tokenVersionInClaim = claims.get("tokenVersion", Long.class);
                String username = claims.getSubject();
                String roleCode = claims.get("roleCode", String.class);
                String userType = claims.get("userType", String.class);

                // 查询数据库核对 token_version (全端退出与改密即时失效核心机制)
                SysUser user = sysUserMapper.selectById(userId);
                if (user != null && user.getStatus() == 1 && user.getIsDeleted() == 0) {
                    if (tokenVersionInClaim != null && tokenVersionInClaim.equals(user.getTokenVersion())) {
                        List<String> roles = sysUserMapper.selectRoleCodesByUserId(userId);
                        if (!roles.contains(roleCode) && StringUtils.hasText(roleCode)) {
                            roles.add(roleCode);
                        }

                        LoginUser loginUser = LoginUser.builder()
                                .userId(user.getId())
                                .username(user.getUsername())
                                .realName(user.getRealName())
                                .userType(userType)
                                .deptId(user.getDeptId())
                                .tokenVersion(user.getTokenVersion())
                                .permissions(roles)
                                .build();

                        List<SimpleGrantedAuthority> authorities = roles.stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList();

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        log.warn("用户 [{}] Token版本不一致 (Claim版本: {}, DB版本: {})，拒绝访问", 
                                username, tokenVersionInClaim, user.getTokenVersion());
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
