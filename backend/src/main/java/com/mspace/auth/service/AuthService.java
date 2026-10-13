package com.mspace.auth.service;

import com.mspace.auth.dto.LoginRequest;
import com.mspace.auth.dto.LoginResponse;
import com.mspace.auth.dto.SignupRequest;
import com.mspace.global.error.BusinessException;
import com.mspace.global.error.ErrorCode;
import com.mspace.global.security.JwtTokenProvider;
import com.mspace.user.domain.User;
import com.mspace.user.domain.UserRole;
import com.mspace.user.domain.UserRules;
import com.mspace.user.domain.UserStatus;
import com.mspace.user.dto.UserResponse;
import com.mspace.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입·로그인 (AU-01, AU-03) */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    /** 없는 학번에도 해시 비교를 한 번 해서, 응답 시간으로 가입 여부를 알아낼 수 없게 해요 */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-password1");
    }

    /**
     * 학생으로만 가입돼요. 관리자는 기존 관리자·초기 데이터로 지정해요 (AU-06).
     * 먼저 확인해서 친절한 에러를 주고, 확인과 저장 사이에 끼어든 동시 가입은 DB 제약이 막아요 (uq_users_student_no_active → 409)
     */
    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByStudentNoAndStatus(request.studentNo(), UserStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.DUPLICATE_STUDENT_NO);
        }
        User user = User.create(request.studentNo(), UserRules.normalizeName(request.name()),
                UserRules.normalizePhone(request.phone()), passwordEncoder.encode(request.password()), UserRole.STUDENT);
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    /** 학번 없음·비밀번호 틀림·비활성 계정은 모두 같은 LOGIN_FAILED예요 (어느 쪽인지 알려주지 않아요) */
    public LoginResponse login(LoginRequest request) {
        Optional<User> found = userRepository.findByStudentNoAndStatus(request.studentNo(), UserStatus.ACTIVE);
        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        User user = found.filter(u -> matches)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        return new LoginResponse(tokenProvider.createAccessToken(user.getId()), tokenProvider.getValiditySeconds(),
                UserResponse.from(user));
    }
}
