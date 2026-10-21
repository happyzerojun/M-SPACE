package com.mspace.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mspace.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 현황판·방 목록·예약 규칙 (BD-01~05). 비로그인 공개라 예약자 정보가 새지 않는지가 핵심이에요 (BD-02) */
class BoardApiTest extends IntegrationTest {

    @Autowired
    private EntityManagerFactory emf;

    private TestUser student;

    @BeforeEach
    void setUp() throws Exception {
        student = signupAndLogin("99000001");
        jdbcTemplate.update("UPDATE users SET name = '김비밀', phone = '010-1111-2222' WHERE id = ?", student.id());
        clock.set(at(1, "08:00"));
    }

    private void reserve(TestUser user, String room, Instant start, Instant end) throws Exception {
        mvc.perform(post("/api/reservations").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(reservationBody(roomId(room), start, end, 2)))
                .andExpect(status().isCreated());
    }

    @Test
    void 비로그인도_현황판을_보고_예약자_정보는_없음() throws Exception {
        reserve(student, "1번방", at(1, "14:00"), at(1, "16:00"));

        String body = mvc.perform(get("/api/board").param("date", day(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(day(1).toString()))
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.openAt").value(iso(at(1, "09:00"))))
                .andExpect(jsonPath("$.closeAt").value(iso(at(1, "18:00"))))
                .andExpect(jsonPath("$.keyRequired").value(true))
                .andExpect(jsonPath("$.rooms.length()").value(5))
                .andExpect(jsonPath("$.rooms[0].name").value("1번방"))
                .andExpect(jsonPath("$.rooms[0].blocks[0].startAt").value(iso(at(1, "14:00"))))
                .andExpect(jsonPath("$.rooms[0].blocks[0].status").value("RESERVED"))
                .andExpect(jsonPath("$.rooms[1].blocks.length()").value(0))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("99000001", "김비밀", "010-1111-2222", "user");
    }

    @Test
    void 날짜를_빼면_오늘_범위_밖이면_400() throws Exception {
        mvc.perform(get("/api/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(day(1).toString()));
        mvc.perform(get("/api/board").param("date", day(9).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVATION_OUT_OF_WINDOW"));
        mvc.perform(get("/api/board").param("date", day(0).toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/board").param("date", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 취소된_예약은_현황판에서_빠짐() throws Exception {
        reserve(student, "1번방", at(1, "14:00"), at(1, "16:00"));
        jdbcTemplate.update("UPDATE reservation SET status = 'CANCELLED', cancelled_at = now()");
        mvc.perform(get("/api/board").param("date", day(1).toString()))
                .andExpect(jsonPath("$.rooms[0].blocks.length()").value(0));
    }

    @Test
    void 개방_기간이면_키_필요_없음과_기간_이름을_보여줌() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO operation_period (name, start_date, end_date, closed, key_required, open_minute, close_minute,
                                              created_at, updated_at)
                VALUES ('중간고사 개방', ?, ?, false, false, 0, 1440, now(), now())
                """, day(2), day(3));
        mvc.perform(get("/api/board").param("date", day(2).toString()))
                .andExpect(jsonPath("$.keyRequired").value(false))
                .andExpect(jsonPath("$.periodName").value("중간고사 개방"))
                .andExpect(jsonPath("$.openAt").value(iso(at(2, "00:00"))))
                .andExpect(jsonPath("$.closeAt").value(iso(at(3, "00:00"))));
    }

    @Test
    void 현황판_쿼리_수는_예약_수와_상관없이_같음() throws Exception {
        reserve(student, "1번방", at(1, "14:00"), at(1, "16:00"));
        long one = countStatements();

        for (int i = 2; i <= 4; i++) {
            TestUser other = signupAndLogin("9900000" + i);
            reserve(other, i + "번방", at(1, "10:00"), at(1, "12:00"));
        }
        long four = countStatements();

        // 30초마다 폴링되는 화면이라 N+1이 생기면 안 돼요 (NF-07)
        assertThat(four).isEqualTo(one);
    }

    @Test
    void 방_목록과_예약_규칙은_비로그인도_봄() throws Exception {
        mvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[4].name").value("5번방"))
                .andExpect(jsonPath("$[4].capacity").value(8));
        mvc.perform(get("/api/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotMinutes").value(30))
                .andExpect(jsonPath("$.maxDurationMinutes").value(120))
                .andExpect(jsonPath("$.dailyLimit").value(1))
                .andExpect(jsonPath("$.bookingWindowDays").value(7))
                .andExpect(jsonPath("$.noShowGraceMinutes").value(15))
                .andExpect(jsonPath("$.keyIssueBeforeMinutes").value(10))
                .andExpect(jsonPath("$.penaltyThreshold").value(2));
    }

    private long countStatements() throws Exception {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        mvc.perform(get("/api/board").param("date", day(1).toString())).andExpect(status().isOk());
        return stats.getPrepareStatementCount();
    }
}
