package com.mspace.admin.repository;

import com.mspace.admin.dto.AdminUserResponse;
import com.mspace.global.common.PageCondition;
import com.mspace.global.util.KstTime;
import com.mspace.user.domain.UserRole;
import com.mspace.user.domain.UserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 회원 목록 (AD-07). 최근 노쇼 수·이용 제한까지 한 쿼리로 가져와요 (회원마다 따로 조회하는 N+1 방지, NF-07).
 * 예약·제한 엔티티가 아직 없는 단계라 네이티브 SQL로 읽어요.
 */
@Repository
@RequiredArgsConstructor
public class AdminUserQueryRepository {

    private static final String FROM_WHERE = """
            FROM users u
            WHERE (CAST(:status AS VARCHAR) IS NULL OR u.status = :status)
              AND (CAST(:q AS VARCHAR) IS NULL
                   OR u.student_no LIKE :qPrefix ESCAPE '\\'
                   OR u.name LIKE :qContains ESCAPE '\\')
            """;

    private static final String SELECT = """
            SELECT u.id, u.student_no, u.name, u.phone, u.role, u.status, u.must_change_password, u.created_at,
                   (SELECT count(*) FROM reservation r
                     WHERE r.user_id = u.id AND r.status = 'NO_SHOW'
                       AND r.start_at >= CAST(:now AS TIMESTAMPTZ)
                           - make_interval(days => (SELECT penalty_window_days FROM operation_policy WHERE id = 1))
                   ) AS recent_no_show_count,
                   (SELECT max(ur.end_at) FROM user_restriction ur
                     WHERE ur.user_id = u.id AND ur.lifted_at IS NULL AND ur.end_at > CAST(:now AS TIMESTAMPTZ)
                   ) AS restricted_until
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AdminUserResponse findOne(Long userId, Instant now) {
        return jdbc.queryForObject(SELECT + "FROM users u WHERE u.id = :id",
                new MapSqlParameterSource().addValue("id", userId).addValue("now", Timestamp.from(now)),
                AdminUserQueryRepository::map);
    }

    public Page<AdminUserResponse> search(String q, UserStatus status, PageCondition page, Instant now) {
        String keyword = q == null || q.isBlank() ? null : q.strip();
        String escaped = keyword == null ? null : keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", status == null ? null : status.name())
                .addValue("q", keyword)
                .addValue("qPrefix", escaped + "%")
                .addValue("qContains", "%" + escaped + "%")
                .addValue("now", Timestamp.from(now))
                .addValue("limit", page.size())
                .addValue("offset", (long) (page.page() - 1) * page.size());

        long total = jdbc.queryForObject("SELECT count(*) " + FROM_WHERE, params, Long.class);
        List<AdminUserResponse> content = jdbc.query(SELECT + FROM_WHERE + """
                ORDER BY u.student_no, u.id
                LIMIT :limit OFFSET :offset
                """, params, AdminUserQueryRepository::map);

        return new PageImpl<>(content, page.toPageable(Sort.unsorted()), total);
    }

    private static AdminUserResponse map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp restrictedUntil = rs.getTimestamp("restricted_until");
        return new AdminUserResponse(
                rs.getLong("id"),
                rs.getString("student_no"),
                rs.getString("name"),
                rs.getString("phone"),
                UserRole.valueOf(rs.getString("role")),
                UserStatus.valueOf(rs.getString("status")),
                rs.getBoolean("must_change_password"),
                rs.getInt("recent_no_show_count"),
                restrictedUntil == null ? null : KstTime.of(restrictedUntil.toInstant()),
                KstTime.of(rs.getTimestamp("created_at").toInstant()));
    }
}
