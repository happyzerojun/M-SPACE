package com.mspace;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 테스트용 시계: 노쇼·키 지급 가능 시간처럼 시각에 따라 결과가 달라지는 규칙을 시각을 고정해 검증해요.
 * 기본은 실제 시각이고, set()으로 고정하면 그 시각에 멈춰요. 테스트가 끝나면 IntegrationTest가 reset()해요.
 *
 * <p>주의: JWT 검증은 실제 시각으로 해서, 고정 시각은 실제 시각보다 미래로만 옮겨요(과거로 옮긴 뒤 만든 토큰은 이미 만료예요).
 */
public class MutableClock extends Clock {

    private volatile Instant fixed;

    public void set(Instant instant) {
        this.fixed = instant;
    }

    public void reset() {
        this.fixed = null;
    }

    @Override
    public Instant instant() {
        Instant f = fixed;
        return f != null ? f : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
