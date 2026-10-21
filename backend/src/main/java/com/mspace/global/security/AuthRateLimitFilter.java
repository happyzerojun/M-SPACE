package com.mspace.global.security;

import com.mspace.global.error.ErrorCode;
import com.mspace.global.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * 로그인·가입 요청 수 제한 (NF-03 보안). 같은 IP에서 1분에 N번을 넘으면 429예요.
 * 비밀번호 무작위 대입(학번은 추측하기 쉬워요)과 가입 남용을 늦춰요. 서버 한 대 운영이라 메모리에 세요.
 *
 * <p>IP는 리버스 프록시(Caddy·Nginx)가 넘겨준 X-Forwarded-For를 Tomcat이 반영한 값이에요
 * (application.yaml의 server.forward-headers-strategy=native). 그래서 프록시를 거치지 않고 직접 붙는 경로는 막아 둬요(배포 가이드).
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    /** 기록이 너무 많아지면 지난 구간 기록을 지워요 (메모리 상한) */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int limitPerMinute;
    private final Clock clock;
    private final JsonMapper jsonMapper;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(@Value("${mspace.rate-limit.auth-per-minute:20}") int limitPerMinute, Clock clock,
                               JsonMapper jsonMapper) {
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !"POST".equals(request.getMethod())
                || !(uri.equals("/api/auth/login") || uri.equals("/api/auth/signup"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!tryAcquire(request.getRemoteAddr(), clock.millis())) {
            response.setStatus(ErrorCode.TOO_MANY_REQUESTS.getStatus().value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            jsonMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.TOO_MANY_REQUESTS));
            return;
        }
        chain.doFilter(request, response);
    }

    /** 1분 고정 구간마다 IP별로 세요. 구간이 바뀌면 처음부터 다시 세요 */
    boolean tryAcquire(String ip, long nowMillis) {
        long windowStart = nowMillis - (nowMillis % WINDOW_MILLIS);
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> e.getValue().start < windowStart);
        }
        Window window = windows.compute(ip, (key, old) ->
                old == null || old.start != windowStart ? new Window(windowStart) : old);
        return window.count.incrementAndGet() <= limitPerMinute;
    }

    private static final class Window {
        private final long start;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long start) {
            this.start = start;
        }
    }
}
