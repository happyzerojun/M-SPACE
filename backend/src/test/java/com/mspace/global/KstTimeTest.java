package com.mspace.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.mspace.global.error.ErrorCode;
import com.mspace.global.util.KstTime;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** KST/UTC 경계와 24:00 마감 표현 (NF-06, PO-02) */
class KstTimeTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 13);

    @Test
    void 한국_0시는_전날_UTC_15시() {
        assertThat(KstTime.startOfDay(DAY)).isEqualTo(Instant.parse("2026-10-12T15:00:00Z"));
    }

    @Test
    void 마감_1440분은_다음날_0시() {
        assertThat(KstTime.atMinute(DAY, 1440)).isEqualTo(KstTime.startOfDay(DAY.plusDays(1)));
        assertThat(KstTime.formatMinute(1440)).isEqualTo("24:00");
        assertThat(KstTime.formatMinute(540)).isEqualTo("09:00");
    }

    @Test
    void 자정_직전_UTC는_한국_다음날() {
        // UTC 10/12 15:30 = KST 10/13 00:30
        assertThat(KstTime.dateOf(Instant.parse("2026-10-12T15:30:00Z"))).isEqualTo(DAY);
        assertThat(KstTime.dateOf(Instant.parse("2026-10-12T14:59:59Z"))).isEqualTo(DAY.minusDays(1));
    }

    @Test
    void 응답은_09시_오프셋() {
        assertThat(KstTime.of(Instant.parse("2026-10-13T05:00:00Z")).toString()).isEqualTo("2026-10-13T14:00+09:00");
        assertThat(KstTime.of(null)).isNull();
    }

    @Test
    void 에러_메시지_자리값_채우기() {
        assertThat(ErrorCode.OUTSIDE_OPERATING_HOURS.format("09:00", "18:00"))
                .isEqualTo("운영 시간(09:00~18:00) 안에서만 예약할 수 있어요.");
        assertThat(ErrorCode.HEADCOUNT_EXCEEDS_CAPACITY.format(4)).isEqualTo("이 방은 최대 4명까지 이용할 수 있어요.");
        assertThat(ErrorCode.LOGIN_FAILED.format()).isEqualTo(ErrorCode.LOGIN_FAILED.getMessage());
    }
}
