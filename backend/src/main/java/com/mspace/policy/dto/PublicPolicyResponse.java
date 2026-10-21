package com.mspace.policy.dto;

import com.mspace.policy.domain.OperationPolicy;

/** GET /api/policy — 예약 규칙 안내 (API 명세서 2장). 예약 화면·예약 완료 화면의 안내 문구에 써요 */
public record PublicPolicyResponse(
        int slotMinutes,
        int maxDurationMinutes,
        int dailyLimit,
        int bookingWindowDays,
        int noShowGraceMinutes,
        int keyIssueBeforeMinutes,
        int penaltyWindowDays,
        int penaltyThreshold,
        int penaltyDays
) {
    /** 30분 단위는 DB CHECK(ck_reservation_slot)로 고정된 값이라 정책 테이블에 없어요 (RV-04) */
    public static final int SLOT_MINUTES = 30;

    public static PublicPolicyResponse from(OperationPolicy p) {
        return new PublicPolicyResponse(SLOT_MINUTES, p.getMaxDurationMinutes(), p.getDailyLimit(), p.getBookingWindowDays(),
                p.getNoShowGraceMinutes(), p.getKeyIssueBeforeMinutes(), p.getPenaltyWindowDays(), p.getPenaltyThreshold(),
                p.getPenaltyDays());
    }
}
