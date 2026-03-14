package com.wut.screencommonsx.Util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {
    @Value("${jwt.secret:screencommonsx-secret-1234567890abc}")
    private String secret;
    @Value("${jwt.expire:7200000}") // 2小时（毫秒）
    private long expireTime;

    // 生成Token（基于用户手机号）
    public String generateToken(String phone) {
        // 1. 构建JWT载荷信息
        Map<String, Object> claims = new HashMap<>();
        claims.put("phone", phone);
        
        // 2. 生成签名密钥（HMAC-SHA256）
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        
        // 3. 构建并生成Token
        return Jwts.builder()
                .setClaims(claims)          // 自定义载荷
                .setSubject(phone)         // 主题（用户标识）
                .setIssuedAt(new Date())   // 签发时间
                .setExpiration(new Date(System.currentTimeMillis() + expireTime)) // 过期时间
                .signWith(key)             // 签名（默认HS256）
                .compact();                // 生成最终Token字符串
    }

    // 解析Token获取Claims（载荷）
    public Claims parseToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
            // 移除Bearer前缀（适配HTTP请求头格式）
            String cleanToken = token.replace("Bearer ", "").trim();
            // 解析Token并返回载荷
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(cleanToken)
                    .getBody();
        } catch (Exception e) {
            // 解析失败（过期、签名错误、格式错误等）返回null
            e.printStackTrace(); // 生产环境建议替换为日志记录
            return null;
        }
    }

    // 验证Token有效性（未过期且解析成功）
    public boolean isValid(String token) {
        Claims claims = parseToken(token);
        return claims != null && !claims.getExpiration().before(new Date());
    }

    // 从Token获取手机号
    public String getPhoneFromToken(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.get("phone", String.class);
    }
}