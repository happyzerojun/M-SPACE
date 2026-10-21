package com.mspace.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.mspace.IntegrationTest;
import com.mspace.global.error.ErrorCode;
import com.mspace.global.error.ErrorResponse;
import com.mspace.global.error.GlobalExceptionHandler;
import com.mspace.user.domain.User;
import com.mspace.user.domain.UserRole;
import com.mspace.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 진짜 PostgreSQL에서 난 제약 위반의 DETAIL(학번·이름·전화번호)이 로그로 새지 않는지 (NF-01, TS-16).
 * 루트 로거에 붙여 Hibernate(SqlExceptionHelper)·Spring·우리 핸들러가 남기는 로그를 모두 봐요.
 * 학번·전화번호는 가짜 값이에요.
 */
class DbErrorPrivacyTest extends IntegrationTest {

    private static final String FAKE_STUDENT_NO = "99000088";
    private static final String FAKE_NAME = "가짜학생";
    private static final String FAKE_PHONE = "010-0000-8888";

    @Autowired
    private UserRepository userRepository;

    private ListAppender<ILoggingEvent> appender;
    private Logger root;

    @BeforeEach
    void attachAppender() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        root.detachAppender(appender);
        appender.stop();
    }

    @Test
    void 드라이버가_예외_메시지에서_DETAIL을_빼고_에러_필드는_그대로_줌() {
        userRepository.saveAndFlush(User.create(FAKE_STUDENT_NO, FAKE_NAME, FAKE_PHONE, "x", UserRole.STUDENT));

        Throwable thrown = catchThrowable(() ->
                userRepository.saveAndFlush(User.create(FAKE_STUDENT_NO, FAKE_NAME, FAKE_PHONE, "x", UserRole.STUDENT)));

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        PSQLException psql = findCause(thrown, PSQLException.class);
        assertThat((Throwable) psql).isNotNull();
        // logServerErrorDetail=false: 메시지에는 "ERROR: duplicate key value violates unique constraint ..."까지만
        assertThat(psql.getMessage()).contains("uq_users_student_no_active").doesNotContain(FAKE_STUDENT_NO);
        // 제약 이름·SQLState·DETAIL 필드 자체는 그대로 받아서 409 매핑은 그대로예요
        assertThat(psql.getServerErrorMessage().getConstraint()).isEqualTo("uq_users_student_no_active");
        assertThat(psql.getServerErrorMessage().getDetail()).contains(FAKE_STUDENT_NO);
        assertThat(GlobalExceptionHandler.toErrorCode(thrown)).isEqualTo(ErrorCode.DUPLICATE_STUDENT_NO);

        // Spring·Hibernate 예외 메시지 사슬에도 학번이 없어요
        assertThat(allMessages(thrown)).doesNotContain(FAKE_STUDENT_NO);
        // Hibernate SqlExceptionHelper가 남긴 SQL 에러 로그 포함, 어떤 로그에도 학번이 없어요
        assertThat(allLogged()).doesNotContain(FAKE_STUDENT_NO);
    }

    @Test
    void 매핑되지_않은_CHECK_위반은_500이고_로그에는_제약_이름과_SQLState만() {
        // CHECK 위반의 DETAIL은 "Failing row contains (…)": 행 전체(학번·이름·전화번호)가 들어 있어요
        Throwable thrown = catchThrowable(() -> jdbcTemplate.update("""
                INSERT INTO users (student_no, name, phone, password_hash, role, created_at, updated_at)
                VALUES (?, ?, ?, 'x', 'GUEST', now(), now())
                """, FAKE_STUDENT_NO, FAKE_NAME, FAKE_PHONE));
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(findCause(thrown, PSQLException.class).getServerErrorMessage().getDetail())
                .as("전제: DB는 DETAIL에 행 값을 보내요").contains(FAKE_STUDENT_NO, FAKE_PHONE);

        ResponseEntity<ErrorResponse> response =
                new GlobalExceptionHandler().handleDataIntegrity((DataIntegrityViolationException) thrown);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(allLogged()).contains("constraint=ck_users_role, sqlState=23514")
                .doesNotContain(FAKE_STUDENT_NO, FAKE_NAME, FAKE_PHONE, "Failing row");
    }

    private String allLogged() {
        List<String> parts = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            parts.add(event.getFormattedMessage());
            for (IThrowableProxy p = event.getThrowableProxy(); p != null; p = p.getCause()) {
                parts.add(p.getClassName() + ": " + p.getMessage());
            }
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

    private static <T extends Throwable> T findCause(Throwable e, Class<T> type) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }
}
