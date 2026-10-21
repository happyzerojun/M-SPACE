package com.mspace.admin.dto;

import com.mspace.global.util.KstTime;
import java.time.Instant;
import java.time.OffsetDateTime;

/** 이용 제한 (NS-02, NS-03). 관리자만 봐요 */
public record RestrictionResponse(
        Long id,
        Long userId,
        String studentNo,
        String studentName,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String reason,
        boolean active,
        OffsetDateTime liftedAt,
        String liftReason
) {
    public static RestrictionResponse of(Long id, Long userId, String studentNo, String studentName, Instant startAt,
                                         Instant endAt, String reason, Instant liftedAt, String liftReason, Instant now) {
        boolean active = liftedAt == null && !now.isBefore(startAt) && now.isBefore(endAt);
        return new RestrictionResponse(id, userId, studentNo, studentName, KstTime.of(startAt), KstTime.of(endAt), reason,
                active, KstTime.of(liftedAt), liftReason);
    }
}
