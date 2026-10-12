package com.mspace.global.error;

import com.mspace.global.error.ErrorResponse.FieldError;
import java.util.List;
import lombok.Getter;

/**
 * 서비스 규칙 위반을 알리는 예외. 예: throw new BusinessException(ErrorCode.CANCEL_AFTER_START);
 * RuntimeException이라 @Transactional 안에서 던지면 자동으로 롤백돼요.
 * 메시지에 값이 들어가야 하면(예: "최대 4명") args로 넘겨요. ErrorCode의 {0}, {1} 자리에 들어가요.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldError> fieldErrors;

    public BusinessException(ErrorCode errorCode, Object... args) {
        this(errorCode, List.of(), args);
    }

    public BusinessException(ErrorCode errorCode, List<FieldError> fieldErrors, Object... args) {
        super(errorCode.format(args));
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;
    }
}
