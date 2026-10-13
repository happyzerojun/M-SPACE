package com.mspace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 서버가 뜨고(Flyway V1 + ddl-auto validate 통과), 초기 데이터가 들어갔는지 확인해요. */
class BackendApplicationTests extends IntegrationTest {

	@Test
	void 초기_데이터_방_5개와_운영_정책() {
		assertThat(jdbcTemplate.queryForList("SELECT name || ':' || capacity FROM room ORDER BY sort_order", String.class))
				.containsExactly("1번방:4", "2번방:4", "3번방:4", "4번방:4", "5번방:8");
		assertThat(jdbcTemplate.queryForMap("""
				SELECT default_open_minute, default_close_minute, max_duration_minutes, daily_limit,
				       booking_window_days, no_show_grace_minutes, penalty_window_days, penalty_threshold, penalty_days
				FROM operation_policy"""))
				.containsEntry("default_open_minute", 540)
				.containsEntry("default_close_minute", 1080)
				.containsEntry("max_duration_minutes", 120)   // V2: 공식 규정 최대 2시간
				.containsEntry("daily_limit", 1)
				.containsEntry("booking_window_days", 7)
				.containsEntry("no_show_grace_minutes", 15)
				.containsEntry("penalty_window_days", 14)
				.containsEntry("penalty_threshold", 2)
				.containsEntry("penalty_days", 7);
	}

}
