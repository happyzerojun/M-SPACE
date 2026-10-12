package com.mspace.global.security;

import com.mspace.user.domain.User;
import com.mspace.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 Bearer 토큰을 확인해서 로그인 상태로 만들어요.
 * 토큰이 없거나 잘못됐거나 비활성 계정이면 아무것도 하지 않고 비회원으로 넘겨요. 막는 건 SecurityConfig의 주소 규칙이 해요.
 *
 * @Component를 붙이지 않아요. 붙이면 Spring Boot가 일반 서블릿 필터로도 등록해서 두 번 실행돼요.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            tokenProvider.parseUserId(header.substring(BEARER_PREFIX.length()))
                    .flatMap(userRepository::findById)
                    .filter(User::isActive)          // 비활성화된 계정의 토큰은 만료 전이라도 거부 (AU-08)
                    .ifPresent(this::authenticate);
        }

        chain.doFilter(request, response);
    }

    private void authenticate(User user) {
        LoginUser principal = new LoginUser(user.getId(), user.getRole(), user.isMustChangePassword());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
