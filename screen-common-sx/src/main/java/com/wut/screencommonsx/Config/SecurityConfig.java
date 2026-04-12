package com.wut.screencommonsx.Config;

import com.wut.screencommonsx.Filter.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EXCLUDE_URLS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static final String[] EXCLUDE_URLS = {
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
}

