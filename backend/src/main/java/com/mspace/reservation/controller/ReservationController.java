package com.mspace.reservation.controller;

import com.mspace.global.common.PageCondition;
import com.mspace.global.common.PageResponse;
import com.mspace.global.security.LoginUser;
import com.mspace.reservation.dto.ReservationCreateRequest;
import com.mspace.reservation.dto.ReservationResponse;
import com.mspace.reservation.service.ReservationService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세서 3장: 학생 예약 (로그인 필요 — SecurityConfig의 /api/** 규칙).
 * 본인 예약인지는 서비스가 다시 확인하고, 남의 예약은 404예요 (RV-09).
 */
@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/api/reservations")
    public ResponseEntity<ReservationResponse> create(@AuthenticationPrincipal LoginUser me,
                                                      @Valid @RequestBody ReservationCreateRequest request) {
        ReservationResponse created = reservationService.create(me.id(), request);
        return ResponseEntity.created(URI.create("/api/reservations/" + created.id())).body(created);
    }

    @GetMapping("/api/me/reservations")
    public PageResponse<ReservationResponse> myReservations(@AuthenticationPrincipal LoginUser me,
                                                            @RequestParam(required = false) String scope,
                                                            @Valid @ModelAttribute PageCondition page) {
        return reservationService.myReservations(me.id(), scope, page);
    }

    @GetMapping("/api/reservations/{id}")
    public ReservationResponse get(@AuthenticationPrincipal LoginUser me, @PathVariable Long id) {
        return reservationService.get(me.id(), id);
    }

    @PostMapping("/api/reservations/{id}/cancel")
    public ReservationResponse cancel(@AuthenticationPrincipal LoginUser me, @PathVariable Long id) {
        return reservationService.cancel(me.id(), id);
    }

    @PostMapping("/api/reservations/{id}/check-in")
    public ReservationResponse checkIn(@AuthenticationPrincipal LoginUser me, @PathVariable Long id) {
        return reservationService.checkIn(me.id(), id);
    }

    @PostMapping("/api/reservations/{id}/finish")
    public ReservationResponse finish(@AuthenticationPrincipal LoginUser me, @PathVariable Long id) {
        return reservationService.finish(me.id(), id);
    }
}
