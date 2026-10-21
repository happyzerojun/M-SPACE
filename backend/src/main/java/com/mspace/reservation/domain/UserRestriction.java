package com.mspace.reservation.domain;

import com.mspace.global.entity.BaseCreatedTimeEntity;
import com.mspace.global.error.BusinessException;
import com.mspace.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 노쇼 누적 이용 제한 (NS-02, NS-03). 해제되지 않은 제한끼리는 겹치지 않아요(ex_user_restriction_active)
 * → 스케줄러가 여러 번 돌거나 동시에 돌아도 제한이 하나만 생겨요.
 */
@Getter
@Entity
@Table(name = "user_restriction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRestriction extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "lifted_at")
    private Instant liftedAt;

    @Column(name = "lifted_by")
    private Long liftedBy;

    @Column(name = "lift_reason", length = 200)
    private String liftReason;

    public static UserRestriction of(Long userId, Instant startAt, Instant endAt, String reason) {
        UserRestriction r = new UserRestriction();
        r.userId = userId;
        r.startAt = startAt;
        r.endAt = endAt;
        r.reason = reason;
        return r;
    }

    public boolean isActiveAt(Instant now) {
        return liftedAt == null && !now.isBefore(startAt) && now.isBefore(endAt);
    }

    /** 관리자 해제 (NS-03). 이미 해제했으면 409 */
    public void lift(Long adminId, String reason, Instant now) {
        if (liftedAt != null) {
            throw new BusinessException(ErrorCode.INVALID_RESTRICTION_STATE);
        }
        this.liftedAt = now;
        this.liftedBy = adminId;
        this.liftReason = reason.strip();
    }
}
