package com.mspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// JWT로 인증해서 Spring Security의 기본 사용자(메모리 계정, 임시 비밀번호)는 쓰지 않아요
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
