package com.mspace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mspace.global.security.JwtTokenProvider;
import com.mspace.global.util.KstTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 통합 테스트 공통 설정: 진짜 PostgreSQL(Testcontainers) + MockMvc + test 프로필.
 * 테스트가 끝날 때마다 데이터를 비워서 테스트끼리 영향을 주지 않게 해요. 방·운영 정책 초기 데이터는 V1 값으로 되돌려요.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider tokenProvider;

    /** 서비스가 쓰는 시계. set()으로 "지금"을 고정해요 (실제 시각보다 미래로만, MutableClock 주석 참고) */
    @Autowired
    protected MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @AfterEach
    void cleanUpDatabase() {
        // key_log는 행 단위 DELETE를 트리거가 막아서 TRUNCATE로 비워요 (TRUNCATE는 행 트리거를 실행하지 않아요)
        jdbcTemplate.execute("""
                TRUNCATE key_log, reservation, user_restriction, policy_change_log, admin_audit_log, operation_period
                RESTART IDENTITY
                """);
        // users는 operation_policy가 참조해서 TRUNCATE ... CASCADE를 쓰면 정책 행까지 지워져요 → DELETE로 비워요
        jdbcTemplate.execute("""
                UPDATE operation_policy SET default_open_minute = 540, default_close_minute = 1080,
                    max_duration_minutes = 120, daily_limit = 1, booking_window_days = 7,
                    key_issue_before_minutes = 10, no_show_grace_minutes = 15,
                    penalty_window_days = 14, penalty_threshold = 2, penalty_days = 7, updated_by = NULL
                """);
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN id RESTART");
    }

    /** 가짜 회원을 DB에 바로 넣어요 (실제 학생 정보 금지: 학번은 99로 시작하는 가짜 값) */
    protected Long insertUser(String studentNo, String role) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (student_no, name, phone, password_hash, role, created_at, updated_at)
                VALUES (?, '테스트', '010-0000-0000', 'x', ?, now(), now())
                RETURNING id
                """, Long.class, studentNo, role);
    }

    protected String bearer(Long userId) {
        return "Bearer " + tokenProvider.createAccessToken(userId);
    }

    /** 테스트 비밀번호 (영문+숫자 규칙 만족) */
    protected static final String PASSWORD = "mspace1234";

    /** API로 가입한 학생. 학번은 99로 시작하는 가짜 값만 써요 */
    protected record TestUser(Long id, String studentNo, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    protected TestUser signupAndLogin(String studentNo) throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"studentNo":"%s","name":"테스트","phone":"010-0000-0000","password":"%s","privacyAgreed":true}
                        """.formatted(studentNo, PASSWORD)))
                .andExpect(status().isCreated());
        return login(studentNo, PASSWORD);
    }

    protected TestUser login(String studentNo, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"studentNo":"%s","password":"%s"}
                        """.formatted(studentNo, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new TestUser(((Number) JsonPath.read(body, "$.user.id")).longValue(), studentNo,
                JsonPath.read(body, "$.accessToken"));
    }

    /** 가입 후 SQL로 관리자 지정 (관리자는 회원가입으로 만들 수 없어요, AU-06) */
    protected TestUser adminLogin(String studentNo) throws Exception {
        signupAndLogin(studentNo);
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE student_no = ?", studentNo);
        return login(studentNo, PASSWORD);
    }

    protected Long roomId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM room WHERE name = ?", Long.class, name);
    }

    /** 오늘(KST, 실제 날짜)부터 dayOffset일 뒤의 한국 시각. 예: at(1, "14:00") = 내일 14:00 KST */
    protected static Instant at(int dayOffset, String hhmm) {
        LocalDate date = KstTime.dateOf(Instant.now()).plusDays(dayOffset);
        return date.atTime(LocalTime.parse(hhmm)).atZone(KstTime.KST).toInstant();
    }

    protected static LocalDate day(int dayOffset) {
        return KstTime.dateOf(Instant.now()).plusDays(dayOffset);
    }

    /** API 형식의 시각 "2026-10-13T14:00:00+09:00" (OffsetDateTime.toString()은 0초를 빼서 쓰지 않아요) */
    protected static String iso(Instant instant) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(KstTime.of(instant));
    }

    protected static String reservationBody(Long roomId, Instant start, Instant end, int headcount) {
        return """
                {"roomId":%d,"startAt":"%s","endAt":"%s","headcount":%d}
                """.formatted(roomId, iso(start), iso(end), headcount);
    }
}
