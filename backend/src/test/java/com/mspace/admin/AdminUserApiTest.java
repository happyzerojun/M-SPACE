package com.mspace.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mspace.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** AD-07 회원 검색, AU-07 비밀번호 초기화, AU-08 계정 비활성화, AD-08 감사 기록 */
class AdminUserApiTest extends IntegrationTest {

    private TestUser admin;
    private TestUser student;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminLogin("99009000");
        student = signupAndLogin("99000001");
    }

    private String reason(String reason) {
        return """
                {"reason":"%s"}""".formatted(reason);
    }

    @Test
    void 학생은_관리자_회원_API_403_비로그인은_401() throws Exception {
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/{id}/password-reset", admin.id())
                        .header(HttpHeaders.AUTHORIZATION, student.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(reason("x")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void 학번_앞부분이나_이름으로_검색_학번순() throws Exception {
        signupAndLogin("99000002");
        jdbcTemplate.update("UPDATE users SET name = '박지훈' WHERE student_no = '99000002'");
        signupAndLogin("98000001");

        mvc.perform(get("/api/admin/users").param("q", "9900").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].studentNo").value("99000001"))
                .andExpect(jsonPath("$.content[0].phone").value("010-0000-0000"))   // 관리자는 마스킹 없음
                .andExpect(jsonPath("$.content[0].recentNoShowCount").value(0))
                .andExpect(jsonPath("$.content[0].restrictedUntil").doesNotExist());

        mvc.perform(get("/api/admin/users").param("q", "지훈").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].studentNo").value("99000002"));
    }

    @Test
    void 검색어의_퍼센트_밑줄은_글자_그대로_찾음() throws Exception {
        mvc.perform(get("/api/admin/users").param("q", "%").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 목록_size는_100으로_제한_page는_1부터() throws Exception {
        mvc.perform(get("/api/admin/users").param("size", "500").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.page").value(1));
        mvc.perform(get("/api/admin/users").param("page", "0").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비밀번호_초기화_임시_비밀번호로_로그인하면_변경_전까지_제한() throws Exception {
        String body = mvc.perform(post("/api/admin/users/{id}/password-reset", student.id())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(reason("학과실 방문, 학생증 확인")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString();
        String temp = JsonPath.read(body, "$.temporaryPassword");
        assertThat(temp).hasSize(10).matches(".*[A-Za-z].*").matches(".*\\d.*");

        TestUser relogin = login("99000001", temp);
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, relogin.bearer()))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        // 감사 기록에는 처리자·대상·사유만 있고 비밀번호는 없어요
        Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM admin_audit_log");
        assertThat(log).containsEntry("action", "PASSWORD_RESET").containsEntry("actor_id", admin.id())
                .containsEntry("target_id", student.id()).containsEntry("reason", "학과실 방문, 학생증 확인");
        assertThat(log.values().toString()).doesNotContain(temp);
    }

    @Test
    void 사유가_없으면_400_없는_회원은_404() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/password-reset", student.id())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(reason(" ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        mvc.perform(post("/api/admin/users/{id}/password-reset", 999999)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(reason("x")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 비활성화하면_로그인과_기존_토큰_모두_거부_두_번_눌러도_기록은_하나() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/admin/users/{id}/disable", student.id())
                            .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(reason("다른 사람이 학번 도용")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DISABLED"));
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM admin_audit_log WHERE action = 'USER_DISABLE'", Long.class)).isEqualTo(1);

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"studentNo":"99000001","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void 본인_계정은_비활성화할_수_없음() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/disable", admin.id())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(reason("실수")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_DISABLE_SELF"));
    }

    @Test
    void 목록에_최근_노쇼_수와_이용_제한_종료_시각() throws Exception {
        Long room = roomId("1번방");
        jdbcTemplate.update("""
                INSERT INTO reservation (room_id, user_id, start_at, end_at, headcount, status, key_required,
                                         no_show_at, created_at, updated_at)
                VALUES (?, ?, date_trunc('hour', now()) - interval '2 day', date_trunc('hour', now()) - interval '2 day' + interval '1 hour',
                        1, 'NO_SHOW', TRUE, now(), now(), now())
                """, room, student.id());
        jdbcTemplate.update("""
                INSERT INTO user_restriction (user_id, start_at, end_at, reason, created_at)
                VALUES (?, now(), '2099-01-01T00:00:00+09:00', '노쇼 2회', now())
                """, student.id());

        mvc.perform(get("/api/admin/users").param("q", "99000001").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.content[0].recentNoShowCount").value(1))
                .andExpect(jsonPath("$.content[0].restrictedUntil").value("2099-01-01T00:00:00+09:00"));
    }
}
