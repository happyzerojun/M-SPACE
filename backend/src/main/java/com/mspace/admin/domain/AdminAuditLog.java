package com.mspace.admin.domain;

import com.mspace.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자 조치 기록 (AD-08). 추가만 하고 고치지 않아요.
 * 비밀번호·전화번호 같은 값은 넣지 않고 "누가, 무엇을, 누구에게, 왜"만 남겨요 (NF-01).
 */
@Getter
@Entity
@Table(name = "admin_audit_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(length = 200)
    private String reason;

    public static AdminAuditLog of(Long actorId, AdminAction action, String targetType, Long targetId, String reason) {
        AdminAuditLog log = new AdminAuditLog();
        log.actorId = actorId;
        log.action = action.name();
        log.targetType = targetType;
        log.targetId = targetId;
        log.reason = reason;
        return log;
    }
}
