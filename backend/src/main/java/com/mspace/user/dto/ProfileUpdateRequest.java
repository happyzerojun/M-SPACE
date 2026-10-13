package com.mspace.user.dto;

import com.mspace.user.domain.UserRules;
import jakarta.validation.constraints.Pattern;

/** 보낸 값만 바꿔요 (null은 그대로). AU-05 */
public record ProfileUpdateRequest(
        @Pattern(regexp = UserRules.NAME_REGEX, message = UserRules.NAME_MESSAGE)
        String name,

        @Pattern(regexp = UserRules.PHONE_REGEX, message = UserRules.PHONE_MESSAGE)
        String phone
) {
}
