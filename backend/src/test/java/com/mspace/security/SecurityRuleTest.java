package com.mspace.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mspace.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** SecurityConfig 주소 규칙과 401/403/404 에러 형식 (AD-01, NF-04). 기능 API가 생기기 전에도 규칙은 지켜져야 해요. */
class SecurityRuleTest extends IntegrationTest {

    @Test
    void 비로그인으로_관리자_API는_401_UNAUTHORIZED() throws Exception {
        mvc.perform(get("/api/admin/reservations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요해요."))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void 비로그인으로_로그인_필요_API는_401() throws Exception {
        mvc.perform(get("/api/me/reservations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 학생이_관리자_API를_부르면_403_FORBIDDEN() throws Exception {
        Long student = insertUser("990001", "STUDENT");
        mvc.perform(get("/api/admin/reservations").header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 관리자는_관리자_주소를_통과_없는_API는_404() throws Exception {
        Long admin = insertUser("990900", "ADMIN");
        mvc.perform(get("/api/admin/reservations").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/not-exists").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 현황판_방_목록_예약_규칙은_비로그인도_통과() throws Exception {
        mvc.perform(get("/api/board")).andExpect(status().isOk());
        mvc.perform(get("/api/rooms")).andExpect(status().isOk());
        mvc.perform(get("/api/policy")).andExpect(status().isOk());
    }

    @Test
    void 위조_토큰은_비회원으로_처리돼_401() throws Exception {
        mvc.perform(get("/api/me/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비활성화된_계정의_토큰은_거부() throws Exception {
        Long student = insertUser("990001", "STUDENT");
        String token = bearer(student);
        jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", student);

        mvc.perform(get("/api/me/reservations").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }
}
