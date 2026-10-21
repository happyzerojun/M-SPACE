package com.mspace.reservation.service;

import com.mspace.global.error.BusinessException;
import com.mspace.policy.domain.OperationPolicy;
import com.mspace.policy.service.PolicyService;
import com.mspace.reservation.domain.Reservation;
import com.mspace.reservation.domain.ReservationStatus;
import com.mspace.reservation.domain.UserRestriction;
import com.mspace.reservation.repository.ReservationRepository;
import com.mspace.reservation.repository.UserRestrictionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주기 작업 (docs/예약-상태-설계.md 4장): 노쇼 처리(NS-01) → 노쇼 제재(NS-02), 개방 기간 자동 이용 종료(OP-03).
 *
 * <p><b>멱등성</b>: 대상은 매번 상태로 다시 골라요(이미 NO_SHOW·COMPLETED면 대상에 안 걸려요). 여러 번, 동시에 돌아도 결과가 같아요.
 * <p><b>건별 트랜잭션</b>: 예약 하나마다 따로 커밋해요. 같은 예약을 관리자가 동시에 키 지급하면 version 충돌로 그 건만
 * 건너뛰고(다음 주기에 다시 판단) 나머지는 그대로 진행돼요. 한 건의 실패가 전체를 롤백하지 않아요.
 */
@Slf4j
@Service
public class NoShowService {

    private final ReservationRepository reservationRepository;
    private final UserRestrictionRepository restrictionRepository;
    private final PolicyService policyService;
    private final Clock clock;
    private final TransactionTemplate tx;

    public NoShowService(ReservationRepository reservationRepository, UserRestrictionRepository restrictionRepository,
                         PolicyService policyService, Clock clock, PlatformTransactionManager transactionManager) {
        this.reservationRepository = reservationRepository;
        this.restrictionRepository = restrictionRepository;
        this.policyService = policyService;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 한 주기. 처리한 건수를 돌려줘요 (로그·테스트용) */
    public Result runOnce() {
        Instant now = clock.instant();
        OperationPolicy policy = policyService.policy();

        // 1. 노쇼: 시작 + 노쇼 기준 시간이 지나도록 RESERVED인 예약 (키 예약·개방 기간 공통)
        Set<Long> noShowUsers = new LinkedHashSet<>();
        int noShows = 0;
        Instant threshold = now.minus(policy.noShowGrace());
        for (Long id : reservationRepository.findIdsByStatusAndStartAtUpTo(ReservationStatus.RESERVED, threshold)) {
            Long userId = inNewTransaction(() -> {
                Reservation r = reservationRepository.findById(id).orElse(null);
                if (r == null || r.getStatus() != ReservationStatus.RESERVED) {
                    return null;
                }
                r.markNoShow(now, policy);
                reservationRepository.flush();
                return r.getUser().getId();
            });
            if (userId != null) {
                noShows++;
                noShowUsers.add(userId);
            }
        }

        // 2. 노쇼 제재: 이번에 노쇼가 생긴 학생만 다시 세요 (NS-02)
        int restrictions = 0;
        for (Long userId : noShowUsers) {
            if (Boolean.TRUE.equals(inNewTransaction(() -> restrictIfNeeded(userId, now, policy)))) {
                restrictions++;
            }
        }

        // 3. 개방 기간 자동 이용 종료: 종료 시각이 지난 셀프 체크인 예약 (OP-03)
        int completed = 0;
        for (Long id : reservationRepository.findSelfCheckInIdsEndedBy(ReservationStatus.IN_USE, now)) {
            Boolean done = inNewTransaction(() -> {
                Reservation r = reservationRepository.findById(id).orElse(null);
                if (r == null || r.getStatus() != ReservationStatus.IN_USE) {
                    return false;
                }
                r.autoComplete(now);
                reservationRepository.flush();
                return true;
            });
            if (Boolean.TRUE.equals(done)) {
                completed++;
            }
        }

        Result result = new Result(noShows, restrictions, completed);
        if (noShows + restrictions + completed > 0) {
            // 개인정보 없이 건수만 남겨요 (NF-01)
            log.info("예약 주기 작업: 노쇼 {}건, 이용 제한 {}건, 자동 이용 종료 {}건", noShows, restrictions, completed);
        }
        return result;
    }

    /**
     * 최근 penalty_window_days 안의 노쇼가 기준 이상이고, 지금 유효한 제한이 없으면 제한을 만들어요.
     * 이미 끝난 제한에 쓰인 노쇼를 다시 세지 않게, 세는 구간 시작은 max(now − window, 마지막 제한 시작)이에요 (설계서 4.1).
     */
    private boolean restrictIfNeeded(Long userId, Instant now, OperationPolicy policy) {
        if (!restrictionRepository.findActive(userId, now).isEmpty()) {
            return false;
        }
        Instant since = now.minus(Duration.ofDays(policy.getPenaltyWindowDays()));
        Instant lastStart = restrictionRepository.findFirstByUserIdOrderByStartAtDesc(userId)
                .map(UserRestriction::getStartAt)
                .orElse(null);
        if (lastStart != null && lastStart.isAfter(since)) {
            since = lastStart;
        }
        long count = reservationRepository.countNoShowsSince(userId, ReservationStatus.NO_SHOW, since);
        if (count < policy.getPenaltyThreshold()) {
            return false;
        }
        String reason = "노쇼 %d회 (최근 %d일)".formatted(count, policy.getPenaltyWindowDays());
        restrictionRepository.saveAndFlush(
                UserRestriction.of(userId, now, now.plus(Duration.ofDays(policy.getPenaltyDays())), reason));
        return true;
    }

    /**
     * 건별 새 트랜잭션. 동시 처리와 부딪힌 경우만 건너뛰고(다음 주기에 다시 판단), 그 밖의 예외는 로그를 남기고 건너뛰어요.
     * - version 충돌: 관리자가 같은 예약을 동시에 키 지급·취소
     * - EXCLUDE 위반(ex_user_restriction_active): 제한을 동시에 두 번 만들려 함 → 하나만 남아요
     * - BusinessException: 그 사이 상태가 바뀜
     */
    private <T> T inNewTransaction(Supplier<T> work) {
        try {
            return tx.execute(status -> work.get());
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException | BusinessException e) {
            log.debug("예약 주기 작업 한 건을 건너뛰어요: {}", e.getClass().getSimpleName());
            return null;
        } catch (RuntimeException e) {
            log.warn("예약 주기 작업 한 건이 실패했어요: {}", e.getClass().getName());
            return null;
        }
    }

    public record Result(int noShows, int restrictions, int completed) {
    }
}
