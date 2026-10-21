package com.mspace.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 노쇼 처리 같은 주기 작업을 켜요. 테스트 프로필은 mspace.scheduler.enabled=false로 실행만 건너뛰어요 (ReservationScheduler) */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
