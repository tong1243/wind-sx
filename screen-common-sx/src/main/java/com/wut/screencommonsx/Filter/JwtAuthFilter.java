package com.wut.screencommonsx.Filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wut.screencommonsx.Response.ApiResponse;
import com.wut.screencommonsx.Util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // 排除无需认证的接口（与 SecurityConfig 中 permitAll 保持一致）
    private static final String[] EXCLUDE_URL_PATTERNS = {
            "/api/login", "/api/register", "/api/auth/**",
            "/api/v1/operation-maintenance/**",

            "/api/v1/road-statuses",
            "/api/v1/section-parameter-detections",
            "/api/v1/event-detections",
            "/api/v1/service-areas",
            "/api/v1/traffic-states",
            "/api/v1/wind-sections",
            "/api/v1/wind-speed-thresholds",
            "/api/v1/wind-speed-thresholds/**",
            "/api/v1/wind-impacts/spatiotemporal",
            "/api/v1/wind-observations",
            "/api/v1/block-duration-forecasts",

            "/api/v1/publish-facilities",
            "/api/v1/publish-facilities/**",
            "/api/v1/closure-devices",
            "/api/v1/closure-devices/**",
            "/api/v1/staff",
            "/api/v1/staff/**",
            "/api/v1/teams",
            "/api/v1/teams/**",

            "/api/v1/control-principles",
            "/api/v1/control-plans",
            "/api/v1/control-plans/**",
            "/api/v1/vms-contents",
            "/api/v1/vms-contents/**",
            "/api/v1/dispatch-plans",
            "/api/v1/dispatch-plans/**",
            "/api/v1/control-flows",
            "/api/v1/generated-control-plans",
            "/api/v1/control-plan-recommendations",
            "/api/v1/wind-events",

            "/api/v1/wind-risk-speed/**",
            "/api/v1/wind-risk-sections/**",
            "/api/v1/wind-speed-limits/**",
            "/socket/**"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isExcludedPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, 401, "未授权，请先登录");
            return;
        }

        String token = authHeader.substring(7);
        if (token.trim().isEmpty()) {
            sendError(response, 401, "Token 不能为空");
            return;
        }

        if (!jwtUtil.isValid(token)) {
            sendError(response, 401, "Token 过期或无效，请重新登录");
            return;
        }

        String phone = jwtUtil.getPhoneFromToken(token);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(phone, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        chain.doFilter(request, response);
    }

    private boolean isExcludedPath(String path) {
        for (String pattern : EXCLUDE_URL_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private void sendError(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, msg));
    }
}
