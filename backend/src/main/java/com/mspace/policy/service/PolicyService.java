package com.mspace.policy.service;

import com.mspace.policy.domain.OperationPeriod;
import com.mspace.policy.domain.OperationPolicy;
import com.mspace.policy.repository.OperationPeriodRepository;
import com.mspace.policy.repository.OperationPolicyRepository;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영 정책·운영 시간 조회 (PO-01, PO-05). 값은 모두 DB에서 읽어요(코드 상수 금지, CLAUDE.md 3장) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyService {

    private final OperationPolicyRepository policyRepository;
    private final OperationPeriodRepository periodRepository;

    /** 정책 행은 V1 마이그레이션이 넣어서 항상 있어요. 없으면 배포가 잘못된 거라 500으로 드러나게 둬요 */
    public OperationPolicy policy() {
        return policyRepository.findById(OperationPolicy.SINGLE_ID)
                .orElseThrow(() -> new IllegalStateException("operation_policy 행이 없어요"));
    }

    /** 그날의 운영 시간: 특별 운영 기간 → 기본 운영 시간 순 (D-4) */
    public DayHours hoursOf(LocalDate date) {
        return hoursOf(date, policy());
    }

    public DayHours hoursOf(LocalDate date, OperationPolicy policy) {
        Optional<OperationPeriod> period = periodRepository.findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date);
        if (period.isEmpty()) {
            return new DayHours(date, false, (int) policy.getDefaultOpenMinute(), (int) policy.getDefaultCloseMinute(), null, true);
        }
        OperationPeriod p = period.get();
        if (p.isClosed()) {
            // 휴관일은 예약을 받지 않아서 키 여부는 의미가 없지만, 화면이 헷갈리지 않게 키 필요(기본)로 둬요
            return new DayHours(date, true, null, null, p.getName(), true);
        }
        return new DayHours(date, false, p.getOpenMinute().intValue(), p.getCloseMinute().intValue(), p.getName(),
                p.isKeyRequired());
    }
}
