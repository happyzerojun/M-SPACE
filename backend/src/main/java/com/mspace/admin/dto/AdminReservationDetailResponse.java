package com.mspace.admin.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.mspace.reservation.dto.ReservationResponse;
import java.util.List;

/** GET /api/admin/reservations/{id}: ReservationResponse 필드에 keyLogs를 더해 한 단계로 펼쳐요 (API 명세서 4.1) */
public record AdminReservationDetailResponse(
        @JsonUnwrapped ReservationResponse reservation,
        List<KeyLogResponse> keyLogs
) {
}
