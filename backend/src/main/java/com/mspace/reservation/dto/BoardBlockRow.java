package com.mspace.reservation.dto;

import com.mspace.reservation.domain.ReservationStatus;
import java.time.Instant;

/**
 * 현황판 조회용 프로젝션: 방·시간·상태만 읽어요 (예약자 컬럼은 SELECT하지 않아요, BD-02).
 * JPQL select new가 넘기는 값이 래퍼 타입이라 keyRequired를 Boolean으로 받아요.
 */
public record BoardBlockRow(Long roomId, Instant startAt, Instant endAt, ReservationStatus status, Boolean keyRequired) {
}
