package com.mspace.user.domain;

import com.mspace.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 (AU-01). 이메일은 받지 않아요 (D-2).
 * 학번은 활성 계정끼리만 유일해요(uq_users_student_no_active) → 비활성화 후 재가입 가능 (AU-08).
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_no", nullable = false, length = 20)
    private String studentNo;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /** 가입할 때 개인정보 수집 및 이용에 동의한 시각과 처리방침 버전 (NF-12). V3 이전 가입 계정은 null이에요 */
    @Column(name = "privacy_agreed_at")
    private Instant privacyAgreedAt;

    @Column(name = "privacy_policy_version", length = 20)
    private String privacyPolicyVersion;

    public static User create(String studentNo, String name, String phone, String passwordHash, UserRole role) {
        User user = new User();
        user.studentNo = studentNo;
        user.name = name;
        user.phone = phone;
        user.passwordHash = passwordHash;
        user.role = role;
        user.status = UserStatus.ACTIVE;
        return user;
    }

    /** 동의 없이는 가입할 수 없어요. 동의 문구는 화면의 처리방침 버전과 같아요 (AU-01, NF-12) */
    public void agreePrivacy(Instant agreedAt, String policyVersion) {
        this.privacyAgreedAt = agreedAt;
        this.privacyPolicyVersion = policyVersion;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /** 내 정보 수정 (AU-05). null인 값은 그대로 둬요. 학번·역할은 바꿀 수 없어요. */
    public void updateProfile(String name, String phone) {
        if (name != null) this.name = name;
        if (phone != null) this.phone = phone;
    }

    /** 본인이 비밀번호를 바꾸면 임시 비밀번호 상태가 풀려요 (AU-05, AU-07) */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
    }

    /** 관리자 초기화: 다음 로그인 뒤 다른 기능을 쓰기 전에 비밀번호를 바꾸게 해요 (AU-07) */
    public void resetPassword(String temporaryPasswordHash) {
        this.passwordHash = temporaryPasswordHash;
        this.mustChangePassword = true;
    }

    /** 남의 학번으로 먼저 가입된 계정 등을 막아요. 학번은 활성 계정끼리만 유일해서 본인이 다시 가입할 수 있어요 (AU-08) */
    public void disable() {
        this.status = UserStatus.DISABLED;
    }
}
