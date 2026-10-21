package com.mspace.policy.service;

import com.mspace.global.util.KstTime;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 하루의 운영 시간 (PO-01, D-4). 특별 운영 기간이 있으면 그 값, 없으면 기본 운영 시간이에요.
 *
 * @param closed      휴관일이면 true (openMinute·closeMinute는 null)
 * @param periodName  특별 운영 기간 이름, 기본 운영이면 null
 * @param keyRequired false면 개방 기간: 키 대신 앱 체크인 (D-9)
 */
public record DayHours(LocalDate date, boolean closed, Integer openMinute, Integer closeMinute, String periodName,
                       boolean keyRequired) {

    public Instant openAt() {
        return closed ? null : KstTime.atMinute(date, openMinute);
    }

    /** 마감 24:00이면 다음 날 0시예요 */
    public Instant closeAt() {
        return closed ? null : KstTime.atMinute(date, closeMinute);
    }

    /** 시작·종료가 모두 이날 운영 시간 안인지 (자정을 넘길 수 없어요, 설계서 5장 5번) */
    public boolean contains(Instant startAt, Instant endAt) {
        return !closed && !startAt.isBefore(openAt()) && !endAt.isAfter(closeAt());
    }
}
