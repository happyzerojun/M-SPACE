package com.mspace.global.error;

import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.hibernate.HibernateException;
import org.hibernate.JDBCException;
import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataAccessException;

/**
 * DB 예외를 로그에 남길 때 쓰는 안전한 사본을 만들어요 (NF-01, TS-16).
 *
 * <p>PostgreSQL 에러의 DETAIL에는 위반한 행의 값이 그대로 들어 있어요. 예: {@code Key (student_no)=(…) already exists},
 * {@code Failing row contains (…, 이름, 전화번호, …)}. 예외를 그대로 {@code log.error(…, e)}에 넘기면 원인 사슬의 메시지가
 * 전부 로그에 찍혀서 학번·이름·전화번호가 남아요(CLAUDE.md 6장: 실제 학생 개인정보를 로그에 넣지 않아요).
 *
 * <p>그래서 DB 계열 예외는 메시지를 버리고 <b>원래 클래스 이름 + SQLState + 제약 이름</b>만 남긴 사본으로 바꿔요.
 * 스택 트레이스는 그대로 복사하니까 어디서 났는지는 추적할 수 있어요.
 * 드라이버 설정 {@code logServerErrorDetail=false}(application.yaml)가 1차 방어이고, 이건 그 설정이 빠져도
 * 이 핸들러의 로그로는 새지 않게 하는 2차 방어예요.
 */
public final class DbErrorLog {

    private DbErrorLog() {
    }

    /** 로그용 사본. 원인 사슬에 DB 예외가 없으면 원래 예외를 그대로 돌려줘요. */
    public static Throwable masked(Throwable e) {
        List<Throwable> chain = chainOf(e);
        if (chain.stream().noneMatch(DbErrorLog::isDbException)) {
            return e;
        }
        Throwable cause = null;
        for (int i = chain.size() - 1; i >= 0; i--) {
            Throwable original = chain.get(i);
            Throwable copy = new MaskedException(describe(original), cause);
            copy.setStackTrace(original.getStackTrace());
            cause = copy;
        }
        return cause;
    }

    /** 한 줄 설명. DB 예외면 메시지 대신 SQLState·제약 이름만 적어요. */
    static String describe(Throwable t) {
        String className = t.getClass().getName();
        if (!isDbException(t)) {
            return t.getMessage() == null ? className : className + ": " + t.getMessage();
        }
        String sqlState = null;
        String constraint = null;
        if (t instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
            ServerErrorMessage server = psql.getServerErrorMessage();
            sqlState = server.getSQLState();
            constraint = server.getConstraint();
        }
        if (sqlState == null && t instanceof SQLException sql) {
            sqlState = sql.getSQLState();
        }
        if (t instanceof JDBCException jdbc) {
            sqlState = jdbc.getSQLState();
        }
        if (t instanceof ConstraintViolationException cve) {
            constraint = cve.getConstraintName();
        }
        if (sqlState == null && constraint == null) {
            return className + " [message masked]";
        }
        return className + " [SQLState=" + sqlState + ", constraint=" + constraint + "]";
    }

    /**
     * 메시지에 DB 서버 에러 문장(DETAIL 포함)이 섞일 수 있는 예외들.
     * Spring·Hibernate·JPA 예외도 원인 드라이버 예외의 메시지를 자기 메시지에 복사해 두어서 함께 가려요.
     */
    private static boolean isDbException(Throwable t) {
        return t instanceof SQLException
                || t instanceof HibernateException
                || t instanceof PersistenceException
                || t instanceof DataAccessException;
    }

    private static List<Throwable> chainOf(Throwable e) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = e; t != null && visited.add(t); t = nextOf(t)) {
            chain.add(t);
        }
        return chain;
    }

    /** ConstraintNames와 같은 규칙: JDBC 배치는 원인을 getNextException()에 두기도 해요 */
    private static Throwable nextOf(Throwable t) {
        if (t.getCause() != null) {
            return t.getCause();
        }
        return t instanceof SQLException sql ? sql.getNextException() : null;
    }

    /** 로그 전용 예외. 메시지 앞에 원래 예외 클래스 이름을 적어 두었어요. */
    static final class MaskedException extends RuntimeException {

        MaskedException(String message, Throwable cause) {
            super(message, cause, false, true);
        }
    }
}
