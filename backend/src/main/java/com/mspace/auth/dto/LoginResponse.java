package com.mspace.auth.dto;

import com.mspace.user.dto.UserResponse;

/** expiresIn은 초 단위 (AU-03) */
public record LoginResponse(String accessToken, long expiresIn, UserResponse user) {
}
