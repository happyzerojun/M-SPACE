package com.mspace.admin.controller;

import com.mspace.admin.dto.AdminReasonRequest;
import com.mspace.admin.dto.AdminReservationDetailResponse;
import com.mspace.admin.dto.DashboardResponse;
import com.mspace.admin.dto.KeyIssueRequest;
import com.mspace.admin.dto.KeyLogResponse;
import com.mspace.admin.dto.KeyMemoRequest;
import com.mspace.admin.service.AdminReservationService;
import com.mspace.global.common.PageCondition;
import com.mspace.global.common.PageResponse;
import com.mspace.global.security.LoginUser;
import com.mspace.reservation.domain.ReservationStatus;
import com.mspace.reservation.dto.ReservationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API 명세서 4.1: 관리자 예약·키. 주소 규칙(/api/admin/**)과 @PreAuthorize를 함께 걸어요 (AD-01) */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReservationController {

    private final AdminReservationService adminReservationService;

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return adminReservationService.dashboard(date);
    }

    @GetMapping("/reservations")
    public PageResponse<ReservationResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) @Size(max = 50, message = "검색어는 50자 이하로 입력해 주세요.") String q,
            @Valid @ModelAttribute PageCondition page) {
        return adminReservationService.search(date, from, to, roomId, status, q, page);
    }

    @GetMapping("/reservations/{id}")
    public AdminReservationDetailResponse detail(@PathVariable Long id) {
        return adminReservationService.detail(id);
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@AuthenticationPrincipal LoginUser me, @PathVariable Long id,
                                      @Valid @RequestBody AdminReasonRequest request) {
        return adminReservationService.cancel(me.id(), id, request.reason());
    }

    @PostMapping("/reservations/{id}/key-issue")
    public ReservationResponse issueKey(@AuthenticationPrincipal LoginUser me, @PathVariable Long id,
                                        @Valid @RequestBody KeyIssueRequest request) {
        return adminReservationService.issueKey(me.id(), id, request.identityVerified(), request.memo());
    }

    /** 본문 없이 보내도 돼요 (메모는 선택) */
    @PostMapping("/reservations/{id}/key-return")
    public ReservationResponse returnKey(@AuthenticationPrincipal LoginUser me, @PathVariable Long id,
                                         @Valid @RequestBody(required = false) KeyMemoRequest request) {
        return adminReservationService.returnKey(me.id(), id, request == null ? null : request.memo());
    }

    @GetMapping("/key-logs")
    public PageResponse<KeyLogResponse> keyLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) @Size(max = 50, message = "검색어는 50자 이하로 입력해 주세요.") String q,
            @Valid @ModelAttribute PageCondition page) {
        return adminReservationService.keyLogs(from, to, roomId, q, page);
    }
}
