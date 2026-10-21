package com.mspace;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 서비스가 주입받는 Clock을 테스트에서 옮길 수 있는 시계로 바꿔요 (ClockConfig의 시스템 시계보다 우선) */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }
}
