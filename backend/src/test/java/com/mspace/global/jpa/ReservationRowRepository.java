package com.mspace.global.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/** 테스트 전용 (ReservationRow 참고). 3단계 예약 리포지토리가 생기면 지워요. */
public interface ReservationRowRepository extends JpaRepository<ReservationRow, Long> {
}
