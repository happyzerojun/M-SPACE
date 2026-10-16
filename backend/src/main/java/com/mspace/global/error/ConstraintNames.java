package com.mspace.global.error;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.JDBCException;
import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * DB 제약 위반 예외에서 제약 이름과 SQLState를 꺼내요 (NF-05, TS-15).
 *
 * <p>에러 메시지 문장은 DB 서버의 {@code lc_messages}에 따라 번역돼요(예: 한국어 서버는
 * {@code 충돌하는 키 값이 "ex_reservation_room_time" 제외 제약 조건을 위반함}). 그래서 문장 대신
 * PostgreSQL이 따로 보내는 에러 필드를 먼저 읽어요. 우선순위:
 * <ol>
 *   <li>{@link PSQLException#getServerErrorMessage()}의 제약 이름 필드(프로토콜 필드 {@code n}): 로케일과 무관해요</li>
 *   <li>Hibernate {@link ConstraintViolationException#getConstraintName()}: 드라이버가 서버 필드를 못 준 경우</li>
 *   <li>영문 메시지의 {@code constraint "이름"} 정규식: 위 둘이 모두 없을 때만 쓰는 마지막 폴백</li>
 * </ol>
 */
public final class ConstraintNames {

    /** PostgreSQL SQLState (Class 23 — Integrity Constraint Violation) */
    public static final String UNIQUE_VIOLATION = "23505";
    public static final String EXCLUSION_VIOLATION = "23P01";
    public static final String CHECK_VIOLATION = "23514";
    public static final String FOREIGN_KEY_VIOLATION = "23503";

    private static final Pattern QUOTED_CONSTRAINT = Pattern.compile("constraint \"([A-Za-z0-9_]+)\"");

    /**
     * 꺼낸 제약 위반 정보.
     *
     * @param constraint 제약(또는 유니크 인덱스) 이름
     * @param sqlState   SQLState, 예외 어디에도 없으면 null
     */
    public record Violation(String constraint, String sqlState) {
    }

    private ConstraintNames() {
    }

    /** 제약 이름만 필요할 때. 못 찾으면 null이에요. */
    public static String of(Throwable e) {
        Violation violation = violationOf(e);
        return violation == null ? null : violation.constraint();
    }

    /** 원인 사슬 전체를 한 번 훑어서 우선순위가 가장 높은 근거로 제약 이름을 정해요. 못 찾으면 null이에요. */
    public static Violation violationOf(Throwable e) {
        String serverConstraint = null;
        String serverSqlState = null;
        String hibernateConstraint = null;
        String sqlState = null;
        String messageConstraint = null;

        // 원인이 자기 자신을 가리키거나 순환하는 예외도 무한 루프에 빠지지 않게 방문한 예외를 기억해요
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = e; t != null && visited.add(t); t = nextOf(t)) {
            if (t instanceof PSQLException psql && serverConstraint == null) {
                ServerErrorMessage server = psql.getServerErrorMessage();
                if (server != null && server.getConstraint() != null) {
                    serverConstraint = server.getConstraint();
                    serverSqlState = server.getSQLState() != null ? server.getSQLState() : psql.getSQLState();
                }
            }
            if (t instanceof ConstraintViolationException cve && hibernateConstraint == null) {
                hibernateConstraint = cve.getConstraintName();
            }
            if (sqlState == null) {
                sqlState = sqlStateOf(t);
            }
            if (t instanceof SQLException sql && messageConstraint == null && sql.getMessage() != null) {
                Matcher m = QUOTED_CONSTRAINT.matcher(sql.getMessage());
                if (m.find()) {
                    messageConstraint = m.group(1);
                }
            }
        }

        if (serverConstraint != null) {
            return new Violation(serverConstraint, serverSqlState);
        }
        if (hibernateConstraint != null) {
            return new Violation(hibernateConstraint, sqlState);
        }
        if (messageConstraint != null) {
            return new Violation(messageConstraint, sqlState);
        }
        return null;
    }

    /**
     * 다음에 볼 예외. JDBC 배치(BatchUpdateException)는 실제 원인을 getCause()가 아니라
     * getNextException()에 두는 경우가 있어서, 원인이 없으면 그쪽도 따라가요.
     */
    private static Throwable nextOf(Throwable t) {
        if (t.getCause() != null) {
            return t.getCause();
        }
        return t instanceof SQLException sql ? sql.getNextException() : null;
    }

    private static String sqlStateOf(Throwable t) {
        if (t instanceof SQLException sql) {
            return sql.getSQLState();
        }
        if (t instanceof JDBCException jdbc) {
            return jdbc.getSQLState();
        }
        return null;
    }
}
