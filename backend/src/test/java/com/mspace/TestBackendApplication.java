package com.mspace;

import org.springframework.boot.SpringApplication;

/** 로컬 DB 없이 Testcontainers PostgreSQL로 서버를 띄워 볼 때 써요. */
public class TestBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(BackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
