package com.mspace.global.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mspace.IntegrationTest;
import org.junit.jupiter.api.Test;

/** 배포 이미지 하나로 화면 + API (SpaResourceConfig). 화면 주소는 index.html, 없는 API·파일은 404 */
class SpaResourceTest extends IntegrationTest {

    @Test
    void 화면_주소는_새로고침해도_index_html() throws Exception {
        for (String path : new String[] {"/", "/me/reservations", "/admin/users", "/rooms/1"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("spa-index")));
        }
    }

    @Test
    void 없는_API는_화면이_아니라_JSON_404() throws Exception {
        mvc.perform(get("/api/not-exists"))
                .andExpect(status().isUnauthorized());   // /api/**는 로그인부터 확인해요
        Long admin = insertUser("990900", "ADMIN");
        mvc.perform(get("/api/admin/not-exists").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 없는_파일은_404() throws Exception {
        mvc.perform(get("/assets/missing-abc123.js")).andExpect(status().isNotFound());
    }
}
