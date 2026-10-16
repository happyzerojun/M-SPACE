package com.mspace.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.mspace.IntegrationTest;
import com.mspace.global.error.ConstraintNames;
import com.mspace.global.error.ErrorCode;
import com.mspace.global.error.ErrorResponse;
import com.mspace.global.error.GlobalExceptionHandler;
import com.mspace.global.jpa.ReservationRow;
import com.mspace.global.jpa.ReservationRowRepository;
import com.mspace.user.domain.User;
import com.mspace.user.domain.UserRole;
import com.mspace.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.JDBCException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * JPA(Hibernate)로 저장할 때 난 제약 위반도 에러 코드로 바뀌는지 (TS-05, TS-15).
 * 서비스의 "먼저 확인"을 건너뛰고 바로 저장해서, 동시 요청이 확인을 통과한 상황을 확정적으로 만들어요.
 */
class JpaConstraintMappingTest extends IntegrationTest {

    // 2026-10-13(화) KST 14:00, 15:00, 16:00, 17:00 (UTC로 적어요)
    private static final Instant T14 = Instant.parse("2026-10-13T05:00:00Z");
    private static final Instant T15 = Instant.parse("2026-10-13T06:00:00Z");
    private static final Instant T16 = Instant.parse("2026-10-13T07:00:00Z");
    private static final Instant T17 = Instant.parse("2026-10-13T08:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRowRepository reservationRows;

    @Test
    void 부분_유니크_인덱스_위반은_DUPLICATE_STUDENT_NO() {
        userRepository.saveAndFlush(User.create("99000001", "가", "010-0000-0000", "x", UserRole.STUDENT));

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(User.create("99000001", "나", "010-0000-0000", "x", UserRole.STUDENT)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(GlobalExceptionHandler.toErrorCode(e)).isEqualTo(ErrorCode.DUPLICATE_STUDENT_NO));
    }

    /** 원인 사슬에서 원하는 타입의 예외를 찾아요 (없으면 null) */
    private static <T extends Throwable> T findCause(Throwable e, Class<T> type) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    @Nested
    @DisplayName("예약 겹침 EXCLUDE 위반의 JPA 경로 (RV-03)")
    class ReservationOverlap {

        @Test
        void JPA_저장의_EXCLUDE_위반은_서버_에러_필드로_읽어_RESERVATION_TIME_CONFLICT_409() {
            Long room = roomId("1번방");
            Long first = insertUser("99000011", "STUDENT");
            Long second = insertUser("99000012", "STUDENT");
            reservationRows.saveAndFlush(ReservationRow.reserved(room, first, T14, T16));

            Throwable thrown = catchThrowable(() ->
                    reservationRows.saveAndFlush(ReservationRow.reserved(room, second, T15, T17)));

            // JdbcTemplate이 아니라 Hibernate를 거친 예외인지, 드라이버가 서버 필드를 줬는지
            assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(findCause(thrown, JDBCException.class)).as("Hibernate 경로").isNotNull();
            PSQLException psql = findCause(thrown, PSQLException.class);
            // SQLException은 Throwable이면서 Iterable이라 assertThat 오버로드가 모호해서 Throwable로 맞춰요
            assertThat((Throwable) psql).as("드라이버 예외").isNotNull();
            assertThat(psql.getServerErrorMessage()).isNotNull();
            assertThat(psql.getServerErrorMessage().getConstraint()).isEqualTo("ex_reservation_room_time");
            assertThat(psql.getSQLState()).isEqualTo(ConstraintNames.EXCLUSION_VIOLATION);

            assertThat(GlobalExceptionHandler.toErrorCode(thrown)).isEqualTo(ErrorCode.RESERVATION_TIME_CONFLICT);
            ResponseEntity<ErrorResponse> response =
                    new GlobalExceptionHandler().handleDataIntegrity((DataIntegrityViolationException) thrown);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("RESERVATION_TIME_CONFLICT");
            assertThat(response.getBody().message()).isEqualTo("이미 예약된 시간이에요. 다른 시간을 골라 주세요.");

            assertThat(reservationRows.count()).isEqualTo(1);
        }

        @Test
        void JPA_저장의_같은_학생_시간_겹침은_USER_TIME_CONFLICT() {
            Long student = insertUser("99000011", "STUDENT");
            reservationRows.saveAndFlush(ReservationRow.reserved(roomId("1번방"), student, T14, T16));

            assertThatThrownBy(() ->
                    reservationRows.saveAndFlush(ReservationRow.reserved(roomId("2번방"), student, T15, T17)))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(GlobalExceptionHandler.toErrorCode(e)).isEqualTo(ErrorCode.USER_TIME_CONFLICT));
        }

        @Test
        void JPA_동시_저장_같은_방_같은_시간_8건이면_1건만_성공하고_나머지는_RESERVATION_TIME_CONFLICT() throws Exception {
            int n = 8;
            Long room = roomId("1번방");
            List<Long> users = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                users.add(insertUser("9920000" + i, "STUDENT"));
            }

            ExecutorService pool = Executors.newFixedThreadPool(n);
            CountDownLatch ready = new CountDownLatch(n);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> results = new ArrayList<>();
            for (Long userId : users) {
                results.add(pool.submit((Callable<String>) () -> {
                    ready.countDown();
                    start.await();
                    try {
                        // saveAndFlush는 리포지토리 트랜잭션 안에서 INSERT하고 바로 커밋해요 (요청 하나 = 트랜잭션 하나)
                        reservationRows.saveAndFlush(ReservationRow.reserved(room, userId, T14, T16));
                        return "SUCCESS";
                    } catch (PessimisticLockingFailureException e) {
                        // 동시에 들어온 두 INSERT가 서로의 진행 중인 행을 기다리면 PostgreSQL이 교착 상태(40P01)로
                        // 한쪽을 끊을 수 있어요(추정, TS-15). 겹치는 예약이 생긴 건 아니라서 따로 세요
                        return "DEADLOCK";
                    } catch (DataAccessException e) {
                        ErrorCode code = GlobalExceptionHandler.toErrorCode(e);
                        return code == null ? "OTHER:" + e : code.name();
                    }
                }));
            }
            ready.await();
            start.countDown();

            int success = 0;
            int conflict = 0;
            int deadlock = 0;
            List<String> others = new ArrayList<>();
            for (Future<String> f : results) {
                String result = f.get(30, TimeUnit.SECONDS);
                switch (result) {
                    case "SUCCESS" -> success++;
                    case "RESERVATION_TIME_CONFLICT" -> conflict++;
                    case "DEADLOCK" -> deadlock++;
                    default -> others.add(result);
                }
            }
            pool.shutdown();

            assertThat(others).as("예상하지 못한 실패").isEmpty();
            assertThat(success).isEqualTo(1);
            assertThat(conflict + deadlock).isEqualTo(n - 1);
            assertThat(conflict).as("겹침은 409로 바뀌어야 해요").isPositive();
            assertThat(reservationRows.count()).isEqualTo(1);
        }
    }
}
