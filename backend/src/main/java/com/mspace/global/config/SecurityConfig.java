package com.mspace.global.config;

import com.mspace.global.security.JwtAuthenticationFilter;
import com.mspace.global.security.JwtProperties;
import com.mspace.global.security.JwtTokenProvider;
import com.mspace.global.security.SecurityErrorHandler;
import com.mspace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity   // 서비스·컨트롤러의 @PreAuthorize를 켜요
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final SecurityErrorHandler securityErrorHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 토큰을 쿠키가 아니라 Authorization 헤더로 보내서 브라우저가 자동으로 실어 보내지 않아요 → CSRF가 성립하지 않아서 꺼요
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)   // 로그아웃은 프론트엔드가 토큰을 지워요 (AU-04)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .authorizeHttpRequests(auth -> auth
                        // 비회원 허용: 가입·로그인, 현황판·방 목록·예약 규칙 (API 명세서 1~2장)
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/board", "/api/rooms", "/api/policy").permitAll()
                        // 예외가 나면 Spring이 /error로 보내요. 막혀 있으면 진짜 에러가 401로 가려져요
                        .requestMatchers("/error").permitAll()
                        // 관리자 API는 한곳에 모아 ADMIN만 (AD-01)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 나머지 API는 로그인 필요. 본인 예약인지는 서비스에서 다시 확인해요 (RV-09)
                        .requestMatchers("/api/**").authenticated()
                        // API가 아닌 주소는 React 화면이라 누구나 받을 수 있어요
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 비밀번호 단방향 해시 (NF-03) */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
