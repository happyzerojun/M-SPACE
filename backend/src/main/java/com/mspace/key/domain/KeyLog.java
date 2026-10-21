package com.mspace.key.domain;

import com.mspace.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 키 지급·반납 이력 (KY-05). 추가만 해요: DB 트리거(tr_key_log_immutable)가 수정·삭제를 막아서, setter도 두지 않아요.
 * 같은 예약에 지급·반납이 두 번 기록되지 않게 UNIQUE(reservation_id, action)가 걸려 있어요(동시 클릭 방지).
 */
@Getter
@Entity
@Table(name = "key_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeyLog extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private KeyAction action;

    /** 처리한 관리자 (조교·근로학생) */
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    /** 실물 학생증으로 본인 확인했는지. 지급은 반드시 true예요(ck_key_log_issue_verified) */
    @Column(name = "identity_verified", nullable = false)
    private boolean identityVerified;

    /** 반납이 종료 시각보다 늦었는지 */
    @Column(nullable = false)
    private boolean late;

    @Column(length = 200)
    private String memo;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static KeyLog issue(Long reservationId, Long adminId, String memo, Instant now) {
        return of(reservationId, KeyAction.ISSUE, adminId, true, false, memo, now);
    }

    /** 반납 때는 학생증을 다시 확인하지 않아서 identityVerified는 false로 남겨요 */
    public static KeyLog returned(Long reservationId, Long adminId, boolean late, String memo, Instant now) {
        return of(reservationId, KeyAction.RETURN, adminId, false, late, memo, now);
    }

    private static KeyLog of(Long reservationId, KeyAction action, Long adminId, boolean identityVerified, boolean late,
                             String memo, Instant now) {
        KeyLog log = new KeyLog();
        log.reservationId = reservationId;
        log.action = action;
        log.adminId = adminId;
        log.identityVerified = identityVerified;
        log.late = late;
        log.memo = memo == null || memo.isBlank() ? null : memo.strip();
        log.occurredAt = now;
        return log;
    }
}
