package com.mspace.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mspace.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** AU-03 로그인, AU-04 토큰 만료·비활성 계정 */
class LoginApiTest extends IntegrationTest {

    private ResultActions login(String body) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 로그인하면_토큰과_2시간_만료와_회원_정보() throws Exception {
        signupAndLogin("99000001");

        login("""
                {"studentNo":"99000001","password":"%s"}""".formatted(PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andExpect(jsonPath("$.user.studentNo").value("99000001"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"));
    }

    @Test
    void 받은_토큰으로_내_정보를_볼_수_있음() throws Exception {
        TestUser user = signupAndLogin("99000001");
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id()));
    }

    @Test
    void 비밀번호_틀림_없는_학번_비활성_계정은_모두_같은_LOGIN_FAILED() throws Exception {
        signupAndLogin("99000001");
        signupAndLogin("99000002");
        jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE student_no = '99000002'");

        for (String body : new String[]{
                """
                {"studentNo":"99000001","password":"wrong12345"}""",
                """
                {"studentNo":"99999999","password":"%s"}""".formatted(PASSWORD),
                """
                {"studentNo":"99000002","password":"%s"}""".formatted(PASSWORD)}) {
            login(body)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                    .andExpect(jsonPath("$.message").value("학번 또는 비밀번호가 맞지 않아요."));
        }
    }

    @Test
    void 빈_입력은_400() throws Exception {
        login("""
                {"studentNo":"","password":""}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }
}
