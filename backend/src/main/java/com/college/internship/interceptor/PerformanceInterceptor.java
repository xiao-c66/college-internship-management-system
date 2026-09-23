package com.college.internship.interceptor;

import com.college.internship.service.IPerformanceMonitorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 接口请求耗时与性能度量拦截器
 * 拦截请求执行时间，并将度量样本推入内存有界环形缓冲区
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "_perf_req_start_time";

    private final IPerformanceMonitorService performanceMonitorService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        if (startTime != null) {
            long cost = System.currentTimeMillis() - startTime;
            String uri = request.getRequestURI();
            String method = request.getMethod();
            int status = response.getStatus();

            String operatorName = "ANONYMOUS";
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
                operatorName = auth.getName();
            }

            String clientIp = request.getHeader("X-Forwarded-For");
            if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
                clientIp = request.getRemoteAddr();
            } else if (clientIp.contains(",")) {
                clientIp = clientIp.split(",")[0].trim();
            }

            performanceMonitorService.recordRequest(uri, method, cost, status, operatorName, clientIp, null);
        }
    }
}
