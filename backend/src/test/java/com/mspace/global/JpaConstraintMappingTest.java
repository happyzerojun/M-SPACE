package com.mspace.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mspace.IntegrationTest;
import com.mspace.global.error.ErrorCode;
import com.mspace.global.error.GlobalExceptionHandler;
import com.mspace.user.domain.User;
import com.mspace.user.domain.UserRole;
import com.mspace.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * JPA(Hibernate)로 저장할 때 난 제약 위반도 에러 코드로 바뀌는지 (TS-05).
 * 서비스의 "먼저 확인"을 건너뛰고 바로 저장해서, 동시 요청이 확인을 통과한 상황을 확정적으로 만들어요.
 */
class JpaConstraintMappingTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void 부분_유니크_인덱스_위반은_DUPLICATE_STUDENT_NO() {
        userRepository.saveAndFlush(User.create("99000001", "가", "010-0000-0000", "x", UserRole.STUDENT));

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(User.create("99000001", "나", "010-0000-0000", "x", UserRole.STUDENT)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(GlobalExceptionHandler.toErrorCode(e)).isEqualTo(ErrorCode.DUPLICATE_STUDENT_NO));
    }
}
