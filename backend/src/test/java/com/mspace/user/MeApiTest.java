package com.mspace.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mspace.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** AU-05 내 정보 수정·비밀번호 변경, AU-07 임시 비밀번호 상태 */
class MeApiTest extends IntegrationTest {

    @Test
    void 본인_정보는_전화번호_마스킹_없이() throws Exception {
        TestUser user = signupAndLogin("99000001");
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("010-0000-0000"));
    }

    @Test
    void 비로그인은_401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void 보낸_값만_바뀌고_학번_역할은_그대로() throws Exception {
        TestUser user = signupAndLogin("99000001");
        mvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"01077778888","studentNo":"11111111","role":"ADMIN"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("010-7777-8888"))
                .andExpect(jsonPath("$.name").value("테스트"))
                .andExpect(jsonPath("$.studentNo").value("99000001"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void 이름을_공백으로_바꾸면_400() throws Exception {
        TestUser user = signupAndLogin("99000001");
        mvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"  "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void 비밀번호를_바꾸면_새_비밀번호로만_로그인() throws Exception {
        TestUser user = signupAndLogin("99000001");
        mvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"currentPassword":"%s","newPassword":"newpass99"}""".formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        login("99000001", "newpass99");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"studentNo":"99000001","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 현재_비밀번호가_틀리거나_같은_비밀번호면_입력칸_에러() throws Exception {
        TestUser user = signupAndLogin("99000001");
        mvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"currentPassword":"wrong12345","newPassword":"newpass99"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));

        mvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"currentPassword":"%s","newPassword":"%s"}""".formatted(PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
    }

    @Test
    void 임시_비밀번호_상태면_내_정보_조회와_비밀번호_변경만_허용() throws Exception {
        TestUser user = signupAndLogin("99000001");
        jdbcTemplate.update("UPDATE users SET must_change_password = TRUE WHERE id = ?", user.id());

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"바꿈"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"currentPassword":"%s","newPassword":"newpass99"}""".formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        // 바꾼 뒤에는 같은 토큰으로 다른 기능도 써요 (상태는 요청마다 DB에서 읽어요)
        mvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"바꿈"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }
}
