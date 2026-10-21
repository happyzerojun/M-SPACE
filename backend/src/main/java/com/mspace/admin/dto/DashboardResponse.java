package com.mspace.admin.dto;

import com.mspace.reservation.dto.ReservationResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 오늘 화면 (AD-04). counts는 그날과 겹치는 예약의 상태별 수예요(inUse는 연체를 뺀 수).
 * pendingIssue: 그날 키를 줘야 하는 예약, overdue: 날짜와 상관없이 반납이 늦은 예약.
 */
public record DashboardResponse(
        LocalDate date,
        Counts counts,
        List<ReservationResponse> pendingIssue,
        List<ReservationResponse> overdue
) {
    public record Counts(long reserved, long inUse, long overdue, long returned, long completed, long noShow, long cancelled) {
    }
}
