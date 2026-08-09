package com.example.management.auth.config;

import com.example.management.auth.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * REST API 전용 stateless 설정. 세션/폼로그인 없이 access token(JWT)만으로 인증한다.
 * 아래 3개 경로는 로그인 전이거나(카카오 로그인) refresh token을 body로 직접 검증하므로
 * (logout/reissue) access token 없이도 접근 가능해야 한다 — 나중에 /api/auth 아래
 * 인증이 필요한 엔드포인트(예: /api/auth/me)가 추가돼도 조용히 permitAll을 상속하지 않도록
 * 전체 prefix가 아닌 경로 3개를 명시한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/kakao/login", "/api/auth/logout", "/api/auth/reissue").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpStatus.UNAUTHORIZED.value())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
