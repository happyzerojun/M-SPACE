package com.mspace.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운영 정책 (PO-05, D-4, D-5). 항상 1행(id = 1)이에요.
 * 최대 이용 시간·하루 한도·예약 가능 기간·노쇼 기준을 코드 상수로 두지 않고 이 값으로 판단해요 (CLAUDE.md 3장).
 * 운영 시간은 0시 기준 분이에요 (마감 24:00 = 1440).
 */
@Getter
@Entity
@Table(name = "operation_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperationPolicy {

    public static final short SINGLE_ID = 1;

    @Id
    private Short id;

    @Column(name = "default_open_minute", nullable = false)
    private short defaultOpenMinute;

    @Column(name = "default_close_minute", nullable = false)
    private short defaultCloseMinute;

    @Column(name = "max_duration_minutes", nullable = false)
    private short maxDurationMinutes;

    @Column(name = "daily_limit", nullable = false)
    private short dailyLimit;

    @Column(name = "booking_window_days", nullable = false)
    private short bookingWindowDays;

    /** 키 지급·셀프 체크인은 시작 이 분 전부터 받아요 (KY-02, OP-02) */
    @Column(name = "key_issue_before_minutes", nullable = false)
    private short keyIssueBeforeMinutes;

    /** 시작 후 이 분이 지나도 키를 안 받으면(체크인 안 하면) 노쇼예요 (NS-01) */
    @Column(name = "no_show_grace_minutes", nullable = false)
    private short noShowGraceMinutes;

    @Column(name = "penalty_window_days", nullable = false)
    private short penaltyWindowDays;

    @Column(name = "penalty_threshold", nullable = false)
    private short penaltyThreshold;

    @Column(name = "penalty_days", nullable = false)
    private short penaltyDays;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    public Duration keyIssueBefore() {
        return Duration.ofMinutes(keyIssueBeforeMinutes);
    }

    public Duration noShowGrace() {
        return Duration.ofMinutes(noShowGraceMinutes);
    }

    public Duration maxDuration() {
        return Duration.ofMinutes(maxDurationMinutes);
    }
}
