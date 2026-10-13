package com.mspace.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자 조치에는 사유를 남겨요 (AD-08) */
public record AdminReasonRequest(
        @NotBlank(message = "사유를 입력해 주세요.")
        @Size(max = 200, message = "사유는 200자 이하로 입력해 주세요.")
        String reason
) {
}
