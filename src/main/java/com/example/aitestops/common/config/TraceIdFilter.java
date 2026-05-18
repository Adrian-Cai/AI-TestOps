package com.example.aitestops.common.config;

import com.example.aitestops.common.util.TraceIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为每个 HTTP 请求分配 traceId，便于压测失败样本和服务端日志对齐。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = TraceIdUtil.normalizeExternalTraceId(request.getHeader(TraceIdUtil.HEADER_NAME));
        MDC.put(TraceIdUtil.MDC_KEY, traceId);
        response.setHeader(TraceIdUtil.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIdUtil.MDC_KEY);
        }
    }
}
