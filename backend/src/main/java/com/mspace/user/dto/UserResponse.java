package com.mspace.user.dto;

import com.mspace.user.domain.User;
import com.mspace.user.domain.UserRole;

/** 본인에게 보여주는 회원 정보. 본인이라 전화번호를 마스킹하지 않아요 (API 명세서 1장) */
public record UserResponse(
        Long id,
        String studentNo,
        String name,
        String phone,
        UserRole role,
        boolean mustChangePassword
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getStudentNo(), user.getName(), user.getPhone(),
                user.getRole(), user.isMustChangePassword());
    }
}
