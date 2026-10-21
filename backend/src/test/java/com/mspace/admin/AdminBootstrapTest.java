package com.mspace.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mspace.IntegrationTest;
import com.mspace.admin.service.AdminBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/** 첫 배포 때 관리자 계정 만들기 (AU-06, AU-07). 관리자가 없을 때만 한 번, 첫 로그인 뒤 비밀번호 변경 강제 */
class AdminBootstrapTest extends IntegrationTest {

    @Autowired
    private AdminBootstrap bootstrap;

    @Test
    void 관리자가_없으면_임시_비밀번호_상태로_한_번만_만듦() throws Exception {
        assertThat(bootstrap.createIfAbsent("99009999", "bootstrap123", "조교")).isTrue();
        assertThat(bootstrap.createIfAbsent("99009998", "bootstrap123", "조교2")).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE role = 'ADMIN'", Long.class)).isEqualTo(1);

        TestUser admin = login("99009999", "bootstrap123");
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void 값이_없거나_비밀번호가_짧으면_만들지_않음() {
        assertThat(bootstrap.createIfAbsent("", "bootstrap123", "조교")).isFalse();
        assertThat(bootstrap.createIfAbsent("99009999", "short1", "조교")).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
    }
}
