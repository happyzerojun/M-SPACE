package com.mspace.global.error;

import java.util.List;

/** API 명세서 0장 에러 응답. fieldErrors는 입력 검증 실패일 때만 채우고, 그 밖에는 빈 배열이에요. */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(BusinessException e) {
        return new ErrorResponse(e.getErrorCode().name(), e.getMessage(), e.getFieldErrors());
    }
}
