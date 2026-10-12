package com.mspace.global.error;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;

/**
 * DB 제약 위반 예외에서 제약 이름을 꺼내요.
 * Hibernate가 이름을 알려주면 그걸 쓰고, 못 알려주면(EXCLUDE 제약 등) PostgreSQL 메시지의
 * {@code constraint "ex_reservation_room_time"} 부분에서 읽어요.
 */
public final class ConstraintNames {

    private static final Pattern QUOTED_CONSTRAINT = Pattern.compile("constraint \"([A-Za-z0-9_]+)\"");

    private ConstraintNames() {
    }

    public static String of(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                return cve.getConstraintName();
            }
            if (t instanceof SQLException sql && sql.getMessage() != null) {
                Matcher m = QUOTED_CONSTRAINT.matcher(sql.getMessage());
                if (m.find()) {
                    return m.group(1);
                }
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }
}
