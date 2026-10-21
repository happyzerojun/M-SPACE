package com.mspace.auth.dto;

import com.mspace.user.domain.UserRules;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 회원가입 (AU-01). 이메일은 받지 않아요 (D-2) */
public record SignupRequest(
        @NotBlank(message = UserRules.STUDENT_NO_MESSAGE)
        @Pattern(regexp = UserRules.STUDENT_NO_REGEX, message = UserRules.STUDENT_NO_MESSAGE)
        String studentNo,

        @NotBlank(message = UserRules.NAME_MESSAGE)
        @Pattern(regexp = UserRules.NAME_REGEX, message = UserRules.NAME_MESSAGE)
        String name,

        @NotBlank(message = UserRules.PHONE_MESSAGE)
        @Pattern(regexp = UserRules.PHONE_REGEX, message = UserRules.PHONE_MESSAGE)
        String phone,

        @NotBlank(message = UserRules.PASSWORD_MESSAGE)
        @Pattern(regexp = UserRules.PASSWORD_REGEX, message = UserRules.PASSWORD_MESSAGE)
        String password,

        /** 개인정보 수집·이용 동의 (NF-11). 빠지거나 false면 가입이 안 돼요 */
        @NotNull(message = UserRules.PRIVACY_MESSAGE)
        @AssertTrue(message = UserRules.PRIVACY_MESSAGE)
        Boolean privacyAgreed
) {
}
