package com.mspace.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** @CreatedDate, @LastModifiedDate 자동 기록을 켜요. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
