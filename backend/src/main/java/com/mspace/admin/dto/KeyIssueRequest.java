package com.mspace.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 키 지급 (KY-01). 관리자가 실물 학생증의 학번·이름이 예약자와 같은지 보고 identityVerified=true로 보내요.
 * 학생증 사진은 받지 않아요 (개인정보 최소 수집, CLAUDE.md 6장).
 */
public record KeyIssueRequest(
        @NotNull(message = "본인 확인 여부를 보내 주세요.")
        Boolean identityVerified,

        @Size(max = 200, message = "메모는 200자 이하로 입력해 주세요.")
        String memo
) {
}
