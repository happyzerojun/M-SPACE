package com.mspace.admin.repository;

import com.mspace.admin.dto.KeyLogResponse;
import com.mspace.global.common.PageCondition;
import com.mspace.reservation.domain.Reservation;
import com.mspace.reservation.domain.ReservationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * 관리자 예약·키 이력 검색 (AD-02, KY-07). 조건이 있을 때만 WHERE에 붙여요.
 * "파라미터 IS NULL OR ..." 꼴은 PostgreSQL이 null 파라미터의 타입을 못 정해 실패할 수 있어서 쓰지 않아요.
 * 목록은 방·회원을 join fetch로 함께 읽어요 (N+1 방지, NF-07). 검색 기간은 서비스가 항상 정해서 넘겨요(상한 없는 목록 방지).
 */
@Repository
@RequiredArgsConstructor
public class AdminReservationQueryRepository {

    private final EntityManager em;

    /** 검색 조건. from~to와 겹치는 예약이에요(시작이 to 전이고 종료가 from 뒤) */
    public record ReservationCriteria(Instant from, Instant to, Long roomId, ReservationStatus status, String q) {
    }

    public record KeyLogCriteria(Instant from, Instant to, Long roomId, String q) {
    }

    public Page<Reservation> searchReservations(ReservationCriteria c, PageCondition page) {
        StringBuilder where = new StringBuilder(" where r.startAt < :to and r.endAt > :from");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("from", c.from());
        params.put("to", c.to());
        if (c.roomId() != null) {
            where.append(" and rm.id = :roomId");
            params.put("roomId", c.roomId());
        }
        if (c.status() != null) {
            where.append(" and r.status = :status");
            params.put("status", c.status());
        }
        appendKeyword(c.q(), where, params);

        TypedQuery<Reservation> query = em.createQuery(
                "select r from Reservation r join fetch r.room rm join fetch r.user u" + where
                        + " order by r.startAt, rm.sortOrder, r.id", Reservation.class);
        TypedQuery<Long> count = em.createQuery(
                "select count(r) from Reservation r join r.room rm join r.user u" + where, Long.class);
        return page(query, count, params, page);
    }

    /** 하루 목록 (대시보드): 그날과 겹치는 예약 전부. 하루 예약 수는 방 5개 × 운영 시간이라 많지 않아요 */
    public List<Reservation> findDay(Instant from, Instant to) {
        return em.createQuery("""
                        select r from Reservation r join fetch r.room rm join fetch r.user
                        where r.startAt < :to and r.endAt > :from
                        order by r.startAt, rm.sortOrder, r.id
                        """, Reservation.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    public Page<KeyLogResponse> searchKeyLogs(KeyLogCriteria c, PageCondition page) {
        StringBuilder where = new StringBuilder(" where k.occurredAt >= :from and k.occurredAt < :to");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("from", c.from());
        params.put("to", c.to());
        if (c.roomId() != null) {
            where.append(" and rm.id = :roomId");
            params.put("roomId", c.roomId());
        }
        appendKeyword(c.q(), where, params);

        String from = " from KeyLog k join Reservation r on r.id = k.reservationId join r.room rm join r.user u"
                + " join User a on a.id = k.adminId";
        TypedQuery<KeyLogResponse> query = em.createQuery(
                "select new com.mspace.admin.dto.KeyLogResponse(k.id, k.reservationId, rm.name, u.studentNo, u.name,"
                        + " k.action, a.name, k.identityVerified, k.late, k.memo, k.occurredAt)"
                        + from + where + " order by k.occurredAt desc, k.id desc", KeyLogResponse.class);
        TypedQuery<Long> count = em.createQuery("select count(k)" + from + where, Long.class);
        return page(query, count, params, page);
    }

    public List<KeyLogResponse> keyLogsOf(Long reservationId) {
        return em.createQuery("""
                        select new com.mspace.admin.dto.KeyLogResponse(k.id, k.reservationId, rm.name, u.studentNo, u.name,
                            k.action, a.name, k.identityVerified, k.late, k.memo, k.occurredAt)
                        from KeyLog k join Reservation r on r.id = k.reservationId join r.room rm join r.user u
                            join User a on a.id = k.adminId
                        where k.reservationId = :reservationId
                        order by k.occurredAt, k.id
                        """, KeyLogResponse.class)
                .setParameter("reservationId", reservationId)
                .getResultList();
    }

    /** 학번 앞부분 또는 이름 일부 (회원 검색과 같은 규칙). %·_는 글자 그대로 찾게 이스케이프해요 */
    private static void appendKeyword(String q, StringBuilder where, Map<String, Object> params) {
        if (q == null || q.isBlank()) {
            return;
        }
        String escaped = q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        where.append(" and (u.studentNo like :qPrefix escape '\\' or u.name like :qContains escape '\\')");
        params.put("qPrefix", escaped + "%");
        params.put("qContains", "%" + escaped + "%");
    }

    private static <T> Page<T> page(TypedQuery<T> query, TypedQuery<Long> count, Map<String, Object> params,
                                    PageCondition page) {
        params.forEach((name, value) -> {
            query.setParameter(name, value);
            count.setParameter(name, value);
        });
        List<T> content = query
                .setFirstResult((page.page() - 1) * page.size())
                .setMaxResults(page.size())
                .getResultList();
        return new PageImpl<>(content, page.toPageable(Sort.unsorted()), count.getSingleResult());
    }
}
