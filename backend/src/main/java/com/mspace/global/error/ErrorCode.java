package com.mspace.global.error;

import java.text.MessageFormat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 명세서 0장의 에러 코드. code 값은 enum 이름 그대로 응답에 나가요.
 * message는 사용자에게 바로 보여줄 해요체 문장이라 내부 정보를 넣지 않아요 (NF-01).
 * {0}, {1}은 BusinessException의 args로 채워요.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "이 기능을 사용할 권한이 없어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없어요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "다른 처리와 겹쳤어요. 새로고침 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요."),

    // 회원·인증
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "학번 또는 비밀번호가 맞지 않아요."),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "임시 비밀번호로 로그인했어요. 비밀번호를 먼저 바꿔 주세요."),
    DUPLICATE_STUDENT_NO(HttpStatus.CONFLICT, "이미 가입된 학번이에요. 본인 학번이라면 학과실에 문의해 주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없어요."),
    USER_RESTRICTED(HttpStatus.FORBIDDEN, "노쇼 누적으로 {0}까지 예약할 수 없어요."),

    // 방
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없어요."),
    ROOM_INACTIVE(HttpStatus.CONFLICT, "지금은 이용할 수 없는 방이에요."),

    // 예약
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없어요."),
    HEADCOUNT_EXCEEDS_CAPACITY(HttpStatus.BAD_REQUEST, "이 방은 최대 {0}명까지 이용할 수 있어요."),
    RESERVATION_IN_PAST(HttpStatus.BAD_REQUEST, "지금 이후 시간만 예약할 수 있어요."),
    RESERVATION_OUT_OF_WINDOW(HttpStatus.BAD_REQUEST, "오늘부터 {0}일 뒤까지만 예약할 수 있어요."),
    OUTSIDE_OPERATING_HOURS(HttpStatus.BAD_REQUEST, "운영 시간({0}~{1}) 안에서만 예약할 수 있어요."),
    CLOSED_DAY(HttpStatus.BAD_REQUEST, "그날은 휴관이에요."),
    RESERVATION_TOO_LONG(HttpStatus.BAD_REQUEST, "한 번에 최대 {0}시간까지 예약할 수 있어요."),
    DAILY_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "하루에 {0}건까지 예약할 수 있어요."),
    RESERVATION_TIME_CONFLICT(HttpStatus.CONFLICT, "이미 예약된 시간이에요. 다른 시간을 골라 주세요."),
    USER_TIME_CONFLICT(HttpStatus.CONFLICT, "같은 시간에 이미 다른 예약이 있어요."),
    INVALID_RESERVATION_STATE(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 작업이에요."),
    CANCEL_AFTER_START(HttpStatus.CONFLICT, "시작 시간이 지난 예약은 취소할 수 없어요."),
    CHECK_IN_NOT_ALLOWED_TIME(HttpStatus.CONFLICT, "이용 시작은 시작 {0}분 전부터 시작 {1}분 후까지 할 수 있어요."),

    // 키
    IDENTITY_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "학생증으로 본인 확인을 먼저 해 주세요."),
    KEY_ISSUE_NOT_ALLOWED_TIME(HttpStatus.CONFLICT, "키는 시작 {0}분 전부터 시작 {1}분 후까지 지급할 수 있어요."),

    // 운영 정책·이용 제한
    PERIOD_NOT_FOUND(HttpStatus.NOT_FOUND, "특별 운영 기간을 찾을 수 없어요."),
    PERIOD_OVERLAP(HttpStatus.CONFLICT, "다른 특별 운영 기간과 날짜가 겹쳐요."),
    RESTRICTION_NOT_FOUND(HttpStatus.NOT_FOUND, "이용 제한을 찾을 수 없어요."),
    INVALID_RESTRICTION_STATE(HttpStatus.CONFLICT, "이미 해제된 이용 제한이에요.");

    private final HttpStatus status;
    private final String message;

    /** 메시지의 {0}, {1} 자리를 채워요. 인자가 없으면 원문 그대로예요. */
    public String format(Object... args) {
        return args == null || args.length == 0 ? message : MessageFormat.format(message, args);
    }
}
