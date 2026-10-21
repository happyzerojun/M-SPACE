package com.mspace.admin.service;

import com.mspace.admin.domain.AdminAction;
import com.mspace.admin.domain.AdminAuditLog;
import com.mspace.admin.dto.RestrictionResponse;
import com.mspace.admin.repository.AdminAuditLogRepository;
import com.mspace.global.common.PageCondition;
import com.mspace.global.common.PageResponse;
import com.mspace.global.error.BusinessException;
import com.mspace.global.error.ErrorCode;
import com.mspace.reservation.domain.UserRestriction;
import com.mspace.reservation.repository.UserRestrictionRepository;
import com.mspace.user.domain.User;
import com.mspace.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 노쇼 누적 이용 제한 조회·해제 (NS-03). 해제는 사유와 함께 감사 기록에 남겨요 (AD-08) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRestrictionService {

    private static final String TARGET_RESTRICTION = "RESTRICTION";

    private final UserRestrictionRepository restrictionRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final EntityManager em;
    private final Clock clock;

    /** active=true면 지금 유효한 제한만, 아니면 전체. 최근 시작 순 */
    public PageResponse<RestrictionResponse> search(boolean activeOnly, PageCondition page) {
        Instant now = clock.instant();
        String where = activeOnly ? " where ur.liftedAt is null and ur.startAt <= :now and ur.endAt > :now" : "";
        TypedQuery<Object[]> query = em.createQuery("""
                select ur.id, ur.userId, u.studentNo, u.name, ur.startAt, ur.endAt, ur.reason, ur.liftedAt, ur.liftReason
                from UserRestriction ur join User u on u.id = ur.userId""" + where
                + " order by ur.startAt desc, ur.id desc", Object[].class);
        TypedQuery<Long> count = em.createQuery("select count(ur) from UserRestriction ur" + where, Long.class);
        if (activeOnly) {
            query.setParameter("now", now);
            count.setParameter("now", now);
        }
        List<RestrictionResponse> content = query
                .setFirstResult((page.page() - 1) * page.size())
                .setMaxResults(page.size())
                .getResultList().stream()
                .map(row -> RestrictionResponse.of((Long) row[0], (Long) row[1], (String) row[2], (String) row[3],
                        (Instant) row[4], (Instant) row[5], (String) row[6], (Instant) row[7], (String) row[8], now))
                .toList();
        return PageResponse.from(new PageImpl<>(content, page.toPageable(Sort.unsorted()), count.getSingleResult()));
    }

    @Transactional
    public RestrictionResponse lift(Long adminId, Long restrictionId, String reason) {
        Instant now = clock.instant();
        UserRestriction restriction = restrictionRepository.findById(restrictionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTRICTION_NOT_FOUND));
        restriction.lift(adminId, reason, now);
        auditLogRepository.save(AdminAuditLog.of(adminId, AdminAction.RESTRICTION_LIFT, TARGET_RESTRICTION, restrictionId,
                reason.strip()));
        restrictionRepository.flush();
        User user = userRepository.findById(restriction.getUserId()).orElseThrow();
        return RestrictionResponse.of(restriction.getId(), user.getId(), user.getStudentNo(), user.getName(),
                restriction.getStartAt(), restriction.getEndAt(), restriction.getReason(), restriction.getLiftedAt(),
                restriction.getLiftReason(), now);
    }
}
