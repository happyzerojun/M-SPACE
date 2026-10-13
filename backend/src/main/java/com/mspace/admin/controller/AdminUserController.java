package com.mspace.admin.controller;

import com.mspace.admin.dto.AdminReasonRequest;
import com.mspace.admin.dto.AdminUserResponse;
import com.mspace.admin.dto.PasswordResetResponse;
import com.mspace.admin.service.AdminUserService;
import com.mspace.global.common.PageCondition;
import com.mspace.global.common.PageResponse;
import com.mspace.global.security.LoginUser;
import com.mspace.user.domain.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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

/** API 명세서 4.4: 관리자 회원 관리. 주소 규칙(/api/admin/**)과 @PreAuthorize를 함께 걸어요 (AD-01) */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public PageResponse<AdminUserResponse> search(
            @RequestParam(required = false) @Size(max = 50, message = "검색어는 50자 이하로 입력해 주세요.") String q,
            @RequestParam(required = false) UserStatus status,
            @Valid @ModelAttribute PageCondition page) {
        return adminUserService.search(q, status, page);
    }

    @PostMapping("/{userId}/password-reset")
    public ResponseEntity<PasswordResetResponse> resetPassword(@AuthenticationPrincipal LoginUser me,
                                                               @PathVariable Long userId,
                                                               @Valid @RequestBody AdminReasonRequest request) {
        // 임시 비밀번호가 브라우저·프록시 캐시에 남지 않게 해요
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminUserService.resetPassword(me.id(), userId, request.reason()));
    }

    @PostMapping("/{userId}/disable")
    public AdminUserResponse disable(@AuthenticationPrincipal LoginUser me, @PathVariable Long userId,
                                     @Valid @RequestBody AdminReasonRequest request) {
        return adminUserService.disable(me.id(), userId, request.reason());
    }
}
