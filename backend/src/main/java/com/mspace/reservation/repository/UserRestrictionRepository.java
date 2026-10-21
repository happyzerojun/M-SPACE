package com.mspace.reservation.repository;

import com.mspace.reservation.domain.UserRestriction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRestrictionRepository extends JpaRepository<UserRestriction, Long> {

    /** 지금 유효한 제한 (해제 안 됨, start ≤ now < end). 겹치지 않게 막혀 있어서 보통 0~1건이에요 */
    @Query("""
            select ur from UserRestriction ur
            where ur.userId = :userId and ur.liftedAt is null and ur.startAt <= :now and ur.endAt > :now
            order by ur.endAt desc
            """)
    List<UserRestriction> findActive(@Param("userId") Long userId, @Param("now") Instant now);

    /** 마지막 제한: 이미 제한에 쓰인 노쇼를 다시 세지 않으려고 세는 구간의 시작으로 써요 (설계서 4.1) */
    Optional<UserRestriction> findFirstByUserIdOrderByStartAtDesc(Long userId);
}
