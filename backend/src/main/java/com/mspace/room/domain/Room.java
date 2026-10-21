package com.mspace.room.domain;

import com.mspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 방 (AD-05). 방 이름·정원은 코드에 하드코딩하지 않고 이 테이블로 관리해요 (CLAUDE.md 2장).
 * 지우지 않고 active=false로 비활성화해요 → 지난 예약 기록이 방을 계속 가리킬 수 있게요.
 */
@Getter
@Entity
@Table(name = "room")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false)
    private short capacity;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;
}
