package com.mspace.global.security;

import com.mspace.global.error.BusinessException;
import com.mspace.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리자가 초기화한 임시 비밀번호로 로그인했으면, 비밀번호를 바꾸기 전까지 내 정보 조회와 비밀번호 변경만 허용해요 (AU-07).
 * 임시 비밀번호는 관리자도 본 값이라, 바꾸지 않은 채 예약 등 다른 기능을 쓰게 두지 않아요.
 * 비회원 공개 API(현황판 등)는 로그인 여부와 상관없이 쓸 수 있어서 막지 않아요.
 */
@Component
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser me && me.mustChangePassword()
                && !isAllowed(request.getMethod(), request.getRequestURI())) {
            throw new BusinessException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
        return true;
    }

    private static boolean isAllowed(String method, String uri) {
        return ("GET".equals(method) && uri.equals("/api/me"))
                || ("PUT".equals(method) && uri.equals("/api/me/password"))
                || uri.startsWith("/api/auth/")
                || ("GET".equals(method) && (uri.equals("/api/board") || uri.equals("/api/rooms") || uri.equals("/api/policy")));
    }
}
