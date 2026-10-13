package com.mspace.user.repository;

import com.mspace.user.domain.User;
import com.mspace.user.domain.UserStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 학번은 활성 계정끼리만 유일해요 (uq_users_student_no_active). 비활성 계정은 로그인 대상이 아니에요 */
    Optional<User> findByStudentNoAndStatus(String studentNo, UserStatus status);

    boolean existsByStudentNoAndStatus(String studentNo, UserStatus status);
}
