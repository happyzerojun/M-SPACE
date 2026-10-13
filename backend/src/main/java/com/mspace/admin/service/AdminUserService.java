package com.mspace.admin.service;

import com.mspace.admin.domain.AdminAction;
import com.mspace.admin.domain.AdminAuditLog;
import com.mspace.admin.dto.AdminUserResponse;
import com.mspace.admin.dto.PasswordResetResponse;
import com.mspace.admin.repository.AdminAuditLogRepository;
import com.mspace.admin.repository.AdminUserQueryRepository;
import com.mspace.global.common.PageCondition;
import com.mspace.global.common.PageResponse;
import com.mspace.global.error.BusinessException;
import com.mspace.global.error.ErrorCode;
import com.mspace.user.domain.User;
import com.mspace.user.domain.UserStatus;
import com.mspace.user.repository.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 회원 관리: 검색(AD-07), 비밀번호 초기화(AU-07), 계정 비활성화(AU-08). 조치마다 감사 기록(AD-08) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private static final String TARGET_USER = "USER";

    private final UserRepository userRepository;
    private final AdminUserQueryRepository adminUserQueryRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final Clock clock;

    public PageResponse<AdminUserResponse> search(String q, UserStatus status, PageCondition page) {
        return PageResponse.from(adminUserQueryRepository.search(q, status, page, clock.instant()));
    }

    /** 비활성 계정은 초기화 대상이 아니에요(로그인할 수 없으니까) → 404 */
    @Transactional
    public PasswordResetResponse resetPassword(Long adminId, Long userId, String reason) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String temporaryPassword = temporaryPasswordGenerator.generate();
        user.resetPassword(passwordEncoder.encode(temporaryPassword));
        auditLogRepository.save(AdminAuditLog.of(adminId, AdminAction.PASSWORD_RESET, TARGET_USER, userId, reason.strip()));
        return new PasswordResetResponse(temporaryPassword);
    }

    /**
     * 이미 비활성이면 그대로 돌려줘요(여러 번 눌러도 기록이 쌓이지 않게).
     * TODO(3단계, AU-08): 이 학생의 RESERVED 예약을 Reservation.cancelByAdmin()으로 함께 취소해요.
     */
    @Transactional
    public AdminUserResponse disable(Long adminId, Long userId, String reason) {
        if (adminId.equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_DISABLE_SELF);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isActive()) {
            user.disable();
            auditLogRepository.save(AdminAuditLog.of(adminId, AdminAction.USER_DISABLE, TARGET_USER, userId, reason.strip()));
            userRepository.flush();
        }
        return adminUserQueryRepository.findOne(userId, clock.instant());
    }
}
