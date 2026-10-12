package com.mspace.global.error;

import com.mspace.global.error.ErrorResponse.FieldError;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 컨트롤러에서 나온 모든 예외를 ErrorResponse 형태로 바꿔요. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * DB 제약 이름 → 에러 코드. 애플리케이션 검사를 통과한 뒤 동시 요청이 끼어든 경우를 409로 바꿔요.
     * 중복·동시 요청은 락이 아니라 이 제약들이 막아요 (NF-05).
     */
    static final Map<String, ErrorCode> CONSTRAINT_ERRORS = Map.of(
            "ex_reservation_room_time", ErrorCode.RESERVATION_TIME_CONFLICT,   // RV-03
            "ex_reservation_user_time", ErrorCode.USER_TIME_CONFLICT,
            "uq_users_student_no_active", ErrorCode.DUPLICATE_STUDENT_NO,      // AU-01
            "ex_operation_period_dates", ErrorCode.PERIOD_OVERLAP,             // PO-02
            "uq_key_log_reservation_action", ErrorCode.INVALID_RESERVATION_STATE);

    /** 서비스 규칙 위반 (가장 많이 쓰여요) */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus()).body(ErrorResponse.of(e));
    }

    /** @Valid @RequestBody 검증 실패 → 어느 입력칸이 틀렸는지 fieldErrors로 알려줘요 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return handleBusiness(new BusinessException(ErrorCode.VALIDATION_FAILED, fieldErrors));
    }

    /** @RequestParam, @PathVariable 검증 실패 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException e) {
        List<FieldError> fieldErrors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldError(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return handleBusiness(new BusinessException(ErrorCode.VALIDATION_FAILED, fieldErrors));
    }

    /** JSON 형식 오류, 필수 파라미터 누락, 타입 불일치 (예: ?date=abc) */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return toResponse(ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return toResponse(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** DB 제약 위반. 알려진 제약이면 409, 그 밖에는 코드 버그일 가능성이 커서 500이에요. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        ErrorCode errorCode = toErrorCode(e);
        if (errorCode == null) {
            log.error("예상하지 못한 DB 제약 위반: {}", ConstraintNames.of(e), e);
            return toResponse(ErrorCode.INTERNAL_ERROR);
        }
        return toResponse(errorCode);
    }

    /** 알려진 제약이면 에러 코드, 아니면 null. 서비스에서 제약 위반을 직접 잡을 때도 써요. */
    public static ErrorCode toErrorCode(Throwable e) {
        String constraint = ConstraintNames.of(e);
        return constraint == null ? null : CONSTRAINT_ERRORS.get(constraint);
    }

    /** 키 지급과 노쇼 처리처럼 같은 예약을 동시에 바꾼 경우 (reservation.version) */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return toResponse(ErrorCode.CONCURRENT_UPDATE);
    }

    /** @PreAuthorize 거부가 컨트롤러까지 올라온 경우 (아래 Exception 처리에 먹혀서 500이 되지 않게) */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return toResponse(ErrorCode.FORBIDDEN);
    }

    /** 예상하지 못한 오류: 원인은 서버 로그에만 남기고, 응답에는 내부 정보를 숨겨요 (NF-01) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return toResponse(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }
}
