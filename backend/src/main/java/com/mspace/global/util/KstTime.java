package com.mspace.global.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 시각은 Instant로 저장하고, "오늘"·운영 시간 판단과 응답은 한국 시간으로 해요 (NF-06).
 * 운영 시간은 0시 기준 분(0~1440)이라 24:00 마감도 다음 날 0시로 정확히 바꿀 수 있어요.
 */
public final class KstTime {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private KstTime() {
    }

    /** 한국 날짜의 0시 (예: 2026-10-13 → 2026-10-12T15:00:00Z) */
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(KST).toInstant();
    }

    /** 한국 날짜의 0시 기준 minute분 뒤. minute=1440이면 다음 날 0시예요. */
    public static Instant atMinute(LocalDate date, int minute) {
        return startOfDay(date).plusSeconds(minute * 60L);
    }

    /** 이 시각의 한국 날짜 */
    public static LocalDate dateOf(Instant instant) {
        return instant.atZone(KST).toLocalDate();
    }

    /** 분 → "09:00", 1440 → "24:00" (화면·에러 메시지용) */
    public static String formatMinute(int minute) {
        return "%02d:%02d".formatted(minute / 60, minute % 60);
    }

    /** null이면 null을 돌려줘요 (예: 아직 반납하지 않은 예약의 returnedAt). */
    public static OffsetDateTime of(Instant instant) {
        return instant == null ? null : instant.atZone(KST).toOffsetDateTime();
    }
}
