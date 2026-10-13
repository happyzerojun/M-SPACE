package com.mspace.user.domain;

/**
 * 회원 입력 규칙 (API 명세서 1장). 요청 DTO의 Bean Validation이 이 값을 함께 써서 규칙이 한곳에 있게 해요.
 */
public final class UserRules {

    public static final String STUDENT_NO_REGEX = "^\\d{8,10}$";
    public static final String STUDENT_NO_MESSAGE = "학번은 숫자 8~10자리로 입력해 주세요.";

    /** 하이픈 없이 입력해도 받아요. 저장할 때 normalizePhone()으로 맞춰요 */
    public static final String PHONE_REGEX = "^01[016789]-?\\d{3,4}-?\\d{4}$";
    public static final String PHONE_MESSAGE = "전화번호는 010-1234-5678 형식으로 입력해 주세요.";

    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    public static final String PASSWORD_MESSAGE = "비밀번호는 영문과 숫자를 모두 넣어 8~64자로 입력해 주세요.";

    public static final String NAME_REGEX = "^(?=.*\\S).{1,50}$";
    public static final String NAME_MESSAGE = "이름은 1~50자로 입력해 주세요.";

    private UserRules() {
    }

    /** 01012345678, 010-1234-5678 → 010-1234-5678 (검색·표시·마스킹을 한 형식으로 하려고요) */
    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replace("-", "");
        int middle = digits.length() - 4;
        return digits.substring(0, 3) + "-" + digits.substring(3, middle) + "-" + digits.substring(middle);
    }

    public static String normalizeName(String name) {
        return name == null ? null : name.strip();
    }
}
