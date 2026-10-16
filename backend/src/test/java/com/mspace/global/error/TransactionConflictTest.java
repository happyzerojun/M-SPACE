package com.mspace.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.mspace.global.error.ConstraintNames.TransactionConflict;
import java.sql.SQLException;
import org.hibernate.exception.LockAcquisitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 교착(40P01)·직렬화 실패(40001)를 500 대신 409로 바꾸는 규칙 단위 테스트 (TS-16). DB 없이 예외 사슬을 직접 만들어요.
 * 실제 교착은 JpaConstraintMappingTest에서 PostgreSQL로 재현해요.
 */
class TransactionConflictTest {

    private static final String RESERVATION_INSERT =
            "insert into reservation (created_at,updated_at,end_at,headcount,key_required,room_id,start_at,status,user_id) "
                    + "values (?,?,?,?,?,?,?,?,?)";

    /** PostgreSQL이 교착 패자에게 보내는 에러 필드(S·C·M·D·W). 문장은 한국어 서버를 흉내 냈어요 */
    private static PSQLException psql(String sqlState) {
        String raw = "SERROR\0VERROR\0C" + sqlState + "\0M교착 상태 발견\0"
                + "D1274 프로세스가 753 트랜잭션에서 ShareLock 대기 중\0"
                + "W\"reservation\" 릴레이션의 (0,2) 튜플에 대한 제외 제약 조건 검사 중\0";
        return new PSQLException(new ServerErrorMessage(raw));
    }

    /** JPA 경로: Spring 예외 → Hibernate 예외(실행 SQL 포함) → 드라이버 예외 */
    private static CannotAcquireLockException viaJpa(SQLException root, String sql) {
        return new CannotAcquireLockException("could not execute statement",
                new LockAcquisitionException("could not execute statement", root, sql));
    }

    @Nested
    @DisplayName("예약 INSERT의 교착은 RESERVATION_TIME_CONFLICT")
    class ReservationInsert {

        @Test
        void JPA_경로의_예약_INSERT_교착은_RESERVATION_TIME_CONFLICT_409() {
            var e = viaJpa(psql(ConstraintNames.DEADLOCK_DETECTED), RESERVATION_INSERT);

            assertThat(ConstraintNames.transactionConflictOf(e))
                    .isEqualTo(new TransactionConflict("40P01", "INSERT", "reservation"));
            assertThat(GlobalExceptionHandler.toTransactionConflictCode(e)).isEqualTo(ErrorCode.RESERVATION_TIME_CONFLICT);

            ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler().handleLockFailure(e);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("RESERVATION_TIME_CONFLICT");
            assertThat(response.getBody().message()).isEqualTo("이미 예약된 시간이에요. 다른 시간을 골라 주세요.");
        }

        @Test
        void 직렬화_실패_40001도_같은_규칙() {
            var e = viaJpa(psql(ConstraintNames.SERIALIZATION_FAILURE), RESERVATION_INSERT);

            assertThat(GlobalExceptionHandler.toTransactionConflictCode(e)).isEqualTo(ErrorCode.RESERVATION_TIME_CONFLICT);
        }

        @Test
        void SQL_앞에_주석이_있고_대문자여도_읽음() {
            var e = viaJpa(psql("40P01"), "/* insert for ReservationRow */ INSERT INTO \"reservation\" (id) values (?)");

            assertThat(ConstraintNames.transactionConflictOf(e))
                    .isEqualTo(new TransactionConflict("40P01", "INSERT", "reservation"));
        }

        @Test
        void 다른_예외로_한번_더_감싸여_와도_500이_아니라_409() {
            // 커밋 시점 실패처럼 PessimisticLockingFailureException이 아닌 예외에 감싸여 handleUnexpected로 온 경우
            var e = new IllegalStateException("commit failed", viaJpa(psql("40P01"), RESERVATION_INSERT));

            ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler().handleUnexpected(e);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("RESERVATION_TIME_CONFLICT");
        }
    }

    @Nested
    @DisplayName("무엇과 부딪혔는지 단정할 수 없으면 CONCURRENT_UPDATE")
    class Generic {

        @Test
        void 예약_UPDATE의_교착은_겹침이_아니라_CONCURRENT_UPDATE() {
            var e = viaJpa(psql("40P01"), "update reservation set status=?,version=? where id=? and version=?");

            assertThat(ConstraintNames.transactionConflictOf(e))
                    .isEqualTo(new TransactionConflict("40P01", "UPDATE", "reservation"));
            assertThat(GlobalExceptionHandler.toTransactionConflictCode(e)).isEqualTo(ErrorCode.CONCURRENT_UPDATE);
        }

        @Test
        void 이름이_reservation으로_시작하는_다른_테이블은_예약_겹침이_아님() {
            var e = viaJpa(psql("40P01"), "insert into reservation_history (id) values (?)");

            assertThat(GlobalExceptionHandler.toTransactionConflictCode(e)).isEqualTo(ErrorCode.CONCURRENT_UPDATE);
        }

        @Test
        void 실행_SQL을_모르는_JdbcTemplate_경로는_CONCURRENT_UPDATE() {
            // 서버 CONTEXT(W)에는 "reservation"이 있지만 번역된 문장이라 읽지 않아요 (TS-15와 같은 이유)
            var e = new CannotAcquireLockException("PreparedStatementCallback", psql("40P01"));

            assertThat(ConstraintNames.transactionConflictOf(e)).isEqualTo(new TransactionConflict("40P01", null, null));
            ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler().handleLockFailure(e);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("CONCURRENT_UPDATE");
        }

        @Test
        void 교착이_아닌_잠금_실패도_500이_아니라_CONCURRENT_UPDATE() {
            // 55P03 lock_not_available: 40P01·40001이 아니라 toTransactionConflictCode는 null이지만 핸들러는 409
            var e = new PessimisticLockingFailureException("lock", psql("55P03"));

            assertThat(GlobalExceptionHandler.toTransactionConflictCode(e)).isNull();
            ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler().handleLockFailure(e);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("CONCURRENT_UPDATE");
        }
    }

    @Test
    void 제약_위반이나_무관한_예외는_교착으로_보지_않음() {
        String raw = "SERROR\0VERROR\0C23P01\0M충돌\0nex_reservation_room_time\0";
        var exclusion = viaJpa(new PSQLException(new ServerErrorMessage(raw)), RESERVATION_INSERT);

        assertThat(ConstraintNames.transactionConflictOf(exclusion)).isNull();
        assertThat(GlobalExceptionHandler.toTransactionConflictCode(exclusion)).isNull();
        assertThat(ConstraintNames.transactionConflictOf(new IllegalStateException("boom"))).isNull();
        assertThat(ConstraintNames.transactionConflictOf(null)).isNull();
    }
}
