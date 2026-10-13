package com.mspace.user.service;

import com.mspace.global.error.BusinessException;
import com.mspace.global.error.ErrorCode;
import com.mspace.global.error.ErrorResponse.FieldError;
import com.mspace.user.domain.User;
import com.mspace.user.domain.UserRules;
import com.mspace.user.dto.PasswordChangeRequest;
import com.mspace.user.dto.ProfileUpdateRequest;
import com.mspace.user.dto.UserResponse;
import com.mspace.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 정보 조회·수정·비밀번호 변경 (AU-05, AU-07) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse getMe(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    @Transactional
    public UserResponse updateMe(Long userId, ProfileUpdateRequest request) {
        User user = findUser(userId);
        user.updateProfile(UserRules.normalizeName(request.name()), UserRules.normalizePhone(request.phone()));
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldError("currentPassword", "현재 비밀번호가 맞지 않아요.")));
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldError("newPassword", "현재 비밀번호와 다른 비밀번호를 입력해 주세요.")));
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /** 필터에서 활성 계정만 로그인 상태로 만들어서, 여기서 못 찾는 건 요청 사이에 비활성화된 경우뿐이에요 */
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
