package com.mspace.reservation.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/board — 현황판 (BD-01~05). 비로그인도 보는 화면이라 **예약자 정보 필드가 아예 없어요** (BD-02, NF-01).
 * 비어 있는 시간은 화면이 blocks로 계산해요.
 */
public record BoardResponse(
        LocalDate date,
        boolean closed,
        OffsetDateTime openAt,
        OffsetDateTime closeAt,
        String periodName,
        boolean keyRequired,
        List<BoardRoom> rooms
) {
    public record BoardRoom(Long id, String name, int capacity, boolean active, List<Block> blocks) {
    }

    /** status: RESERVED | IN_USE | OVERDUE */
    public record Block(OffsetDateTime startAt, OffsetDateTime endAt, String status) {
    }
}
