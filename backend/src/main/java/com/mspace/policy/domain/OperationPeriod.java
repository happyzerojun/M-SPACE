package com.mspace.policy.domain;

import com.mspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 특별 운영 기간 (PO-01~03, OP-01). 기간끼리는 날짜가 겹치지 않아요(ex_operation_period_dates).
 * - closed: 휴관일
 * - keyRequired=false: 개방 기간(시험기간처럼 방을 열어두는 기간) → 키 대신 앱 체크인 (D-9)
 */
@Getter
@Entity
@Table(name = "operation_period")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperationPeriod extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean closed;

    @Column(name = "key_required", nullable = false)
    private boolean keyRequired;

    @Column(name = "open_minute")
    private Short openMinute;

    @Column(name = "close_minute")
    private Short closeMinute;
}
