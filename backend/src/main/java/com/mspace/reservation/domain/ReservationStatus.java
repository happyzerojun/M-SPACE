package com.mspace.reservation.domain;

import java.util.List;

/** 예약 상태 (docs/예약-상태-설계.md 1장). 화면 전용 OVERDUE(연체)는 저장하지 않아요 */
public enum ReservationStatus {
    RESERVED,   // 확정, 키 수령 전
    IN_USE,     // 키 지급됨(키 예약) 또는 셀프 체크인(개방 기간 예약)
    RETURNED,   // 반납 확인 완료
    COMPLETED,  // 이용 종료 (개방 기간 예약)
    CANCELLED,  // 본인 또는 관리자 취소
    NO_SHOW;    // 키 미수령·체크인 안 함

    /** 시간 겹침 검사 대상 (EXCLUDE 제약의 WHERE와 같아요, RV-03) */
    public static final List<ReservationStatus> ACTIVE = List.of(RESERVED, IN_USE);

    /** 하루 한도에 세는 상태: 취소·노쇼는 빼요 (설계서 1장 표) */
    public static final List<ReservationStatus> COUNTED_FOR_DAILY_LIMIT = List.of(RESERVED, IN_USE, RETURNED, COMPLETED);
}
