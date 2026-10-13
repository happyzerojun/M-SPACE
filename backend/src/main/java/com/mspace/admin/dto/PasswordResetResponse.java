package com.mspace.admin.dto;

/** 임시 비밀번호는 이 응답에서만 보여요. 저장·로그에 남기지 않아요 (AU-07) */
public record PasswordResetResponse(String temporaryPassword) {
}
