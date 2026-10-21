package com.mspace.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** 로그인·가입 요청 수 제한 (NF-03). 시각을 고정해서 1분 구간 경계를 확인해요 */
class AuthRateLimitFilterTest {

    private static final Instant T0 = Instant.parse("2026-10-21T00:00:00Z");

    private AuthRateLimitFilter filter(int limit, Instant now) {
        return new AuthRateLimitFilter(limit, Clock.fixed(now, ZoneOffset.UTC), JsonMapper.builder().build());
    }

    @Test
    void 같은_IP는_1분에_N번까지_다음_구간이면_다시_허용() {
        AuthRateLimitFilter f = filter(3, T0);
        long now = T0.toEpochMilli();
        assertThat(f.tryAcquire("10.0.0.1", now)).isTrue();
        assertThat(f.tryAcquire("10.0.0.1", now + 1_000)).isTrue();
        assertThat(f.tryAcquire("10.0.0.1", now + 2_000)).isTrue();
        assertThat(f.tryAcquire("10.0.0.1", now + 3_000)).isFalse();
        assertThat(f.tryAcquire("10.0.0.2", now + 3_000)).isTrue();          // 다른 IP는 따로 세요
        assertThat(f.tryAcquire("10.0.0.1", now + 60_000)).isTrue();         // 다음 1분 구간
    }

    @Test
    void 넘으면_429와_에러_형식_로그인이_아닌_요청은_세지_않음() throws Exception {
        AuthRateLimitFilter f = filter(1, T0);
        assertThat(run(f, "POST", "/api/auth/login").getStatus()).isEqualTo(200);
        MockHttpServletResponse blocked = run(f, "POST", "/api/auth/login");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("\"code\":\"TOO_MANY_REQUESTS\"");

        assertThat(run(f, "GET", "/api/board").getStatus()).isEqualTo(200);
        assertThat(run(f, "POST", "/api/reservations").getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse run(AuthRateLimitFilter f, String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("10.0.0.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        f.doFilter(request, response, chain);
        return response;
    }
}
