package com.mspace.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mspace.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 예약 신청 검사 순서(설계서 5장)와 내 예약·취소·개방 기간 체크인 (RV-01~12, OP-02~03).
 * "지금"은 내일 08:00(KST)으로 고정해요. 기본 운영 09:00~18:00, 최대 2시간, 하루 1건, 7일 앞까지.
 */
class ReservationApiTest extends IntegrationTest {

    private TestUser student;
    private Long room1;
    private Long room2;

    @BeforeEach
    void setUp() throws Exception {
        student = signupAndLogin("99000001");
        room1 = roomId("1번방");
        room2 = roomId("2번방");
        clock.set(at(1, "08:00"));
    }

    private ResultActions reserve(TestUser user, Long roomId, Instant start, Instant end, int headcount) throws Exception {
        return mvc.perform(post("/api/reservations").header(HttpHeaders.AUTHORIZATION, user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(reservationBody(roomId, start, end, headcount)));
    }

    private Long reserveOk(TestUser user, Long roomId, Instant start, Instant end) throws Exception {
        String body = reserve(user, roomId, start, end, 2).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions action(TestUser user, Long id, String action) throws Exception {
        return mvc.perform(post("/api/reservations/{id}/" + action, id).header(HttpHeaders.AUTHORIZATION, user.bearer()));
    }

    private void insertPeriod(LocalDate date, boolean closed, boolean keyRequired, Integer open, Integer close) {
        jdbcTemplate.update("""
                INSERT INTO operation_period (name, start_date, end_date, closed, key_required, open_minute, close_minute,
                                              created_at, updated_at)
                VALUES ('테스트 기간', ?, ?, ?, ?, ?, ?, now(), now())
                """, date, date, closed, keyRequired, open, close);
    }

    @Test
    void 신청하면_즉시_확정_201과_Location() throws Exception {
        reserve(student, room1, at(1, "14:00"), at(1, "16:00"), 3)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/reservations/")))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.displayStatus").value("RESERVED"))
                .andExpect(jsonPath("$.keyRequired").value(true))
                .andExpect(jsonPath("$.room.name").value("1번방"))
                .andExpect(jsonPath("$.startAt").value(iso(at(1, "14:00"))))
                .andExpect(jsonPath("$.endAt").value(iso(at(1, "16:00"))))
                .andExpect(jsonPath("$.headcount").value(3))
                .andExpect(jsonPath("$.user.studentNo").value("99000001"))
                .andExpect(jsonPath("$.user.phone").value("010-0000-0000"));   // 본인이라 마스킹 없음
    }

    @Test
    void 비로그인은_401() throws Exception {
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(room1, at(1, "14:00"), at(1, "16:00"), 2)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/reservations")).andExpect(status().isUnauthorized());
    }

    @Test
    void 삼십분_단위가_아니거나_종료가_시작보다_빠르면_400() throws Exception {
        reserve(student, room1, at(1, "14:10"), at(1, "16:00"), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("startAt"));
        reserve(student, room1, at(1, "16:00"), at(1, "14:00"), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("endAt"));
        mvc.perform(post("/api/reservations").header(HttpHeaders.AUTHORIZATION, student.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"roomId":%d,"startAt":"%s","endAt":"%s"}""".formatted(room1, iso(at(1, "14:00")), iso(at(1, "15:00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("headcount"));
    }

    @Test
    void 없는_방은_404_비활성_방은_409() throws Exception {
        reserve(student, 9999L, at(1, "14:00"), at(1, "15:00"), 2)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        jdbcTemplate.update("UPDATE room SET active = false WHERE id = ?", room2);
        try {
            reserve(student, room2, at(1, "14:00"), at(1, "15:00"), 2)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROOM_INACTIVE"));
        } finally {
            jdbcTemplate.update("UPDATE room SET active = true WHERE id = ?", room2);
        }
    }

    @Test
    void 정원을_넘으면_400_정원까지는_성공() throws Exception {
        reserve(student, room1, at(1, "14:00"), at(1, "15:00"), 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HEADCOUNT_EXCEEDS_CAPACITY"))
                .andExpect(jsonPath("$.message").value("이 방은 최대 4명까지 이용할 수 있어요."));
        reserve(student, room1, at(1, "14:00"), at(1, "15:00"), 4).andExpect(status().isCreated());
    }

    @Test
    void 지난_시각은_400_예약_가능_기간은_오늘부터_7일_뒤까지() throws Exception {
        reserve(student, room1, at(1, "07:30"), at(1, "08:30"), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVATION_IN_PAST"));
        reserve(student, room1, at(9, "10:00"), at(9, "11:00"), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVATION_OUT_OF_WINDOW"))
                .andExpect(jsonPath("$.message").value("오늘부터 7일 뒤까지만 예약할 수 있어요."));
        reserve(student, room1, at(8, "10:00"), at(8, "11:00"), 2).andExpect(status().isCreated());
    }

    @Test
    void 운영_시간_밖은_400_경계는_성공() throws Exception {
        reserve(student, room1, at(1, "08:30"), at(1, "09:30"), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OUTSIDE_OPERATING_HOURS"))
                .andExpect(jsonPath("$.message").value("운영 시간(09:00~18:00) 안에서만 예약할 수 있어요."));
        reserve(student, room1, at(1, "17:00"), at(1, "18:30"), 2)
                .andExpect(jsonPath("$.code").value("OUTSIDE_OPERATING_HOURS"));
        reserve(student, room1, at(1, "16:00"), at(1, "18:00"), 2).andExpect(status().isCreated());
    }

    @Test
    void 마감이_24시면_자정까지_예약되고_자정을_넘기면_400() throws Exception {
        insertPeriod(day(1), false, true, 0, 1440);
        reserve(student, room1, at(1, "23:00"), at(2, "00:30"), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OUTSIDE_OPERATING_HOURS"))
                .andExpect(jsonPath("$.message").value("운영 시간(00:00~24:00) 안에서만 예약할 수 있어요."));
        reserve(student, room1, at(1, "22:00"), at(2, "00:00"), 2).andExpect(status().isCreated());
    }

    @Test
    void 휴관일은_400() throws Exception {
        insertPeriod(day(2), true, true, null, null);
        reserve(student, room1, at(2, "10:00"), at(2, "11:00"), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLOSED_DAY"));
    }

    @Test
    void 최대_2시간을_넘으면_400() throws Exception {
        reserve(student, room1, at(1, "14:00"), at(1, "16:30"), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVATION_TOO_LONG"))
                .andExpect(jsonPath("$.message").value("한 번에 최대 2시간까지 예약할 수 있어요."));
    }

    @Test
    void 하루_한도를_넘으면_409_취소하면_다시_신청할_수_있음() throws Exception {
        Long id = reserveOk(student, room1, at(1, "10:00"), at(1, "11:00"));
        reserve(student, room2, at(1, "13:00"), at(1, "14:00"), 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("하루에 1건까지 예약할 수 있어요."));

        action(student, id, "cancel").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        reserve(student, room2, at(1, "13:00"), at(1, "14:00"), 2).andExpect(status().isCreated());
    }

    @Test
    void 같은_방_겹치는_시간은_409_바로_이어지는_시간은_성공() throws Exception {
        TestUser other = signupAndLogin("99000002");
        reserveOk(student, room1, at(1, "14:00"), at(1, "16:00"));
        reserve(other, room1, at(1, "15:00"), at(1, "16:00"), 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_TIME_CONFLICT"));
        // [start, end) 범위라 16:00 시작은 겹치지 않아요
        reserve(other, room1, at(1, "16:00"), at(1, "17:00"), 2).andExpect(status().isCreated());
    }

    @Test
    void 같은_학생이_같은_시간에_다른_방을_잡으면_409() throws Exception {
        jdbcTemplate.update("UPDATE operation_policy SET daily_limit = 2");
        reserveOk(student, room1, at(1, "14:00"), at(1, "16:00"));
        reserve(student, room2, at(1, "15:00"), at(1, "16:00"), 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_TIME_CONFLICT"));
    }

    @Test
    void 같은_방_같은_시간에_8명이_동시에_신청하면_1건만_성공() throws Exception {
        int n = 8;
        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            users.add(signupAndLogin("9900010" + i));
        }
        List<Integer> statuses = runConcurrently(users.stream()
                .map(u -> (Callable<Integer>) () -> reserve(u, room1, at(1, "14:00"), at(1, "16:00"), 2)
                        .andReturn().getResponse().getStatus())
                .toList());

        // 검사를 통과한 동시 요청도 DB EXCLUDE 제약이 막고, 500이 아니라 409로 안내해요 (RV-03, TS-16)
        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reservation WHERE status IN ('RESERVED', 'IN_USE')", Long.class)).isEqualTo(1);
    }

    @Test
    void 같은_학생이_동시에_두_건을_신청하면_하루_한도로_1건만_성공() throws Exception {
        List<Integer> statuses = runConcurrently(List.of(
                () -> reserve(student, room1, at(1, "10:00"), at(1, "11:00"), 2).andReturn().getResponse().getStatus(),
                () -> reserve(student, room2, at(1, "13:00"), at(1, "14:00"), 2).andReturn().getResponse().getStatus()));

        // 신청자 행 잠금으로 뒤 요청이 앞 요청 커밋 뒤에 한도를 다시 세요 (설계서 5장 8번)
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Long.class)).isEqualTo(1);
    }

    @Test
    void 내_예약은_다가오는_예약과_지난_예약으로_나눠_보여줌() throws Exception {
        Long cancelled = reserveOk(student, room1, at(1, "14:00"), at(1, "15:00"));
        action(student, cancelled, "cancel").andExpect(status().isOk());
        reserveOk(student, room1, at(3, "10:00"), at(3, "11:00"));
        reserveOk(student, room1, at(2, "10:00"), at(2, "11:00"));

        mvc.perform(get("/api/me/reservations").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content[0].startAt").value(iso(at(2, "10:00"))))
                .andExpect(jsonPath("$.content[1].startAt").value(iso(at(3, "10:00"))));
        mvc.perform(get("/api/me/reservations").param("scope", "past").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
        mvc.perform(get("/api/me/reservations").param("scope", "all").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isBadRequest());

        TestUser other = signupAndLogin("99000002");
        mvc.perform(get("/api/me/reservations").header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 남의_예약은_조회도_취소도_404() throws Exception {
        Long id = reserveOk(student, room1, at(1, "14:00"), at(1, "15:00"));
        TestUser other = signupAndLogin("99000002");

        mvc.perform(get("/api/reservations/{id}", id).header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        action(other, id, "cancel").andExpect(status().isNotFound());
        mvc.perform(get("/api/reservations/{id}", id).header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void 시작_시간이_지나면_본인_취소는_409() throws Exception {
        Long id = reserveOk(student, room1, at(1, "09:00"), at(1, "10:00"));
        clock.set(at(1, "09:05"));
        action(student, id, "cancel")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_AFTER_START"));
    }

    @Test
    void 개방_기간에는_키_없이_체크인하고_이용을_종료함() throws Exception {
        insertPeriod(day(1), false, false, 0, 1440);
        Long id = reserveOk(student, room1, at(1, "10:00"), at(1, "12:00"));
        mvc.perform(get("/api/reservations/{id}", id).header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(jsonPath("$.keyRequired").value(false));

        clock.set(at(1, "09:49"));
        action(student, id, "check-in")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECK_IN_NOT_ALLOWED_TIME"))
                .andExpect(jsonPath("$.message").value("이용 시작은 시작 10분 전부터 시작 15분 후까지 할 수 있어요."));

        clock.set(at(1, "09:55"));
        action(student, id, "check-in")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_USE"))
                .andExpect(jsonPath("$.checkedInAt").value(iso(at(1, "09:55"))));

        clock.set(at(1, "11:00"));
        action(student, id, "finish")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").value(iso(at(1, "11:00"))));
    }

    @Test
    void 키가_필요한_예약은_셀프_체크인할_수_없음() throws Exception {
        Long id = reserveOk(student, room1, at(1, "10:00"), at(1, "12:00"));
        clock.set(at(1, "09:55"));
        action(student, id, "check-in")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"));
    }

    private List<Integer> runConcurrently(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();
        return statuses;
    }
}
