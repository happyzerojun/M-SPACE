package com.mspace.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각은 Instant.now() 대신 이 Clock에서 읽어요.
 * 노쇼·키 지급 가능 시간처럼 시각에 따라 결과가 달라지는 규칙을 테스트에서 시각을 고정해 검증하려고요.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
