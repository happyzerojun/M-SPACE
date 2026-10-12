package com.mspace;

import com.mspace.global.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider tokenProvider;

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
                    max_duration_minutes = 180, daily_limit = 1, booking_window_days = 7,
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

    protected Long roomId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM room WHERE name = ?", Long.class, name);
    }
}
