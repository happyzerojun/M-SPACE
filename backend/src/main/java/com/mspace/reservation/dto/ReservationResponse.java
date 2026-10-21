package com.mspace.reservation.dto;

import com.mspace.global.util.KstTime;
import com.mspace.reservation.domain.Reservation;
import com.mspace.reservation.domain.ReservationStatus;
import com.mspace.room.domain.Room;
import com.mspace.user.domain.User;
import com.mspace.user.domain.UserRules;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * 예약 응답 (API 명세서 0장 공통 DTO). 본인과 관리자에게만 보여줘요 — 현황판에는 이 DTO를 쓰지 않아요 (BD-02).
 * displayStatus는 status에 화면 전용 OVERDUE(연체)를 더한 값이에요.
 */
public record ReservationResponse(
        Long id,
        RoomSummary room,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        int headcount,
        ReservationStatus status,
        boolean keyRequired,
        String displayStatus,
        UserSummary user,
        OffsetDateTime keyIssuedAt,
        OffsetDateTime returnedAt,
        Boolean returnedLate,
        OffsetDateTime checkedInAt,
        OffsetDateTime completedAt,
        OffsetDateTime cancelledAt,
        String cancelReason,
        OffsetDateTime createdAt
) {
    public static final String OVERDUE = "OVERDUE";

    public record RoomSummary(Long id, String name, int capacity) {
    }

    public record UserSummary(Long id, String studentNo, String name, String phone) {
    }

    /**
     * room·user는 호출하는 쪽이 join fetch로 미리 읽어 둬요(목록에서 N+1이 생기지 않게, NF-07).
     *
     * @param fullPhone 본인·관리자면 true. 그 밖에는 전화번호를 마스킹해요 (NF-02)
     */
    public static ReservationResponse of(Reservation r, boolean fullPhone, Instant now) {
        Room room = r.getRoom();
        User user = r.getUser();
        String display = r.isOverdue(now) ? OVERDUE : r.getStatus().name();
        return new ReservationResponse(
                r.getId(),
                new RoomSummary(room.getId(), room.getName(), room.getCapacity()),
                KstTime.of(r.getStartAt()),
                KstTime.of(r.getEndAt()),
                r.getHeadcount(),
                r.getStatus(),
                r.isKeyRequired(),
                display,
                new UserSummary(user.getId(), user.getStudentNo(), user.getName(),
                        fullPhone ? user.getPhone() : UserRules.maskPhone(user.getPhone())),
                KstTime.of(r.getKeyIssuedAt()),
                KstTime.of(r.getReturnedAt()),
                r.getReturnedLate(),
                KstTime.of(r.getCheckedInAt()),
                KstTime.of(r.getCompletedAt()),
                KstTime.of(r.getCancelledAt()),
                r.getCancelReason(),
                KstTime.of(r.getCreatedAt()));
    }
}
