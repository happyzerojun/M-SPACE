package com.mspace.policy.repository;

import com.mspace.policy.domain.OperationPeriod;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationPeriodRepository extends JpaRepository<OperationPeriod, Long> {

    /** 그날이 속한 특별 운영 기간. 기간끼리 겹치지 않아서(EXCLUDE) 많아야 1건이에요 */
    Optional<OperationPeriod> findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate date1, LocalDate date2);
}
