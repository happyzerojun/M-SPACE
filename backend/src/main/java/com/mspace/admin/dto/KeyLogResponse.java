package com.mspace.admin.dto;

import com.mspace.global.util.KstTime;
import com.mspace.key.domain.KeyAction;
import java.time.Instant;
import java.time.OffsetDateTime;

/** 키 이력 (KY-05, KY-07). 관리자만 봐요 */
public record KeyLogResponse(
        Long id,
        Long reservationId,
        String roomName,
        String studentNo,
        String studentName,
        KeyAction action,
        String adminName,
        boolean identityVerified,
        boolean late,
        String memo,
        OffsetDateTime occurredAt
) {
    /** JPQL select new 용 생성자 (Instant로 받아 KST로 바꿔요). Hibernate가 넘기는 값이 래퍼 타입이라 Boolean으로 받아요 */
    public KeyLogResponse(Long id, Long reservationId, String roomName, String studentNo, String studentName, KeyAction action,
                          String adminName, Boolean identityVerified, Boolean late, String memo, Instant occurredAt) {
        this(id, reservationId, roomName, studentNo, studentName, action, adminName, identityVerified, late, memo,
                KstTime.of(occurredAt));
    }
}
