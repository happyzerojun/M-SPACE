package com.mspace.reservation.domain;

import com.mspace.global.entity.BaseTimeEntity;
import com.mspace.global.error.BusinessException;
import com.mspace.global.error.ErrorCode;
import com.mspace.key.domain.KeyLog;
import com.mspace.policy.domain.OperationPolicy;
import com.mspace.room.domain.Room;
import com.mspace.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예약 (RV-*, KY-*, NS-01, OP-*). 상태는 아래 전이 메서드로만 바꿔요(setter 없음, docs/예약-상태-설계.md 3장).
 *
 * <p>동시성: 같은 방·같은 시간 겹침은 DB EXCLUDE 제약이, 같은 예약을 동시에 바꾸는 경우(키 지급 vs 노쇼 스케줄러)는
 * {@code version}(낙관적 락)이 막아요. 진 쪽은 {@code CONCURRENT_UPDATE}(409)이고 스케줄러는 다음 주기에 다시 판단해요.
 * 시각 {@code now}는 인자로 받아요 → 테스트에서 Clock으로 고정할 수 있게요.
 */
@Getter
@Entity
@Table(name = "reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private short headcount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    /** 예약을 만들 때 그날 운영 기간으로 정해요. false면 개방 기간 예약: 키 대신 앱 체크인 (D-9) */
    @Column(name = "key_required", nullable = false)
    private boolean keyRequired;

    @Column(name = "key_issued_at")
    private Instant keyIssuedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "returned_late")
    private Boolean returnedLate;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** 취소한 사람 (본인 또는 관리자) */
    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "no_show_at")
    private Instant noShowAt;

    @Version
    @Column(nullable = false)
    private Long version;

    /** 신청 즉시 확정이에요(승인 대기 없음, D-3). 입력 검사는 서비스가 설계서 5장 순서로 끝낸 뒤 불러요 */
    public static Reservation reserve(Room room, User user, Instant startAt, Instant endAt, int headcount, boolean keyRequired) {
        Reservation r = new Reservation();
        r.room = room;
        r.user = user;
        r.startAt = startAt;
        r.endAt = endAt;
        r.headcount = (short) headcount;
        r.status = ReservationStatus.RESERVED;
        r.keyRequired = keyRequired;
        return r;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    /** 화면 표시 전용 연체: 키 예약이 이용 중인데 종료 시각이 지났어요 (설계서 1장). 개방 기간 예약은 연체가 없어요 */
    public boolean isOverdue(Instant now) {
        return status == ReservationStatus.IN_USE && keyRequired && !now.isBefore(endAt);
    }

    // ───────────── 상태 전이 (설계서 3장) ─────────────

    /**
     * 키 지급 (KY-01, KY-02): 실물 학생증 확인 후, 시작 N분 전부터 노쇼 판정 전까지.
     * @return 쌓을 key_log (서비스가 저장해요)
     */
    public KeyLog issueKey(Long adminId, boolean identityVerified, String memo, Instant now, OperationPolicy policy) {
        requireStatus(ReservationStatus.RESERVED);
        requireKeyRequired(true);
        if (!identityVerified) {
            throw new BusinessException(ErrorCode.IDENTITY_NOT_VERIFIED);
        }
        if (!isInUseWindow(now, policy)) {
            throw new BusinessException(ErrorCode.KEY_ISSUE_NOT_ALLOWED_TIME,
                    policy.getKeyIssueBeforeMinutes(), policy.getNoShowGraceMinutes());
        }
        this.status = ReservationStatus.IN_USE;
        this.keyIssuedAt = now;
        return KeyLog.issue(id, adminId, memo, now);
    }

    /** 반납 확인 (KY-03). 종료 시각이 지나서 반납하면 연체로 기록해요 */
    public KeyLog returnKey(Long adminId, String memo, Instant now) {
        requireStatus(ReservationStatus.IN_USE);
        requireKeyRequired(true);
        boolean late = now.isAfter(endAt);
        this.status = ReservationStatus.RETURNED;
        this.returnedAt = now;
        this.returnedLate = late;
        return KeyLog.returned(id, adminId, late, memo, now);
    }

    /** 개방 기간 셀프 체크인 (OP-02). 본인 확인은 서비스가 먼저 해요 */
    public void selfCheckIn(Instant now, OperationPolicy policy) {
        requireKeyRequired(false);
        requireStatus(ReservationStatus.RESERVED);
        if (!isInUseWindow(now, policy)) {
            throw new BusinessException(ErrorCode.CHECK_IN_NOT_ALLOWED_TIME,
                    policy.getKeyIssueBeforeMinutes(), policy.getNoShowGraceMinutes());
        }
        this.status = ReservationStatus.IN_USE;
        this.checkedInAt = now;
    }

    /** 개방 기간 이용 종료 (OP-03) */
    public void finishBySelf(Instant now) {
        requireKeyRequired(false);
        requireStatus(ReservationStatus.IN_USE);
        this.status = ReservationStatus.COMPLETED;
        this.completedAt = now;
    }

    /** 개방 기간 예약이 종료 시각까지 이용 종료를 안 누르면 스케줄러가 끝내요 (OP-03). 종료 시각으로 기록해요 */
    public void autoComplete(Instant now) {
        requireKeyRequired(false);
        requireStatus(ReservationStatus.IN_USE);
        if (now.isBefore(endAt)) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_STATE);
        }
        this.status = ReservationStatus.COMPLETED;
        this.completedAt = endAt;
    }

    /** 본인 취소 (RV-10): 시작 전에만. 이용 중 예약은 반납·이용 종료로 끝내요 */
    public void cancelByOwner(Long userId, Instant now) {
        requireStatus(ReservationStatus.RESERVED);
        if (!now.isBefore(startAt)) {
            throw new BusinessException(ErrorCode.CANCEL_AFTER_START);
        }
        cancel(userId, null, now);
    }

    /** 관리자 취소 (AD-03): 사유 필수. 시작 후라도 키를 받기 전(RESERVED)이면 취소할 수 있어요 */
    public void cancelByAdmin(Long adminId, String reason, Instant now) {
        requireStatus(ReservationStatus.RESERVED);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        cancel(adminId, reason.strip(), now);
    }

    /** 노쇼 (NS-01): 시작 후 노쇼 기준 시간이 지나도록 키를 안 받았거나 체크인을 안 했어요 */
    public void markNoShow(Instant now, OperationPolicy policy) {
        requireStatus(ReservationStatus.RESERVED);
        if (now.isBefore(startAt.plus(policy.noShowGrace()))) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_STATE);
        }
        this.status = ReservationStatus.NO_SHOW;
        this.noShowAt = now;
    }

    private void cancel(Long by, String reason, Instant now) {
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancelledBy = by;
        this.cancelReason = reason;
    }

    /** 키 지급·체크인 가능 시간: 시작 N분 전 ≤ now < 시작 + 노쇼 기준 (설계서 3장) */
    private boolean isInUseWindow(Instant now, OperationPolicy policy) {
        return !now.isBefore(startAt.minus(policy.keyIssueBefore())) && now.isBefore(startAt.plus(policy.noShowGrace()));
    }

    private void requireStatus(ReservationStatus expected) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_STATE);
        }
    }

    /** 키 예약에 셀프 체크인, 개방 기간 예약에 키 지급을 하면 INVALID_RESERVATION_STATE예요 (설계서 3장) */
    private void requireKeyRequired(boolean expected) {
        if (keyRequired != expected) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_STATE);
        }
    }
}
