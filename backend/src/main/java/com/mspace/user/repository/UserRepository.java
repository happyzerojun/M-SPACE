package com.mspace.user.repository;

import com.mspace.user.domain.User;
import com.mspace.user.domain.UserRole;
import com.mspace.user.domain.UserStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 학번은 활성 계정끼리만 유일해요 (uq_users_student_no_active). 비활성 계정은 로그인 대상이 아니에요 */
    Optional<User> findByStudentNoAndStatus(String studentNo, UserStatus status);

    boolean existsByStudentNoAndStatus(String studentNo, UserStatus status);

    /** 첫 배포 때 관리자가 이미 있는지 (AdminBootstrap) */
    boolean existsByRoleAndStatus(UserRole role, UserStatus status);

    /**
     * 예약 신청자 행 잠금 (설계서 5장 8번). 같은 학생이 동시에 두 건을 신청하면 뒤 요청이 앞 요청의 커밋을 기다렸다가
     * 하루 한도를 다시 세요 → "확인 후 저장" 사이에 끼어들어 한도를 넘기는 일을 막아요.
     * 다른 학생끼리는 서로 다른 행이라 기다리지 않아요.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
