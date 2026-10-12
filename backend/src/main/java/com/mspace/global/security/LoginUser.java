package com.mspace.global.security;

import com.mspace.user.domain.UserRole;

/**
 * 로그인한 회원. 컨트롤러에서 @AuthenticationPrincipal LoginUser me 로 받아요.
 * 학번·이름은 넣지 않아요 → 실수로 로그에 찍혀도 개인정보가 새지 않게 (NF-01).
 */
public record LoginUser(Long id, UserRole role, boolean mustChangePassword) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
