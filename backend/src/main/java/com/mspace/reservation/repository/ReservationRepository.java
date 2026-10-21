package com.mspace.reservation.repository;

import com.mspace.reservation.domain.Reservation;
import com.mspace.reservation.domain.ReservationStatus;
import com.mspace.reservation.dto.BoardBlockRow;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 예약 조회. 응답에 방·회원이 함께 나가는 조회는 join fetch로 한 번에 읽어요(N+1 방지, NF-07).
 * 방·회원은 ManyToOne이라 fetch join과 페이지를 같이 써도 행이 불어나지 않아요.
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("select r from Reservation r join fetch r.room join fetch r.user where r.id = :id")
    Optional<Reservation> findDetail(@Param("id") Long id);

    /** 하루 한도 (RV-06): 시작 날짜(KST)가 같은 예약 중 취소·노쇼가 아닌 것 */
    @Query("""
            select count(r) from Reservation r
            where r.user.id = :userId and r.status in :statuses and r.startAt >= :from and r.startAt < :to
            """)
    long countByUserAndStartBetween(@Param("userId") Long userId, @Param("statuses") Collection<ReservationStatus> statuses,
                                    @Param("from") Instant from, @Param("to") Instant to);

    /** 내 예약 - 다가오는 예약 (RV-09): 끝나지 않은 RESERVED·IN_USE */
    @Query(value = """
            select r from Reservation r join fetch r.room join fetch r.user
            where r.user.id = :userId and r.status in :active and r.endAt > :now
            """, countQuery = """
            select count(r) from Reservation r
            where r.user.id = :userId and r.status in :active and r.endAt > :now
            """)
    Page<Reservation> findUpcoming(@Param("userId") Long userId, @Param("active") Collection<ReservationStatus> active,
                                   @Param("now") Instant now, Pageable pageable);

    /** 내 예약 - 지난 예약 (RV-09): 다가오는 예약이 아닌 나머지 전부 (종료됨·취소·노쇼) */
    @Query(value = """
            select r from Reservation r join fetch r.room join fetch r.user
            where r.user.id = :userId and (r.status not in :active or r.endAt <= :now)
            """, countQuery = """
            select count(r) from Reservation r
            where r.user.id = :userId and (r.status not in :active or r.endAt <= :now)
            """)
    Page<Reservation> findPast(@Param("userId") Long userId, @Param("active") Collection<ReservationStatus> active,
                               @Param("now") Instant now, Pageable pageable);

    /** 현황판 (BD-01): 그날과 겹치는 RESERVED·IN_USE 예약의 방·시간·상태만. 예약자 컬럼은 읽지 않아요 (BD-02) */
    @Query("""
            select new com.mspace.reservation.dto.BoardBlockRow(r.room.id, r.startAt, r.endAt, r.status, r.keyRequired)
            from Reservation r
            where r.status in :active and r.startAt < :to and r.endAt > :from
            order by r.startAt
            """)
    List<BoardBlockRow> findBoardBlocks(@Param("active") Collection<ReservationStatus> active,
                                        @Param("from") Instant from, @Param("to") Instant to);

    /** 연체 목록 (AD-04): 키 예약이 이용 중인데 종료 시각이 지났어요. 날짜와 상관없이 전부 */
    @Query("""
            select r from Reservation r join fetch r.room join fetch r.user
            where r.status = :inUse and r.keyRequired = true and r.endAt <= :now
            order by r.endAt, r.id
            """)
    List<Reservation> findOverdue(@Param("inUse") ReservationStatus inUse, @Param("now") Instant now);

    /** 스케줄러 대상 id (설계서 4장). 건별로 따로 처리해서 한 건이 실패해도 나머지는 진행돼요 */
    @Query("select r.id from Reservation r where r.status = :status and r.startAt <= :threshold order by r.startAt, r.id")
    List<Long> findIdsByStatusAndStartAtUpTo(@Param("status") ReservationStatus status, @Param("threshold") Instant threshold);

    @Query("""
            select r.id from Reservation r
            where r.status = :status and r.keyRequired = false and r.endAt <= :now
            order by r.endAt, r.id
            """)
    List<Long> findSelfCheckInIdsEndedBy(@Param("status") ReservationStatus status, @Param("now") Instant now);

    /** 노쇼 제재 판단 (NS-02): since 이후 시작한 이 학생의 NO_SHOW 수 */
    @Query("""
            select count(r) from Reservation r
            where r.user.id = :userId and r.status = :noShow and r.startAt >= :since
            """)
    long countNoShowsSince(@Param("userId") Long userId, @Param("noShow") ReservationStatus noShow,
                           @Param("since") Instant since);

    /** 계정 비활성화 때 함께 취소할 예약 (AU-08) */
    @Query("select r from Reservation r where r.user.id = :userId and r.status = :status")
    List<Reservation> findByUserAndStatus(@Param("userId") Long userId, @Param("status") ReservationStatus status);
}
