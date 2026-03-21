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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    // 排除无需认证的接口
    private static final String[] EXCLUDE_URLS = {
            "/api/login", "/api/register", "/api/auth/send-code", "/api/auth/reset-password",
            "/socket/"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 跳过排除接口
        String path = request.getRequestURI();
        for (String exclude : EXCLUDE_URLS) {
            if (path.contains(exclude)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 获取 Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, 401, "未授权，请先登录");
            return;
        }

        String token = authHeader.substring(7);
        // 额外验证：token 不能为空或纯空格
        if (token.trim().isEmpty()) {
            sendError(response, 401, "Token 不能为空");
            return;
        }

        if (!jwtUtil.isValid(token)) {
            sendError(response, 401, "Token 过期或无效，请重新登录");
            return;
        }

        // 设置认证信息
        String phone = jwtUtil.getPhoneFromToken(token);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(phone, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        chain.doFilter(request, response);
    }

    // 统一返回认证错误
    private void sendError(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, msg));
    }
}
