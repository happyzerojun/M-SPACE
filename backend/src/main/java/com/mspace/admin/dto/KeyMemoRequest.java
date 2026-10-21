package com.mspace.admin.dto;

import jakarta.validation.constraints.Size;

/** 반납 확인 (KY-03). 메모는 선택이에요 (예: "키 고리 파손") */
public record KeyMemoRequest(
        @Size(max = 200, message = "메모는 200자 이하로 입력해 주세요.")
        String memo
) {
}
