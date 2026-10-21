package com.mspace.policy.controller;

import com.mspace.policy.dto.PublicPolicyResponse;
import com.mspace.policy.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/policy — 비로그인 허용 (API 명세서 2장) */
@RestController
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @GetMapping("/api/policy")
    public PublicPolicyResponse policy() {
        return PublicPolicyResponse.from(policyService.policy());
    }
}
