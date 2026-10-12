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

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
