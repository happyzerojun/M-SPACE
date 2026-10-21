package com.mspace.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mspace.IntegrationTest;
import com.mspace.reservation.service.NoShowService;
import com.mspace.reservation.service.NoShowService.Result;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 노쇼(NS-01)·노쇼 제재(NS-02)·제한 해제(NS-03)·개방 기간 자동 이용 종료(OP-03) — docs/예약-상태-설계.md 4장.
 * 스케줄러는 테스트에서 꺼 두고 시각을 옮겨 가며 runOnce()를 직접 불러요.
 */
class NoShowSchedulerTest extends IntegrationTest {

    @Autowired
    private NoShowService noShowService;

    private TestUser admin;
    private TestUser student;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminLogin("99009000");
        student = signupAndLogin("99000001");
        clock.set(at(1, "08:00"));
    }

    private ResultActions reserve(Instant start, Instant end) throws Exception {
        return mvc.perform(post("/api/reservations").header(HttpHeaders.AUTHORIZATION, student.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(reservationBody(roomId("1번방"), start, end, 2)));
    }

    private Long reserveOk(Instant start, Instant end) throws Exception {
        String body = reserve(start, end).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String statusOf(Long id) {
        return jdbcTemplate.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, id);
    }

    @Test
    void 시작_15분이_지나도록_키를_안_받으면_노쇼_여러_번_돌아도_결과가_같음() throws Exception {
        Long id = reserveOk(at(1, "14:00"), at(1, "16:00"));

        clock.set(at(1, "14:14"));
        assertThat(noShowService.runOnce().noShows()).isZero();
        assertThat(statusOf(id)).isEqualTo("RESERVED");

        clock.set(at(1, "14:15"));
        assertThat(noShowService.runOnce().noShows()).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo("NO_SHOW");
        assertThat(noShowService.runOnce()).isEqualTo(new Result(0, 0, 0));
    }

    @Test
    void 키를_받은_예약은_노쇼가_아님() throws Exception {
        Long id = reserveOk(at(1, "14:00"), at(1, "16:00"));
        clock.set(at(1, "13:55"));
        mvc.perform(post("/api/admin/reservations/{id}/key-issue", id).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"identityVerified\":true}"))
                .andExpect(status().isOk());

        clock.set(at(1, "14:30"));
        assertThat(noShowService.runOnce().noShows()).isZero();
        assertThat(statusOf(id)).isEqualTo("IN_USE");
    }

    @Test
    void 이주_안에_노쇼_2회면_7일_제한_해제하면_다시_예약할_수_있음() throws Exception {
        reserveOk(at(1, "14:00"), at(1, "16:00"));
        reserveOk(at(2, "10:00"), at(2, "11:00"));

        clock.set(at(1, "14:20"));
        assertThat(noShowService.runOnce()).isEqualTo(new Result(1, 0, 0));
        clock.set(at(2, "10:20"));
        assertThat(noShowService.runOnce()).isEqualTo(new Result(1, 1, 0));

        reserve(at(3, "10:00"), at(3, "11:00"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_RESTRICTED"));
        mvc.perform(get("/api/admin/users").param("q", "99000001").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.content[0].recentNoShowCount").value(2))
                .andExpect(jsonPath("$.content[0].restrictedUntil").value(iso(at(9, "10:20"))));

        String list = mvc.perform(get("/api/admin/restrictions").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].studentNo").value("99000001"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andReturn().getResponse().getContentAsString();
        Long restrictionId = ((Number) JsonPath.read(list, "$.content[0].id")).longValue();

        mvc.perform(post("/api/admin/restrictions/{id}/lift", restrictionId).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"사정 확인\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.liftReason").value("사정 확인"));
        mvc.perform(post("/api/admin/restrictions/{id}/lift", restrictionId).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"다시\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESTRICTION_STATE"));
        mvc.perform(get("/api/admin/restrictions").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.totalElements").value(0));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM admin_audit_log WHERE action = 'RESTRICTION_LIFT'", Long.class)).isEqualTo(1);

        // 해제 뒤에는 예약할 수 있고, 이미 제한에 쓰인 노쇼는 다시 세지 않아요 → 새 노쇼 1회로는 다시 제한되지 않아요
        Long third = reserveOk(at(3, "14:00"), at(3, "15:00"));
        clock.set(at(3, "14:20"));
        assertThat(noShowService.runOnce()).isEqualTo(new Result(1, 0, 0));
        assertThat(statusOf(third)).isEqualTo("NO_SHOW");
    }

    @Test
    void 학생은_이용_제한_API_403() throws Exception {
        mvc.perform(get("/api/admin/restrictions").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 개방_기간_예약은_체크인을_안_하면_노쇼_체크인_후_종료를_안_누르면_종료_시각에_자동_종료() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO operation_period (name, start_date, end_date, closed, key_required, open_minute, close_minute,
                                              created_at, updated_at)
                VALUES ('시험기간 개방', ?, ?, false, false, 0, 1440, now(), now())
                """, day(1), day(2));
        Long checkedIn = reserveOk(at(1, "10:00"), at(1, "12:00"));
        Long missed = reserveOk(at(2, "10:00"), at(2, "12:00"));

        clock.set(at(1, "09:55"));
        mvc.perform(post("/api/reservations/{id}/check-in", checkedIn).header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isOk());

        clock.set(at(1, "11:59"));
        assertThat(noShowService.runOnce().completed()).isZero();
        clock.set(at(1, "12:00"));
        assertThat(noShowService.runOnce().completed()).isEqualTo(1);
        assertThat(statusOf(checkedIn)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT completed_at = end_at FROM reservation WHERE id = ?", Boolean.class,
                checkedIn)).isTrue();

        clock.set(at(2, "10:15"));
        assertThat(noShowService.runOnce().noShows()).isEqualTo(1);
        assertThat(statusOf(missed)).isEqualTo("NO_SHOW");
    }
}
