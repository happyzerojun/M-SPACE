package com.mspace.global.jpa;

import com.mspace.global.entity.BaseTimeEntity;
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
 * 테스트 전용: reservation 테이블을 JPA로 저장해 보는 최소 매핑이에요 (TS-15).
 * 예약 엔티티·서비스(3단계)가 아직 없어서, EXCLUDE 위반이 Hibernate 경로에서도 409로 바뀌는지 먼저 확인하려고 만들었어요.
 * 상태 전이 같은 도메인 규칙은 넣지 않아요. 3단계에서 진짜 Reservation 엔티티가 생기면 그걸로 바꾸고 이 클래스는 지워요.
 * ddl-auto: validate가 이 매핑도 V1 스키마와 맞는지 검사해요.
 */
@Getter
@Entity(name = "ReservationRow")
@Table(name = "reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationRow extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private short headcount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "key_required", nullable = false)
    private boolean keyRequired;

    /** 키가 필요한 RESERVED 예약 (EXCLUDE 제약의 대상 상태) */
    public static ReservationRow reserved(Long roomId, Long userId, Instant startAt, Instant endAt) {
        ReservationRow row = new ReservationRow();
        row.roomId = roomId;
        row.userId = userId;
        row.startAt = startAt;
        row.endAt = endAt;
        row.headcount = 2;
        row.status = "RESERVED";
        row.keyRequired = true;
        return row;
    }
}
