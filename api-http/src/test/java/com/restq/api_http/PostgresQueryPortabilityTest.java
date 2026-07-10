package com.restq.api_http;

import com.restq.APIApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Skipped automatically when no Docker daemon is available. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = APIApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "benchmark.type=TPCH",
                "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
        })
class PostgresQueryPortabilityTest extends QueryPortabilityMatrix {

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:14");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
}
