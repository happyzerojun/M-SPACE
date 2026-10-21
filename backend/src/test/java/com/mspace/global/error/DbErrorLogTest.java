package com.mspace.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * 매핑되지 않은 제약 위반 로그에 PostgreSQL DETAIL(학번·이름·전화번호)이 남지 않는지 (NF-01, TS-16).
 * 드라이버 설정(logServerErrorDetail=false)이 빠진 경우를 가정해서, DETAIL이 메시지에 들어 있는 예외로 확인해요.
 * 값은 모두 가짜예요(99로 시작하는 학번, 010-0000-…).
 */
class DbErrorLogTest {

    private static final String FAKE_STUDENT_NO = "99000077";
    private static final String FAKE_PHONE = "010-0000-7777";
    private static final String DETAIL = "Failing row contains (7, " + FAKE_STUDENT_NO + ", 홍길동, " + FAKE_PHONE + ", GUEST).";

    private ListAppender<ILoggingEvent> appender;
    private Logger handlerLogger;

    @BeforeEach
    void attachAppender() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        handlerLogger.detachAppender(appender);
        appender.stop();
    }

    /** DETAIL(D)까지 담긴 CHECK 위반. PSQLException(ServerErrorMessage)는 DETAIL을 메시지에 넣는 기본 동작이에요 */
    private static DataIntegrityViolationException checkViolationWithDetail() {
        String raw = "SERROR\0VERROR\0C23514\0Mnew row for relation \"users\" violates check constraint \"ck_users_role\"\0"
                + "D" + DETAIL + "\0nck_users_role\0tusers\0";
        PSQLException psql = new PSQLException(new ServerErrorMessage(raw));
        // Hibernate·Spring은 원인 메시지를 자기 메시지에 복사해 둬요 (실제 예외 모양 그대로)
        String hibernateMessage = "could not execute statement [" + psql.getMessage() + "] [insert into users ...]";
        return new DataIntegrityViolationException(hibernateMessage + "; SQL [insert into users ...]",
                new ConstraintViolationException(hibernateMessage, psql, "ck_users_role"));
    }

    @Test
    void 전제_가리지_않은_원래_예외는_메시지_사슬에_DETAIL이_있음() {
        assertThat(allMessages(checkViolationWithDetail())).contains(FAKE_STUDENT_NO, FAKE_PHONE);
    }

    @Test
    void 가린_사본은_SQLState와_제약_이름만_남기고_스택은_유지() {
        DataIntegrityViolationException original = checkViolationWithDetail();

        Throwable masked = DbErrorLog.masked(original);

        String messages = allMessages(masked);
        assertThat(messages).doesNotContain(FAKE_STUDENT_NO, FAKE_PHONE, "홍길동", "Failing row", "insert into");
        assertThat(messages)
                .contains("org.springframework.dao.DataIntegrityViolationException")
                .contains("org.hibernate.exception.ConstraintViolationException [SQLState=23514, constraint=ck_users_role]")
                .contains("org.postgresql.util.PSQLException [SQLState=23514, constraint=ck_users_role]");
        // 어디서 났는지는 추적할 수 있게 스택은 그대로
        assertThat(masked.getStackTrace()).isEqualTo(original.getStackTrace());
        assertThat(masked.getCause().getCause().getStackTrace()).isEqualTo(original.getCause().getCause().getStackTrace());
    }

    @Test
    void DB_예외가_없는_사슬은_그대로_돌려줌() {
        var e = new IllegalStateException("boom", new IllegalArgumentException("cause"));

        assertThat(DbErrorLog.masked(e)).isSameAs(e);
    }

    @Test
    void 매핑되지_않은_제약_위반_로그에는_제약_이름과_SQLState만_남음() {
        var response = new GlobalExceptionHandler().handleDataIntegrity(checkViolationWithDetail());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("잠시 후 다시 시도해 주세요.");

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).isEqualTo("예상하지 못한 DB 제약 위반: constraint=ck_users_role, sqlState=23514");
        assertThat(event.getThrowableProxy()).as("스택은 남겨요").isNotNull();
        assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
        assertThat(loggedText(event)).doesNotContain(FAKE_STUDENT_NO, FAKE_PHONE, "홍길동", "Failing row");
    }

    @Test
    void 처리되지_않은_예외_로그도_DB_예외의_DETAIL을_가림() {
        new GlobalExceptionHandler().handleUnexpected(new IllegalStateException("저장 실패", checkViolationWithDetail()));

        assertThat(appender.list).hasSize(1);
        String logged = loggedText(appender.list.getFirst());
        assertThat(logged).contains("java.lang.IllegalStateException: 저장 실패", "SQLState=23514");
        assertThat(logged).doesNotContain(FAKE_STUDENT_NO, FAKE_PHONE, "홍길동", "Failing row");
    }

    @Test
    void 교착_409_로그는_스택_없이_한_줄() {
        String raw = "SERROR\0VERROR\0C40P01\0Mdeadlock detected\0DProcess 1274 waits for ShareLock\0";
        var e = new org.springframework.dao.CannotAcquireLockException("x", new org.hibernate.exception.LockAcquisitionException(
                "x", new PSQLException(new ServerErrorMessage(raw)), "insert into reservation (id) values (?)"));

        new GlobalExceptionHandler().handleLockFailure(e);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .isEqualTo("동시 트랜잭션 충돌 → RESERVATION_TIME_CONFLICT: sqlState=40P01, statement=INSERT reservation");
        assertThat(event.getThrowableProxy()).isNull();
    }

    /** 로그 한 건에서 사람이 읽게 되는 글자 전부: 포맷된 메시지 + 예외 사슬의 클래스·메시지 */
    private static String loggedText(ILoggingEvent event) {
        List<String> parts = new ArrayList<>();
        parts.add(event.getFormattedMessage());
        for (IThrowableProxy p = event.getThrowableProxy(); p != null; p = p.getCause()) {
            parts.add(p.getClassName() + ": " + p.getMessage());
        }
        return String.join("\n", parts);
    }

    private static String allMessages(Throwable e) {
        List<String> parts = new ArrayList<>();
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            parts.add(String.valueOf(t.getMessage()));
        }
        return String.join("\n", parts);
    }
}
