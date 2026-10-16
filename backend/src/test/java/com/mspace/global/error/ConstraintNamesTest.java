package com.mspace.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.mspace.global.error.ConstraintNames.Violation;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 제약 이름 추출과 에러 코드 매핑 단위 테스트 (TS-15). DB 없이 예외 사슬을 직접 만들어요.
 * 한국어 메시지는 lc_messages가 ko_KR인 서버가 보내는 문장을 흉내 낸 값이에요.
 */
class ConstraintNamesTest {

    private static final String KO_EXCLUSION = "충돌하는 키 값이 \"ex_reservation_room_time\" 제외 제약 조건을 위반함";
    private static final String KO_UNIQUE = "중복된 키 값이 \"uq_users_student_no_active\" 고유 제약 조건을 위반함";

    /** PostgreSQL 서버가 보낸 에러처럼 필드(S·C·M·n)를 채운 PSQLException */
    private static PSQLException psql(String sqlState, String message, String constraint) {
        StringBuilder raw = new StringBuilder()
                .append("SERROR\0VERROR\0C").append(sqlState).append("\0M").append(message).append('\0');
        if (constraint != null) {
            raw.append('n').append(constraint).append('\0');
        }
        return new PSQLException(new ServerErrorMessage(raw.toString()));
    }

    /** JPA 저장 경로: Spring 예외 → Hibernate 예외(이름 못 찾음) → 드라이버 예외 */
    private static DataIntegrityViolationException viaJpa(SQLException root, String hibernateName) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", root, hibernateName));
    }

    /** JdbcTemplate 경로: Spring 예외 → 드라이버 예외 */
    private static DataIntegrityViolationException viaJdbc(SQLException root) {
        return new DataIntegrityViolationException("PreparedStatementCallback", root);
    }

    @Nested
    @DisplayName("서버 에러 필드 우선 (로케일 무관)")
    class ServerField {

        @Test
        void 한국어_메시지라도_JPA_경로의_EXCLUDE_위반은_RESERVATION_TIME_CONFLICT() {
            var e = viaJpa(psql("23P01", KO_EXCLUSION, "ex_reservation_room_time"), null);

            assertThat(ConstraintNames.violationOf(e))
                    .isEqualTo(new Violation("ex_reservation_room_time", ConstraintNames.EXCLUSION_VIOLATION));
            assertThat(GlobalExceptionHandler.toErrorCode(e)).isEqualTo(ErrorCode.RESERVATION_TIME_CONFLICT);
        }

        @Test
        void 한국어_메시지라도_JDBC_경로의_부분_유니크_인덱스_위반은_DUPLICATE_STUDENT_NO() {
            var e = viaJdbc(psql("23505", KO_UNIQUE, "uq_users_student_no_active"));

            assertThat(ConstraintNames.violationOf(e))
                    .isEqualTo(new Violation("uq_users_student_no_active", ConstraintNames.UNIQUE_VIOLATION));
            assertThat(GlobalExceptionHandler.toErrorCode(e)).isEqualTo(ErrorCode.DUPLICATE_STUDENT_NO);
        }

        @Test
        void 서버_필드가_Hibernate가_준_이름보다_우선() {
            var e = viaJpa(psql("23P01", KO_EXCLUSION, "ex_reservation_room_time"), "something_else");

            assertThat(ConstraintNames.of(e)).isEqualTo("ex_reservation_room_time");
        }

        @Test
        void 메시지_속_정규식_후보보다_서버_필드가_우선() {
            // 메시지에는 다른 이름이 영문 형식으로 들어 있어도 서버 필드를 믿어요
            var e = viaJdbc(psql("23P01", "violates exclusion constraint \"ex_reservation_user_time\"",
                    "ex_reservation_room_time"));

            assertThat(ConstraintNames.of(e)).isEqualTo("ex_reservation_room_time");
        }

        @Test
        void JDBC_배치의_다음_예외에_있는_원인도_찾음() {
            BatchUpdateException batch = new BatchUpdateException("Batch entry 0 was aborted", "23P01", new int[0]);
            batch.setNextException(psql("23P01", KO_EXCLUSION, "ex_reservation_room_time"));

            assertThat(GlobalExceptionHandler.toErrorCode(viaJdbc(batch))).isEqualTo(ErrorCode.RESERVATION_TIME_CONFLICT);
        }
    }

    @Nested
    @DisplayName("폴백: Hibernate 이름 → 영문 메시지 정규식")
    class Fallback {

        @Test
        void 서버_필드가_없으면_Hibernate가_준_이름() {
            var e = viaJpa(new SQLException("duplicate", "23505"), "uq_key_log_reservation_action");

            assertThat(ConstraintNames.violationOf(e))
                    .isEqualTo(new Violation("uq_key_log_reservation_action", "23505"));
            assertThat(GlobalExceptionHandler.toErrorCode(e)).isEqualTo(ErrorCode.INVALID_RESERVATION_STATE);
        }

        @Test
        void 둘_다_없으면_영문_메시지에서_정규식으로() {
            var e = viaJdbc(new SQLException(
                    "ERROR: conflicting key value violates exclusion constraint \"ex_operation_period_dates\"", "23P01"));

            assertThat(GlobalExceptionHandler.toErrorCode(e)).isEqualTo(ErrorCode.PERIOD_OVERLAP);
        }

        @Test
        void 서버_필드_없는_한국어_메시지는_정규식으로도_못_읽음_폴백의_한계() {
            // 이게 예전 구현(정규식만)의 결함이에요: 한국어 서버에서는 409 대신 500이 나갔어요
            var e = viaJdbc(new SQLException(KO_EXCLUSION, "23P01"));

            assertThat(ConstraintNames.violationOf(e)).isNull();
            assertThat(GlobalExceptionHandler.toErrorCode(e)).isNull();
        }

        @Test
        void SQLState를_알_수_없으면_이름만으로_매핑() {
            var e = viaJpa(new SQLException("unknown"), "ex_reservation_user_time");

            assertThat(ConstraintNames.violationOf(e)).isEqualTo(new Violation("ex_reservation_user_time", null));
            assertThat(GlobalExceptionHandler.toErrorCode(e)).isEqualTo(ErrorCode.USER_TIME_CONFLICT);
        }
    }

    @Nested
    @DisplayName("매핑하지 않는 경우")
    class NotMapped {

        @Test
        void 이름은_같아도_SQLState가_다르면_매핑하지_않음() {
            var e = viaJdbc(psql("23514", "check", "ex_reservation_room_time"));

            assertThat(ConstraintNames.of(e)).isEqualTo("ex_reservation_room_time");
            assertThat(GlobalExceptionHandler.toErrorCode(e)).isNull();
        }

        @Test
        void 모르는_제약은_이름만_읽고_에러_코드는_null() {
            var e = viaJdbc(psql("23514", "체크 제약 조건 위반", "ck_reservation_slot"));

            assertThat(ConstraintNames.violationOf(e)).isEqualTo(new Violation("ck_reservation_slot", "23514"));
            assertThat(GlobalExceptionHandler.toErrorCode(e)).isNull();
        }

        @Test
        void 제약과_무관한_예외는_null() {
            assertThat(ConstraintNames.violationOf(new IllegalStateException("boom"))).isNull();
            assertThat(ConstraintNames.violationOf(viaJdbc(psql("40P01", "교착 상태 발견", null)))).isNull();
            assertThat(ConstraintNames.violationOf(null)).isNull();
        }
    }

    @Test
    void 매핑표의_모든_제약이_기대_SQLState로_위반되면_그_에러_코드로_바뀜() {
        assertThat(GlobalExceptionHandler.CONSTRAINT_ERRORS).isNotEmpty();
        for (Map.Entry<String, GlobalExceptionHandler.ConstraintRule> entry : GlobalExceptionHandler.CONSTRAINT_ERRORS.entrySet()) {
            var rule = entry.getValue();
            var e = viaJpa(psql(rule.sqlState(), "메시지", entry.getKey()), null);

            assertThat(GlobalExceptionHandler.toErrorCode(e)).as(entry.getKey()).isEqualTo(rule.errorCode());
            assertThat(rule.errorCode().getStatus().value()).as(entry.getKey()).isEqualTo(409);
        }
    }
}
