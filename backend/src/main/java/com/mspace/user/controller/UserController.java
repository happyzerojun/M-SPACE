package com.mspace.user.controller;

import com.mspace.global.security.LoginUser;
import com.mspace.user.dto.PasswordChangeRequest;
import com.mspace.user.dto.ProfileUpdateRequest;
import com.mspace.user.dto.UserResponse;
import com.mspace.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API 명세서 1장: 내 정보 (로그인 필요 — SecurityConfig의 /api/** 규칙) */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserResponse getMe(@AuthenticationPrincipal LoginUser me) {
        return userService.getMe(me.id());
    }

    @PatchMapping
    public UserResponse updateMe(@AuthenticationPrincipal LoginUser me, @Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateMe(me.id(), request);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal LoginUser me,
                                               @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(me.id(), request);
        return ResponseEntity.noContent().build();
    }
}
