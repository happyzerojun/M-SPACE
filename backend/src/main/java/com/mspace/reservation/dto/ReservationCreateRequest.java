package com.mspace.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * POST /api/reservations (RV-01). 시각은 "2026-10-13T14:00:00+09:00"처럼 받아요.
 * 30분 단위·종료 > 시작은 서비스가 검사해서 같은 fieldErrors 형식으로 알려줘요 (설계서 5장 1번).
 */
public record ReservationCreateRequest(
        @NotNull(message = "방을 골라 주세요.")
        Long roomId,

        @NotNull(message = "시작 시각을 골라 주세요.")
        OffsetDateTime startAt,

        @NotNull(message = "종료 시각을 골라 주세요.")
        OffsetDateTime endAt,

        @NotNull(message = "이용 인원을 입력해 주세요.")
        @Min(value = 1, message = "이용 인원은 1명 이상이에요.")
        @Max(value = 100, message = "이용 인원을 확인해 주세요.")
        Integer headcount
) {
}
