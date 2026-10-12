package com.mspace.global.security;

import com.mspace.global.error.ErrorCode;
import com.mspace.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Security 필터에서 막힌 요청은 컨트롤러에 도착하지 않아서 GlobalExceptionHandler가 처리하지 못해요.
 * 그래서 여기서 같은 ErrorResponse 형태로 401, 403을 직접 써요.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;  // Spring Boot 4 = Jackson 3 (패키지가 tools.jackson이에요)

    /** 로그인이 필요한 주소에 비회원이 들어옴 → 401 */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException e) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED);
    }

    /** 로그인은 했지만 권한이 없음 (예: 학생이 /api/admin/**) → 403 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException e) throws IOException {
        write(response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(), ErrorResponse.of(errorCode));
    }
}
