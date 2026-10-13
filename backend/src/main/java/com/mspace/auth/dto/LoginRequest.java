package com.mspace.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "학번을 입력해 주세요.")
        String studentNo,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {
}
