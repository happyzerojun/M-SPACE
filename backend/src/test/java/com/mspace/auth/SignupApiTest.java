package com.mspace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mspace.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** AU-01 회원가입 */
class SignupApiTest extends IntegrationTest {

    private ResultActions signup(String studentNo, String name, String phone, String password) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"studentNo":"%s","name":"%s","phone":"%s","password":"%s","privacyAgreed":true}
                """.formatted(studentNo, name, phone, password)));
    }

    @Test
    void 가입하면_201과_학생_정보_비밀번호는_해시로_저장() throws Exception {
        signup("99000001", " 김민수 ", "01012345678", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentNo").value("99000001"))
                .andExpect(jsonPath("$.name").value("김민수"))              // 앞뒤 공백 제거
                .andExpect(jsonPath("$.phone").value("010-1234-5678"))     // 하이픈 정규화
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE student_no = '99000001'", String.class);
        assertThat(hash).isNotEqualTo(PASSWORD).startsWith("$2");   // BCrypt (NF-03)
    }

    @Test
    void 이미_가입된_학번은_409_DUPLICATE_STUDENT_NO() throws Exception {
        signup("99000001", "김민수", "010-1234-5678", PASSWORD).andExpect(status().isCreated());
        signup("99000001", "다른사람", "010-9999-9999", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_STUDENT_NO"));
    }

    @Test
    void 비활성화된_계정의_학번으로_다시_가입_가능_AU08() throws Exception {
        signup("99000001", "남이가입", "010-1234-5678", PASSWORD).andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE student_no = '99000001'");

        signup("99000001", "진짜본인", "010-2222-3333", PASSWORD).andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
            "studentNo, 1234567",          // 7자리 (경계값 아래)
            "studentNo, 12345678901",      // 11자리 (경계값 위)
            "studentNo, 2023abcd",
            "phone, 02-123-4567",
            "phone, 010-12-34",
            "password, abcdefgh",          // 숫자 없음
            "password, 12345678",          // 영문 없음
            "password, abc1234",           // 7자 (경계값 아래)
            "name, '   '",
    })
    void 입력_규칙_위반은_400과_입력칸별_메시지(String field, String value) throws Exception {
        String studentNo = field.equals("studentNo") ? value : "99000001";
        String phone = field.equals("phone") ? value : "010-1234-5678";
        String password = field.equals("password") ? value : PASSWORD;
        String name = field.equals("name") ? value : "김민수";

        signup(studentNo, name, phone, password)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem(field)));
    }

    @Test
    void 경계값_학번_8자리_10자리와_비밀번호_8자는_허용() throws Exception {
        signup("99000001", "김민수", "010-123-4567", "abcdef12").andExpect(status().isCreated());
        signup("9900000002", "이영희", "010-1234-5678", PASSWORD).andExpect(status().isCreated());
    }

    @Test
    void 같은_학번_동시_가입_5건이면_1건만_성공() throws Exception {
        int n = 5;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit((Callable<Integer>) () -> {
                start.await();
                return signup("99000001", "동시", "010-1234-5678", PASSWORD).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get());
        pool.shutdown();

        // 확인과 저장 사이에 끼어든 요청도 500이 아니라 409로 바뀌어야 해요 (DB 제약 → 에러 코드, TS-05)
        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(1);
    }

    @Test
    void 개인정보_수집_이용에_동의하지_않으면_가입할_수_없음() throws Exception {
        for (String consent : new String[] {"", ",\"privacyAgreed\":false"}) {
            mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                            {"studentNo":"99000001","name":"김민수","phone":"010-1234-5678","password":"%s"%s}
                            """.formatted(PASSWORD, consent)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("privacyAgreed")))
                    .andExpect(jsonPath("$.fieldErrors[*].message", hasItem("개인정보 수집 및 이용에 동의해 주세요.")));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
    }

    @Test
    void 동의하면_동의_시각과_처리방침_버전을_남김() throws Exception {
        signup("99000001", "김민수", "010-1234-5678", PASSWORD).andExpect(status().isCreated());
        var row = jdbcTemplate.queryForMap("SELECT privacy_agreed_at, privacy_policy_version FROM users");
        assertThat(row.get("privacy_agreed_at")).isNotNull();
        assertThat(row).containsEntry("privacy_policy_version", "2026-10-21");
    }
}
