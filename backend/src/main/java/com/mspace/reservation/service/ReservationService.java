package com.mspace.reservation.service;

import com.mspace.global.common.PageCondition;
import com.mspace.global.common.PageResponse;
import com.mspace.global.error.BusinessException;
import com.mspace.global.error.ErrorCode;
import com.mspace.global.error.ErrorResponse.FieldError;
import com.mspace.global.util.KstTime;
import com.mspace.policy.domain.OperationPolicy;
import com.mspace.policy.service.DayHours;
import com.mspace.policy.service.PolicyService;
import com.mspace.reservation.domain.Reservation;
import com.mspace.reservation.domain.ReservationStatus;
import com.mspace.reservation.domain.UserRestriction;
import com.mspace.reservation.dto.ReservationCreateRequest;
import com.mspace.reservation.dto.ReservationResponse;
import com.mspace.reservation.repository.ReservationRepository;
import com.mspace.reservation.repository.UserRestrictionRepository;
import com.mspace.room.domain.Room;
import com.mspace.room.repository.RoomRepository;
import com.mspace.user.domain.User;
import com.mspace.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학생 예약: 신청(RV-01~08, RV-12), 내 예약(RV-09), 취소(RV-10), 개방 기간 이용 시작·종료(OP-02, OP-03) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private static final long SLOT_SECONDS = 30 * 60;
    private static final DateTimeFormatter RESTRICTION_END = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final ReservationRepository reservationRepository;
    private final UserRestrictionRepository restrictionRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final PolicyService policyService;
    private final Clock clock;

    /**
     * 예약 신청. 검사 순서는 docs/예약-상태-설계.md 5장 그대로예요.
     * 앞 단계 검사는 친절한 에러를 위한 것이고, 동시 신청이 검사를 통과해도 겹침은 DB EXCLUDE 제약이 막아요
     * (위반 → GlobalExceptionHandler가 409 RESERVATION_TIME_CONFLICT / USER_TIME_CONFLICT로 바꿔요, NF-05).
     * 실패하면 RuntimeException이라 트랜잭션 전체가 롤백돼요 → 신청자 행 잠금도 함께 풀려요.
     */
    @Transactional
    public ReservationResponse create(Long userId, ReservationCreateRequest request) {
        Instant now = clock.instant();
        Instant startAt = request.startAt().toInstant();
        Instant endAt = request.endAt().toInstant();

        // 1. 입력 형식: 30분 단위, 종료 > 시작 (DB CHECK ck_reservation_slot·ck_reservation_time과 같은 규칙)
        validateSlots(startAt, endAt);

        // 2. 방 존재·활성
        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        if (!room.isActive()) {
            throw new BusinessException(ErrorCode.ROOM_INACTIVE);
        }

        // 3. 인원 ≤ 정원 (예약 단위는 방 전체, D-1)
        if (request.headcount() > room.getCapacity()) {
            throw new BusinessException(ErrorCode.HEADCOUNT_EXCEEDS_CAPACITY, room.getCapacity());
        }

        // 4. 지금 이후, 오늘 ~ 오늘 + 예약 가능 기간 (KST 날짜 기준)
        OperationPolicy policy = policyService.policy();
        if (!startAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.RESERVATION_IN_PAST);
        }
        LocalDate date = KstTime.dateOf(startAt);
        if (date.isAfter(KstTime.dateOf(now).plusDays(policy.getBookingWindowDays()))) {
            throw new BusinessException(ErrorCode.RESERVATION_OUT_OF_WINDOW, policy.getBookingWindowDays());
        }

        // 5. 그날 운영 시간 안 (특별 운영 기간 → 기본). 자정을 넘길 수 없어요
        DayHours hours = policyService.hoursOf(date, policy);
        if (hours.closed()) {
            throw new BusinessException(ErrorCode.CLOSED_DAY);
        }
        if (!hours.contains(startAt, endAt)) {
            throw new BusinessException(ErrorCode.OUTSIDE_OPERATING_HOURS,
                    KstTime.formatMinute(hours.openMinute()), KstTime.formatMinute(hours.closeMinute()));
        }

        // 6. 1회 최대 이용 시간 (공식 규정 2시간, 정책 값)
        if (Duration.between(startAt, endAt).compareTo(policy.maxDuration()) > 0) {
            throw new BusinessException(ErrorCode.RESERVATION_TOO_LONG, hoursText(policy.getMaxDurationMinutes()));
        }

        // 7. 노쇼 누적 이용 제한 (NS-02)
        List<UserRestriction> restrictions = restrictionRepository.findActive(userId, now);
        if (!restrictions.isEmpty()) {
            throw new BusinessException(ErrorCode.USER_RESTRICTED,
                    RESTRICTION_END.format(KstTime.of(restrictions.getFirst().getEndAt())));
        }

        // 8. 신청자 행을 잠그고 하루 한도를 세요. 같은 학생의 동시 신청은 여기서 줄을 서요 (RV-06)
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        long sameDay = reservationRepository.countByUserAndStartBetween(userId, ReservationStatus.COUNTED_FOR_DAILY_LIMIT,
                KstTime.startOfDay(date), KstTime.startOfDay(date.plusDays(1)));
        if (sameDay >= policy.getDailyLimit()) {
            throw new BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED, policy.getDailyLimit());
        }

        // 9. 키 필요 여부는 그날 운영 기간으로 정해 저장해요 (D-9). 겹침은 INSERT 때 EXCLUDE 제약이 판정해요.
        //    Hibernate 경로로 INSERT해야 교착(40P01)도 예약 INSERT로 알아보고 409로 바꿀 수 있어요 (TS-16)
        Reservation reservation = Reservation.reserve(room, user, startAt, endAt, request.headcount(), hours.keyRequired());
        reservationRepository.saveAndFlush(reservation);
        return ReservationResponse.of(reservation, true, now);
    }

    /** scope: upcoming(종료 전, 시작 오름차순, 기본) | past(나머지, 시작 내림차순) (RV-09) */
    public PageResponse<ReservationResponse> myReservations(Long userId, String scope, PageCondition page) {
        Instant now = clock.instant();
        boolean past = parseScope(scope);
        Page<Reservation> result = past
                ? reservationRepository.findPast(userId, ReservationStatus.ACTIVE, now,
                        page.toPageable(Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id"))))
                : reservationRepository.findUpcoming(userId, ReservationStatus.ACTIVE, now,
                        page.toPageable(Sort.by(Sort.Order.asc("startAt"), Sort.Order.asc("id"))));
        return PageResponse.from(result.map(r -> ReservationResponse.of(r, true, now)));
    }

    public ReservationResponse get(Long userId, Long reservationId) {
        return ReservationResponse.of(findOwn(userId, reservationId), true, clock.instant());
    }

    @Transactional
    public ReservationResponse cancel(Long userId, Long reservationId) {
        Instant now = clock.instant();
        Reservation reservation = findOwn(userId, reservationId);
        reservation.cancelByOwner(userId, now);
        reservationRepository.flush();   // version 충돌(스케줄러·관리자와 동시 처리)을 여기서 드러내요 → 409 CONCURRENT_UPDATE
        return ReservationResponse.of(reservation, true, now);
    }

    @Transactional
    public ReservationResponse checkIn(Long userId, Long reservationId) {
        Instant now = clock.instant();
        Reservation reservation = findOwn(userId, reservationId);
        reservation.selfCheckIn(now, policyService.policy());
        reservationRepository.flush();
        return ReservationResponse.of(reservation, true, now);
    }

    @Transactional
    public ReservationResponse finish(Long userId, Long reservationId) {
        Instant now = clock.instant();
        Reservation reservation = findOwn(userId, reservationId);
        reservation.finishBySelf(now);
        reservationRepository.flush();
        return ReservationResponse.of(reservation, true, now);
    }

    /** 남의 예약은 있는지조차 알려주지 않아요 → 없는 예약과 같은 404 (RV-09, CLAUDE.md 7장) */
    private Reservation findOwn(Long userId, Long reservationId) {
        return reservationRepository.findDetail(reservationId)
                .filter(r -> r.isOwnedBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    private static void validateSlots(Instant startAt, Instant endAt) {
        List<FieldError> errors = new ArrayList<>();
        if (startAt.getEpochSecond() % SLOT_SECONDS != 0 || startAt.getNano() != 0) {
            errors.add(new FieldError("startAt", "30분 단위로 골라 주세요."));
        }
        if (endAt.getEpochSecond() % SLOT_SECONDS != 0 || endAt.getNano() != 0) {
            errors.add(new FieldError("endAt", "30분 단위로 골라 주세요."));
        }
        if (!endAt.isAfter(startAt)) {
            errors.add(new FieldError("endAt", "종료 시각은 시작 시각보다 늦어야 해요."));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, errors);
        }
    }

    private static boolean parseScope(String scope) {
        if (scope == null || scope.isBlank() || scope.equals("upcoming")) {
            return false;
        }
        if (scope.equals("past")) {
            return true;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                List.of(new FieldError("scope", "upcoming 또는 past로 골라 주세요.")));
    }

    /** 120분 → "2", 90분 → "1.5" (에러 메시지 "최대 {0}시간") */
    static String hoursText(int minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
