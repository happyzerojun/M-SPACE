package com.mspace.global.error;

import com.mspace.global.error.ErrorResponse.FieldError;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
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
     * DB 제약 이름 → (기대 SQLState, 에러 코드). 애플리케이션 검사를 통과한 뒤 동시 요청이 끼어든 경우를 409로 바꿔요.
     * 중복·동시 요청은 락이 아니라 이 제약들이 막아요 (NF-05).
     * SQLState까지 맞춰 보는 이유: 이름만 보면 트리거 메시지 등에 같은 이름이 섞였을 때 엉뚱한 409가 나갈 수 있어서요 (TS-15).
     */
    static final Map<String, ConstraintRule> CONSTRAINT_ERRORS = Map.of(
            "ex_reservation_room_time", new ConstraintRule(ConstraintNames.EXCLUSION_VIOLATION, ErrorCode.RESERVATION_TIME_CONFLICT),  // RV-03
            "ex_reservation_user_time", new ConstraintRule(ConstraintNames.EXCLUSION_VIOLATION, ErrorCode.USER_TIME_CONFLICT),
            "uq_users_student_no_active", new ConstraintRule(ConstraintNames.UNIQUE_VIOLATION, ErrorCode.DUPLICATE_STUDENT_NO),        // AU-01
            "ex_operation_period_dates", new ConstraintRule(ConstraintNames.EXCLUSION_VIOLATION, ErrorCode.PERIOD_OVERLAP),            // PO-02
            "uq_key_log_reservation_action", new ConstraintRule(ConstraintNames.UNIQUE_VIOLATION, ErrorCode.INVALID_RESERVATION_STATE));

    /** 제약 하나가 어떤 SQLState로 위반되고, 그때 어떤 에러 코드로 바꾸는지 */
    record ConstraintRule(String sqlState, ErrorCode errorCode) {
    }

    /** 겹침 검사(EXCLUDE)가 걸린 테이블. 이 테이블 INSERT가 교착으로 끊기면 "이미 예약된 시간"으로 안내해요 (TS-16) */
    static final String RESERVATION_TABLE = "reservation";

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

    /**
     * DB 제약 위반. 알려진 제약이면 409, 그 밖에는 코드 버그일 가능성이 커서 500이에요.
     * 로그에는 제약 이름·SQLState만 남겨요. 예외를 그대로 넘기면 PostgreSQL DETAIL(위반한 행의 학번·이름·전화번호)이
     * 메시지 사슬에 섞여 로그에 남아서, 메시지를 가린 사본({@link DbErrorLog#masked})으로 스택만 남겨요 (NF-01, TS-16).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        ErrorCode errorCode = toErrorCode(e);
        if (errorCode == null) {
            ConstraintNames.Violation violation = ConstraintNames.violationOf(e);
            log.error("예상하지 못한 DB 제약 위반: constraint={}, sqlState={}",
                    violation == null ? null : violation.constraint(),
                    violation == null ? null : violation.sqlState(),
                    DbErrorLog.masked(e));
            return toResponse(ErrorCode.INTERNAL_ERROR);
        }
        return toResponse(errorCode);
    }

    /**
     * 동시 트랜잭션끼리 부딪혀 DB가 한쪽을 끊은 경우: 교착(40P01)·직렬화 실패(40001)·잠금 대기 실패 (TS-16).
     *
     * <p>겹치는 예약 INSERT가 동시에 들어오면 EXCLUDE 검사가 서로의 커밋 전 행을 기다리다 교착이 되고, PostgreSQL이
     * 한쪽(패자)을 40P01로 끊어요. 패자가 기다리던 상대가 바로 같은 시간을 잡은 예약이라, 패자에게 맞는 답은 23P01과 같은
     * "이미 예약된 시간"이에요. 그래서 재시도하지 않고 바로 {@code RESERVATION_TIME_CONFLICT}(409)로 바꿔요.
     * <ul>
     *   <li>재시도하지 않는 이유: 다시 해도 승자가 커밋한 뒤라 대부분 23P01(같은 409)로 끝나요. 인기 시간대가 열리는
     *       순간처럼 요청이 몰릴 때 재시도는 DB 부하만 키우고, 재시도는 트랜잭션 전체를 다시 실행해야 해서 서비스 구조도
     *       복잡해져요.</li>
     *   <li>감수하는 것: 승자가 나중에 롤백하면 실제로는 빈 시간인데 409를 받을 수 있어요(현황판 새로고침 후 다시 신청).
     *       교착 에러에는 제약 이름이 없어 같은 학생의 시간 겹침({@code USER_TIME_CONFLICT})과 구분하지 못해요.</li>
     *   <li>예약 INSERT가 아닌 교착(예: 같은 행을 동시에 수정)은 무엇과 부딪혔는지 단정할 수 없어
     *       {@code CONCURRENT_UPDATE}(409, "새로고침 후 다시 시도")로 안내해요.</li>
     * </ul>
     * 예전에는 이 예외를 처리하는 곳이 없어 아래 {@link #handleUnexpected}로 떨어져 500이었어요.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleLockFailure(PessimisticLockingFailureException e) {
        ErrorCode errorCode = toTransactionConflictCode(e);
        return toTransactionConflictResponse(e, errorCode == null ? ErrorCode.CONCURRENT_UPDATE : errorCode);
    }

    /**
     * 교착·직렬화 실패면 에러 코드, 아니면 null. 예약 테이블 INSERT면 RESERVATION_TIME_CONFLICT, 그 밖에는 CONCURRENT_UPDATE.
     * 서비스에서 예외를 직접 잡을 때도 써요.
     */
    public static ErrorCode toTransactionConflictCode(Throwable e) {
        ConstraintNames.TransactionConflict conflict = ConstraintNames.transactionConflictOf(e);
        if (conflict == null) {
            return null;
        }
        return conflict.isInsertInto(RESERVATION_TABLE) ? ErrorCode.RESERVATION_TIME_CONFLICT : ErrorCode.CONCURRENT_UPDATE;
    }

    private ResponseEntity<ErrorResponse> toTransactionConflictResponse(Throwable e, ErrorCode errorCode) {
        ConstraintNames.TransactionConflict conflict = ConstraintNames.transactionConflictOf(e);
        // 동시 요청이 몰리면 정상적으로 생기는 일이라 스택 없이 WARN 한 줄만 남겨요 (DETAIL은 프로세스 번호뿐이지만 남기지 않아요)
        log.warn("동시 트랜잭션 충돌 → {}: sqlState={}, statement={} {}", errorCode,
                conflict == null ? null : conflict.sqlState(),
                conflict == null ? null : conflict.operation(),
                conflict == null ? null : conflict.table());
        return toResponse(errorCode);
    }

    /** 알려진 제약이면 에러 코드, 아니면 null. 서비스에서 제약 위반을 직접 잡을 때도 써요. */
    public static ErrorCode toErrorCode(Throwable e) {
        ConstraintNames.Violation violation = ConstraintNames.violationOf(e);
        if (violation == null) {
            return null;
        }
        ConstraintRule rule = CONSTRAINT_ERRORS.get(violation.constraint());
        if (rule == null) {
            return null;
        }
        // SQLState를 알 수 없으면(드라이버·래퍼가 안 준 경우) 이름만으로 판단하고, 알면 기대값과 같아야 해요
        if (violation.sqlState() != null && !rule.sqlState().equals(violation.sqlState())) {
            return null;
        }
        return rule.errorCode();
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
        // 커밋 시점 등 다른 예외로 감싸여 온 교착·직렬화 실패도 500이 아니라 409로 (TS-16)
        ErrorCode transactionConflict = toTransactionConflictCode(e);
        if (transactionConflict != null) {
            return toTransactionConflictResponse(e, transactionConflict);
        }
        // DB 예외가 섞여 있으면 메시지(DETAIL)를 가린 사본으로 남겨요 (TS-16)
        log.error("처리되지 않은 예외", DbErrorLog.masked(e));
        return toResponse(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }
}
