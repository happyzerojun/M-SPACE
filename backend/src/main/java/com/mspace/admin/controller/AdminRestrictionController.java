package com.mspace.admin.controller;

import com.mspace.admin.dto.AdminReasonRequest;
import com.mspace.admin.dto.RestrictionResponse;
import com.mspace.admin.service.AdminRestrictionService;
import com.mspace.global.common.PageCondition;
import com.mspace.global.common.PageResponse;
import com.mspace.global.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/** API 명세서 4.4: 이용 제한 조회·해제 (NS-03) */
@RestController
@RequestMapping("/api/admin/restrictions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRestrictionController {

    private final AdminRestrictionService restrictionService;

    @GetMapping
    public PageResponse<RestrictionResponse> search(@RequestParam(defaultValue = "true") boolean active,
                                                    @Valid @ModelAttribute PageCondition page) {
        return restrictionService.search(active, page);
    }

    @PostMapping("/{id}/lift")
    public RestrictionResponse lift(@AuthenticationPrincipal LoginUser me, @PathVariable Long id,
                                    @Valid @RequestBody AdminReasonRequest request) {
        return restrictionService.lift(me.id(), id, request.reason());
    }
}
