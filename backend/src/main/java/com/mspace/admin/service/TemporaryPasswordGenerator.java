package com.mspace.admin.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 관리자가 학생에게 불러 줄 임시 비밀번호 (AU-07). 영문·숫자 10자로, 비밀번호 규칙(영문+숫자)을 항상 만족해요.
 * 말로 불러 줄 때 헷갈리는 글자(0/O, 1/l/I)는 뺐어요.
 */
@Component
public class TemporaryPasswordGenerator {

    private static final String LETTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String ALL = LETTERS + DIGITS;
    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] chars = new char[LENGTH];
        chars[0] = LETTERS.charAt(random.nextInt(LETTERS.length()));
        chars[1] = DIGITS.charAt(random.nextInt(DIGITS.length()));
        for (int i = 2; i < LENGTH; i++) {
            chars[i] = ALL.charAt(random.nextInt(ALL.length()));
        }
        // 앞 두 자리가 늘 영문·숫자 순이지 않게 섞어요
        for (int i = LENGTH - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
