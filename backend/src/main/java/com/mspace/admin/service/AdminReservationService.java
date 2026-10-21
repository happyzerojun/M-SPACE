package com.mspace.admin.service;

import com.mspace.admin.domain.AdminAction;
import com.mspace.admin.domain.AdminAuditLog;
import com.mspace.admin.dto.AdminReservationDetailResponse;
import com.mspace.admin.dto.DashboardResponse;
import com.mspace.admin.dto.KeyLogResponse;
import com.mspace.admin.repository.AdminAuditLogRepository;
import com.mspace.admin.repository.AdminReservationQueryRepository;
import com.mspace.admin.repository.AdminReservationQueryRepository.KeyLogCriteria;
import com.mspace.admin.repository.AdminReservationQueryRepository.ReservationCriteria;
import com.mspace.global.common.PageCondition;
import com.mspace.global.common.PageResponse;
import com.mspace.global.error.BusinessException;
import com.mspace.global.error.ErrorCode;
import com.mspace.global.error.ErrorResponse.FieldError;
import com.mspace.global.util.KstTime;
import com.mspace.key.domain.KeyLog;
import com.mspace.key.repository.KeyLogRepository;
import com.mspace.policy.service.PolicyService;
import com.mspace.reservation.domain.Reservation;
import com.mspace.reservation.domain.ReservationStatus;
import com.mspace.reservation.dto.ReservationResponse;
import com.mspace.reservation.repository.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 예약·키 (AD-02~04, KY-01~07). 조교·근로학생이 학과실에서 쓰는 기능이에요.
 * 키 지급·반납·취소는 같은 예약을 동시에 바꾸면 version 충돌(409 CONCURRENT_UPDATE),
 * 지급·반납 두 번 클릭은 key_log UNIQUE 제약(409 INVALID_RESERVATION_STATE)이 막아요.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReservationService {

    private static final String TARGET_RESERVATION = "RESERVATION";
    /** 날짜를 안 주고 기간 검색할 때 한 번에 볼 수 있는 최대 범위 (상한 없는 목록 방지, NF-07) */
    private static final long MAX_SEARCH_DAYS = 92;

    private final ReservationRepository reservationRepository;
    private final AdminReservationQueryRepository queryRepository;
    private final KeyLogRepository keyLogRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final PolicyService policyService;
    private final Clock clock;

    /** 오늘 화면 (AD-04) */
    public DashboardResponse dashboard(LocalDate requested) {
        Instant now = clock.instant();
        LocalDate date = requested == null ? KstTime.dateOf(now) : requested;
        List<Reservation> day = queryRepository.findDay(KstTime.startOfDay(date), KstTime.startOfDay(date.plusDays(1)));

        long overdue = count(day, r -> r.isOverdue(now));
        DashboardResponse.Counts counts = new DashboardResponse.Counts(
                count(day, status(ReservationStatus.RESERVED)),
                count(day, status(ReservationStatus.IN_USE)) - overdue,
                overdue,
                count(day, status(ReservationStatus.RETURNED)),
                count(day, status(ReservationStatus.COMPLETED)),
                count(day, status(ReservationStatus.NO_SHOW)),
                count(day, status(ReservationStatus.CANCELLED)));

        List<ReservationResponse> pendingIssue = day.stream()
                .filter(r -> r.getStatus() == ReservationStatus.RESERVED && r.isKeyRequired())
                .map(r -> ReservationResponse.of(r, true, now))
                .toList();
        List<ReservationResponse> overdueList = reservationRepository.findOverdue(ReservationStatus.IN_USE, now).stream()
                .map(r -> ReservationResponse.of(r, true, now))
                .toList();
        return new DashboardResponse(date, counts, pendingIssue, overdueList);
    }

    /**
     * 예약 검색 (AD-02). date를 주면 그날, 아니면 from~to(날짜, 둘 다 포함). 아무것도 없으면 오늘이에요.
     * 범위는 최대 92일로 막아요.
     */
    public PageResponse<ReservationResponse> search(LocalDate date, LocalDate from, LocalDate to, Long roomId,
                                                    ReservationStatus status, String q, PageCondition page) {
        Instant now = clock.instant();
        LocalDate start;
        LocalDate end;
        if (date != null) {
            start = date;
            end = date;
        } else {
            LocalDate today = KstTime.dateOf(now);
            start = from != null ? from : (to != null ? to : today);
            end = to != null ? to : start;
        }
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= MAX_SEARCH_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldError("to", "검색 기간은 시작일부터 92일 안에서 골라 주세요.")));
        }
        ReservationCriteria criteria = new ReservationCriteria(KstTime.startOfDay(start), KstTime.startOfDay(end.plusDays(1)),
                roomId, status, q);
        return PageResponse.from(queryRepository.searchReservations(criteria, page)
                .map(r -> ReservationResponse.of(r, true, now)));
    }

    public AdminReservationDetailResponse detail(Long reservationId) {
        Reservation reservation = find(reservationId);
        return new AdminReservationDetailResponse(ReservationResponse.of(reservation, true, clock.instant()),
                queryRepository.keyLogsOf(reservationId));
    }

    /** 관리자 취소 (AD-03): 사유를 예약과 감사 기록에 함께 남겨요 (AD-08) */
    @Transactional
    public ReservationResponse cancel(Long adminId, Long reservationId, String reason) {
        Instant now = clock.instant();
        Reservation reservation = find(reservationId);
        reservation.cancelByAdmin(adminId, reason, now);
        auditLogRepository.save(AdminAuditLog.of(adminId, AdminAction.RESERVATION_CANCEL, TARGET_RESERVATION,
                reservationId, reason.strip()));
        reservationRepository.flush();
        return ReservationResponse.of(reservation, true, now);
    }

    /** 키 지급 (KY-01, KY-02): 실물 학생증 확인 → 키 지급 → 이용 중 */
    @Transactional
    public ReservationResponse issueKey(Long adminId, Long reservationId, boolean identityVerified, String memo) {
        Instant now = clock.instant();
        Reservation reservation = find(reservationId);
        KeyLog log = reservation.issueKey(adminId, identityVerified, memo, now, policyService.policy());
        keyLogRepository.save(log);
        reservationRepository.flush();
        return ReservationResponse.of(reservation, true, now);
    }

    /** 반납 확인 (KY-03): 종료 시각이 지났으면 연체 반납으로 기록돼요 */
    @Transactional
    public ReservationResponse returnKey(Long adminId, Long reservationId, String memo) {
        Instant now = clock.instant();
        Reservation reservation = find(reservationId);
        KeyLog log = reservation.returnKey(adminId, memo, now);
        keyLogRepository.save(log);
        reservationRepository.flush();
        return ReservationResponse.of(reservation, true, now);
    }

    /** 키 이력 검색 (KY-07): 기간을 안 주면 최근 7일 */
    public PageResponse<KeyLogResponse> keyLogs(LocalDate from, LocalDate to, Long roomId, String q, PageCondition page) {
        LocalDate today = KstTime.dateOf(clock.instant());
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(6);
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= MAX_SEARCH_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldError("to", "검색 기간은 시작일부터 92일 안에서 골라 주세요.")));
        }
        KeyLogCriteria criteria = new KeyLogCriteria(KstTime.startOfDay(start), KstTime.startOfDay(end.plusDays(1)), roomId, q);
        return PageResponse.from(queryRepository.searchKeyLogs(criteria, page));
    }

    private Reservation find(Long reservationId) {
        return reservationRepository.findDetail(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    private static Predicate<Reservation> status(ReservationStatus status) {
        return r -> r.getStatus() == status;
    }

    private static long count(List<Reservation> list, Predicate<Reservation> predicate) {
        return list.stream().filter(predicate).count();
    }
}
