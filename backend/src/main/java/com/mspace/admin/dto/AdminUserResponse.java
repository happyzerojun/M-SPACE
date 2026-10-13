package com.mspace.admin.dto;

import com.mspace.user.domain.UserRole;
import com.mspace.user.domain.UserStatus;
import java.time.OffsetDateTime;

/** 관리자용 회원 정보. 관리자라 전화번호를 마스킹하지 않아요 (NF-02) */
public record AdminUserResponse(
        Long id,
        String studentNo,
        String name,
        String phone,
        UserRole role,
        UserStatus status,
        boolean mustChangePassword,
        int recentNoShowCount,
        OffsetDateTime restrictedUntil,
        OffsetDateTime createdAt
) {
}
