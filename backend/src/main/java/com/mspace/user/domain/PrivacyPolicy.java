package com.mspace.user.domain;

/**
 * 개인정보 처리방침 버전 (NF-11). 화면의 처리방침(frontend/src/pages/PrivacyPage.jsx)과 같은 값이에요.
 * 수집 항목·목적·보관 기간이 바뀌면 버전을 올리고 화면 문구를 함께 고쳐요 → 누가 어느 버전에 동의했는지 users에 남아요.
 */
public final class PrivacyPolicy {

    public static final String CURRENT_VERSION = "2026-10-21";

    private PrivacyPolicy() {
    }
}
