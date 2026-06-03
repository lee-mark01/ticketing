package com.ticketing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * experiment profile 전용 보안 설정.
 * /api/experiment/** 경로를 인증 없이 허용한다.
 * 운영 profile에서는 이 빈이 등록되지 않으므로 해당 경로는 기본 authenticated 규칙 적용.
 */
@Configuration
@Profile("experiment")
public class ExperimentSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain experimentFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/experiment/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
