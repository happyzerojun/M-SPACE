package com.mspace.user.dto;

import com.mspace.user.domain.UserRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        String currentPassword,

        @NotBlank(message = UserRules.PASSWORD_MESSAGE)
        @Pattern(regexp = UserRules.PASSWORD_REGEX, message = UserRules.PASSWORD_MESSAGE)
        String newPassword
) {
}
