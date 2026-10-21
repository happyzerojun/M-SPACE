package com.mspace.reservation.service;

import com.mspace.global.error.BusinessException;
import com.mspace.global.error.ErrorCode;
import com.mspace.global.util.KstTime;
import com.mspace.policy.domain.OperationPolicy;
import com.mspace.policy.service.DayHours;
import com.mspace.policy.service.PolicyService;
import com.mspace.reservation.domain.ReservationStatus;
import com.mspace.reservation.dto.BoardBlockRow;
import com.mspace.reservation.dto.BoardResponse;
import com.mspace.reservation.dto.BoardResponse.Block;
import com.mspace.reservation.dto.BoardResponse.BoardRoom;
import com.mspace.reservation.dto.ReservationResponse;
import com.mspace.reservation.repository.ReservationRepository;
import com.mspace.room.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현황판 (BD-01~05). 비로그인 공개 화면이라 방·시간·상태만 내보내요 (BD-02).
 * 30초마다 폴링되므로 쿼리 수를 날짜와 무관한 상수로 유지해요: 정책 1 + 운영 기간 1 + 방 1 + 예약 1 (NF-07).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final PolicyService policyService;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public BoardResponse board(LocalDate requested) {
        Instant now = clock.instant();
        OperationPolicy policy = policyService.policy();
        LocalDate today = KstTime.dateOf(now);
        LocalDate date = requested == null ? today : requested;
        if (date.isBefore(today) || date.isAfter(today.plusDays(policy.getBookingWindowDays()))) {
            throw new BusinessException(ErrorCode.RESERVATION_OUT_OF_WINDOW, policy.getBookingWindowDays());
        }

        DayHours hours = policyService.hoursOf(date, policy);
        Map<Long, List<Block>> blocksByRoom = reservationRepository
                .findBoardBlocks(ReservationStatus.ACTIVE, KstTime.startOfDay(date), KstTime.startOfDay(date.plusDays(1)))
                .stream()
                .collect(Collectors.groupingBy(BoardBlockRow::roomId,
                        Collectors.mapping(row -> toBlock(row, now), Collectors.toList())));

        List<BoardRoom> rooms = roomRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(room -> new BoardRoom(room.getId(), room.getName(), room.getCapacity(), room.isActive(),
                        blocksByRoom.getOrDefault(room.getId(), List.of())))
                .toList();

        return new BoardResponse(date, hours.closed(), KstTime.of(hours.openAt()), KstTime.of(hours.closeAt()),
                hours.periodName(), hours.keyRequired(), rooms);
    }

    /** 키 예약이 이용 중인데 종료 시각이 지나면 화면에 연체로 보여줘요 (설계서 1장) */
    private static Block toBlock(BoardBlockRow row, Instant now) {
        boolean overdue = row.status() == ReservationStatus.IN_USE && Boolean.TRUE.equals(row.keyRequired()) && !now.isBefore(row.endAt());
        return new Block(KstTime.of(row.startAt()), KstTime.of(row.endAt()),
                overdue ? ReservationResponse.OVERDUE : row.status().name());
    }
}
