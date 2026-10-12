package com.mspace.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mspace.IntegrationTest;
import com.mspace.global.error.ConstraintNames;
import com.mspace.global.error.ErrorCode;
import com.mspace.global.error.GlobalExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V1 스키마의 DB 제약이 의도대로 막는지 진짜 PostgreSQL에서 확인해요.
 * 애플리케이션 검사를 통과한 동시 요청도 결국 이 제약들이 막아요 (NF-05).
 */
class SchemaConstraintTest extends IntegrationTest {

    // 2026-10-13(화) 14:00~16:00 KST
    private static final String T14 = "2026-10-13T14:00:00+09:00";
    private static final String T16 = "2026-10-13T16:00:00+09:00";
    private static final String T15 = "2026-10-13T15:00:00+09:00";
    private static final String T18 = "2026-10-13T18:00:00+09:00";

    private Long room1;
    private Long room2;
    private Long student;

    @BeforeEach
    void setUp() {
        room1 = roomId("1번방");
        room2 = roomId("2번방");
        student = insertUser("990001", "STUDENT");
    }

    private Long reserve(Long roomId, Long userId, String start, String end) {
        return reserve(roomId, userId, start, end, "RESERVED");
    }

    private Long reserve(Long roomId, Long userId, String start, String end, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO reservation (room_id, user_id, start_at, end_at, headcount, status, key_required,
                                         cancelled_at, created_at, updated_at)
                VALUES (?, ?, ?::timestamptz, ?::timestamptz, 2, ?, TRUE,
                        CASE WHEN ? = 'CANCELLED' THEN now() END, now(), now())
                RETURNING id
                """, Long.class, roomId, userId, start, end, status, status);
    }

    /** 제약 위반 예외가 어떤 에러 코드로 바뀌는지 (GlobalExceptionHandler와 같은 규칙) */
    private static ErrorCode errorCodeOf(Throwable e) {
        return GlobalExceptionHandler.toErrorCode(e);
    }

    @Nested
    @DisplayName("예약 시간 겹침 (RV-03)")
    class Overlap {

        @Test
        void 같은_방_겹치는_시간은_RESERVATION_TIME_CONFLICT() {
            reserve(room1, student, T14, T16);
            Long other = insertUser("990002", "STUDENT");

            assertThatThrownBy(() -> reserve(room1, other, T15, T18))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(ErrorCode.RESERVATION_TIME_CONFLICT));
        }

        @Test
        void 끝과_시작이_맞닿은_예약은_허용() {
            reserve(room1, student, T14, T16);
            Long other = insertUser("990002", "STUDENT");

            assertThatCode(() -> reserve(room1, other, T16, T18)).doesNotThrowAnyException();
        }

        @Test
        void 다른_방은_같은_시간도_허용() {
            reserve(room1, student, T14, T16);
            Long other = insertUser("990002", "STUDENT");

            assertThatCode(() -> reserve(room2, other, T14, T16)).doesNotThrowAnyException();
        }

        @Test
        void 취소된_예약은_시간을_막지_않음() {
            reserve(room1, student, T14, T16, "CANCELLED");
            Long other = insertUser("990002", "STUDENT");

            assertThatCode(() -> reserve(room1, other, T14, T16)).doesNotThrowAnyException();
        }

        @Test
        void 같은_학생이_같은_시간에_다른_방은_USER_TIME_CONFLICT() {
            reserve(room1, student, T14, T16);

            assertThatThrownBy(() -> reserve(room2, student, T15, T18))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(ErrorCode.USER_TIME_CONFLICT));
        }

        @Test
        void 같은_방_같은_시간_동시_10건이면_1건만_성공() throws Exception {
            int n = 10;
            List<Long> users = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                users.add(insertUser("99100" + i, "STUDENT"));
            }

            ExecutorService pool = Executors.newFixedThreadPool(n);
            CountDownLatch ready = new CountDownLatch(n);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ErrorCode>> results = new ArrayList<>();
            for (Long userId : users) {
                results.add(pool.submit((Callable<ErrorCode>) () -> {
                    ready.countDown();
                    start.await();
                    try {
                        reserve(room1, userId, T14, T16);
                        return null;   // 성공
                    } catch (DataAccessException e) {
                        return errorCodeOf(e);
                    }
                }));
            }
            ready.await();
            start.countDown();

            int success = 0;
            int conflict = 0;
            for (Future<ErrorCode> f : results) {
                ErrorCode code = f.get();
                if (code == null) success++;
                else if (code == ErrorCode.RESERVATION_TIME_CONFLICT) conflict++;
            }
            pool.shutdown();

            assertThat(success).isEqualTo(1);
            assertThat(conflict).isEqualTo(n - 1);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Long.class)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("예약 값·상태 컬럼")
    class ReservationColumns {

        @Test
        void 시각이_30분_단위가_아니면_거부() {
            assertThatThrownBy(() -> reserve(room1, student, "2026-10-13T14:10:00+09:00", T16))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(ConstraintNames.of(e)).isEqualTo("ck_reservation_slot"));
        }

        @Test
        void 종료가_시작보다_빠르면_거부() {
            assertThatThrownBy(() -> reserve(room1, student, T16, T14))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 키_지급_시각_없이_IN_USE는_거부() {
            assertThatThrownBy(() -> reserve(room1, student, T14, T16, "IN_USE"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(ConstraintNames.of(e)).isEqualTo("ck_reservation_state_columns"));
        }

        @Test
        void 개방_기간_예약에_키_지급_기록은_거부() {
            assertThatThrownBy(() -> jdbcTemplate.update("""
                    INSERT INTO reservation (room_id, user_id, start_at, end_at, headcount, status, key_required,
                                             key_issued_at, created_at, updated_at)
                    VALUES (?, ?, ?::timestamptz, ?::timestamptz, 2, 'IN_USE', FALSE, now(), now(), now())
                    """, room1, student, T14, T16))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 개방_기간_예약은_체크인으로_IN_USE() {
            assertThatCode(() -> jdbcTemplate.update("""
                    INSERT INTO reservation (room_id, user_id, start_at, end_at, headcount, status, key_required,
                                             checked_in_at, created_at, updated_at)
                    VALUES (?, ?, ?::timestamptz, ?::timestamptz, 2, 'IN_USE', FALSE, now(), now(), now())
                    """, room1, student, T14, T16)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("키 이력 (KY-01, KY-05)")
    class KeyLog {

        private Long reservationId;
        private Long admin;

        @BeforeEach
        void setUp() {
            reservationId = reserve(room1, student, T14, T16);
            admin = insertUser("990900", "ADMIN");
        }

        private void log(String action, boolean verified) {
            jdbcTemplate.update("""
                    INSERT INTO key_log (reservation_id, action, admin_id, identity_verified, occurred_at, created_at)
                    VALUES (?, ?, ?, ?, now(), now())
                    """, reservationId, action, admin, verified);
        }

        @Test
        void 본인_확인_없이_지급_기록은_거부() {
            assertThatThrownBy(() -> log("ISSUE", false)).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 같은_예약에_지급_두_번은_거부() {
            log("ISSUE", true);
            assertThatThrownBy(() -> log("ISSUE", true))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(ErrorCode.INVALID_RESERVATION_STATE));
        }

        @Test
        void 이력은_수정도_삭제도_안됨() {
            log("ISSUE", true);
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE key_log SET memo = '고침'"))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("수정·삭제할 수 없어요");
            assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM key_log"))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("수정·삭제할 수 없어요");
        }
    }

    @Nested
    @DisplayName("특별 운영 기간 (PO-02, OP-01)")
    class OperationPeriod {

        private void period(String start, String end, boolean closed, Integer open, Integer close, boolean keyRequired) {
            jdbcTemplate.update("""
                    INSERT INTO operation_period (name, start_date, end_date, closed, key_required,
                                                  open_minute, close_minute, created_at, updated_at)
                    VALUES ('기간', ?::date, ?::date, ?, ?, ?, ?, now(), now())
                    """, start, end, closed, keyRequired, open, close);
        }

        @Test
        void 시험기간_종일_개방_00시부터_24시까지_등록() {
            assertThatCode(() -> period("2026-10-19", "2026-10-23", false, 0, 1440, false)).doesNotThrowAnyException();
        }

        @Test
        void 날짜가_겹치는_기간은_PERIOD_OVERLAP() {
            period("2026-10-19", "2026-10-23", false, 0, 1440, false);
            assertThatThrownBy(() -> period("2026-10-23", "2026-10-25", true, null, null, true))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(ErrorCode.PERIOD_OVERLAP));
        }

        @Test
        void 휴관일에_운영_시간을_넣으면_거부() {
            assertThatThrownBy(() -> period("2026-10-19", "2026-10-19", true, 540, 1080, true))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void 마감이_24시를_넘으면_거부() {
            assertThatThrownBy(() -> period("2026-10-19", "2026-10-19", false, 540, 1470, true))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("이용 제한 (NS-02, NS-03)")
    class Restriction {

        private Long admin;

        @BeforeEach
        void setUp() {
            admin = insertUser("990900", "ADMIN");
        }

        private void restrict(String start, String end, boolean lifted) {
            jdbcTemplate.update("""
                    INSERT INTO user_restriction (user_id, start_at, end_at, reason, lifted_at, lifted_by, lift_reason, created_at)
                    VALUES (?, ?::timestamptz, ?::timestamptz, '노쇼 2회',
                            CASE WHEN ? THEN now() END, ?, CASE WHEN ? THEN '본인 소명' END, now())
                    """, student, start, end, lifted, lifted ? admin : null, lifted);
        }

        @Test
        void 해제되지_않은_제한이_겹치면_거부_스케줄러_중복_방지() {
            restrict("2026-10-13T00:00:00+09:00", "2026-10-20T00:00:00+09:00", false);
            assertThatThrownBy(() -> restrict("2026-10-14T00:00:00+09:00", "2026-10-21T00:00:00+09:00", false))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(ConstraintNames.of(e)).isEqualTo("ex_user_restriction_active"));
        }

        @Test
        void 해제된_제한은_새_제한을_막지_않음() {
            restrict("2026-10-13T00:00:00+09:00", "2026-10-20T00:00:00+09:00", true);
            assertThatCode(() -> restrict("2026-10-14T00:00:00+09:00", "2026-10-21T00:00:00+09:00", false))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("학번 중복 (AU-01, AU-08)")
    class StudentNo {

        @Test
        void 활성_계정끼리_같은_학번은_DUPLICATE_STUDENT_NO() {
            assertThatThrownBy(() -> insertUser("990001", "STUDENT"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(ErrorCode.DUPLICATE_STUDENT_NO));
        }

        @Test
        void 비활성화된_계정의_학번으로_재가입_가능() {
            jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", student);
            assertThatCode(() -> insertUser("990001", "STUDENT")).doesNotThrowAnyException();
        }
    }
}
