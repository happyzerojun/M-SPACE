package com.mspace.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mspace.IntegrationTest;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 관리자 오늘 화면·예약 검색·키 지급/반납·관리자 취소 (AD-02~04, KY-01~07).
 * "지금"은 내일 08:00(KST), 기본 예약은 1번방 14:00~16:00이에요. 키는 시작 10분 전부터 시작 15분 후까지 줄 수 있어요.
 */
class AdminReservationApiTest extends IntegrationTest {

    private TestUser admin;
    private TestUser student;
    private Long reservationId;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminLogin("99009000");
        student = signupAndLogin("99000001");
        jdbcTemplate.update("UPDATE users SET phone = '010-1234-5678' WHERE id = ?", student.id());
        clock.set(at(1, "08:00"));
        reservationId = reserve(student, "1번방", at(1, "14:00"), at(1, "16:00"));
    }

    private Long reserve(TestUser user, String room, Instant start, Instant end) throws Exception {
        String body = mvc.perform(post("/api/reservations").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(reservationBody(roomId(room), start, end, 2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions issueKey(Long id, boolean identityVerified) throws Exception {
        return mvc.perform(post("/api/admin/reservations/{id}/key-issue", id).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"identityVerified":%s}""".formatted(identityVerified)));
    }

    private ResultActions returnKey(Long id) throws Exception {
        return mvc.perform(post("/api/admin/reservations/{id}/key-return", id).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    @Test
    void 학생은_관리자_예약_API_403_비로그인은_401() throws Exception {
        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/reservations/{id}/key-issue", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, student.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"identityVerified\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reservations")).andExpect(status().isUnauthorized());
    }

    @Test
    void 키_지급은_학생증_확인과_지급_가능_시간이_필요하고_두_번은_안_됨() throws Exception {
        clock.set(at(1, "13:55"));
        issueKey(reservationId, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_NOT_VERIFIED"));

        clock.set(at(1, "13:49"));
        issueKey(reservationId, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KEY_ISSUE_NOT_ALLOWED_TIME"))
                .andExpect(jsonPath("$.message").value("키는 시작 10분 전부터 시작 15분 후까지 지급할 수 있어요."));

        clock.set(at(1, "13:50"));
        issueKey(reservationId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_USE"))
                .andExpect(jsonPath("$.keyIssuedAt").value(iso(at(1, "13:50"))));
        issueKey(reservationId, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"));

        Map<String, Object> log = jdbcTemplate.queryForMap("SELECT * FROM key_log");
        assertThat(log).containsEntry("action", "ISSUE").containsEntry("admin_id", admin.id())
                .containsEntry("identity_verified", true).containsEntry("reservation_id", reservationId);
    }

    @Test
    void 종료_시각이_지나면_연체로_보이고_반납하면_연체_반납으로_기록() throws Exception {
        clock.set(at(1, "13:55"));
        issueKey(reservationId, true).andExpect(status().isOk());

        clock.set(at(1, "16:10"));
        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.overdue").value(1))
                .andExpect(jsonPath("$.counts.inUse").value(0))
                .andExpect(jsonPath("$.overdue[0].id").value(reservationId))
                .andExpect(jsonPath("$.overdue[0].displayStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.overdue[0].user.phone").value("010-1234-5678"));   // 관리자는 연락하려고 전체 번호를 봐요
        mvc.perform(get("/api/board").param("date", day(1).toString()))
                .andExpect(jsonPath("$.rooms[0].blocks[0].status").value("OVERDUE"));

        returnKey(reservationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.returnedLate").value(true));
        assertThat(jdbcTemplate.queryForObject("SELECT late FROM key_log WHERE action = 'RETURN'", Boolean.class)).isTrue();
    }

    @Test
    void 종료_전에_반납하면_연체가_아니고_남은_시간은_다른_사람이_예약할_수_있음() throws Exception {
        clock.set(at(1, "13:55"));
        issueKey(reservationId, true).andExpect(status().isOk());
        clock.set(at(1, "15:00"));
        mvc.perform(post("/api/admin/reservations/{id}/key-return", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))   // 본문 없이도 돼요
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnedLate").value(false));

        // RETURNED는 겹침 검사 대상이 아니라서 같은 방 남은 시간(15:30~16:00)을 다른 학생이 잡을 수 있어요 (설계서 1장)
        TestUser other = signupAndLogin("99000002");
        reserve(other, "1번방", at(1, "15:30"), at(1, "16:00"));
    }

    @Test
    void 관리자_취소는_사유가_필요하고_감사_기록에_남음() throws Exception {
        mvc.perform(post("/api/admin/reservations/{id}/cancel", reservationId).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/reservations/{id}/cancel", reservationId).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"시설 점검\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("시설 점검"));
        mvc.perform(post("/api/admin/reservations/{id}/cancel", reservationId).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"다시\"}"))
                .andExpect(status().isConflict());

        Map<String, Object> audit = jdbcTemplate.queryForMap("SELECT * FROM admin_audit_log");
        assertThat(audit).containsEntry("action", "RESERVATION_CANCEL").containsEntry("target_id", reservationId)
                .containsEntry("reason", "시설 점검");
    }

    @Test
    void 오늘_화면은_상태별_수와_키_지급_대기를_시작_순으로_보여줌() throws Exception {
        TestUser other = signupAndLogin("99000002");
        Long early = reserve(other, "2번방", at(1, "10:00"), at(1, "11:00"));

        mvc.perform(get("/api/admin/dashboard").param("date", day(1).toString()).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(day(1).toString()))
                .andExpect(jsonPath("$.counts.reserved").value(2))
                .andExpect(jsonPath("$.pendingIssue.length()").value(2))
                .andExpect(jsonPath("$.pendingIssue[0].id").value(early))
                .andExpect(jsonPath("$.pendingIssue[1].id").value(reservationId));
    }

    @Test
    void 예약_검색과_상세의_키_이력() throws Exception {
        TestUser other = signupAndLogin("99000002");
        reserve(other, "2번방", at(1, "10:00"), at(1, "11:00"));

        mvc.perform(get("/api/admin/reservations").param("date", day(1).toString()).param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].room.name").value("2번방"));
        mvc.perform(get("/api/admin/reservations").param("date", day(1).toString()).param("q", "99000001")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].user.phone").value("010-1234-5678"));
        mvc.perform(get("/api/admin/reservations").param("from", day(1).toString()).param("to", day(100).toString())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isBadRequest());

        clock.set(at(1, "13:55"));
        issueKey(reservationId, true).andExpect(status().isOk());
        mvc.perform(get("/api/admin/reservations/{id}", reservationId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId))
                .andExpect(jsonPath("$.status").value("IN_USE"))
                .andExpect(jsonPath("$.keyLogs.length()").value(1))
                .andExpect(jsonPath("$.keyLogs[0].action").value("ISSUE"))
                .andExpect(jsonPath("$.keyLogs[0].studentNo").value("99000001"))
                .andExpect(jsonPath("$.keyLogs[0].identityVerified").value(true));
        mvc.perform(get("/api/admin/key-logs").param("q", "99000001").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].roomName").value("1번방"));
        mvc.perform(get("/api/admin/reservations/{id}", 999999).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 계정을_비활성화하면_키를_받기_전_예약은_함께_취소() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/disable", student.id()).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"학번 도용\"}"))
                .andExpect(status().isOk());
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT status, cancel_reason, cancelled_by FROM reservation WHERE id = ?",
                reservationId);
        assertThat(row).containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "계정 비활성화")
                .containsEntry("cancelled_by", admin.id());
    }
}
