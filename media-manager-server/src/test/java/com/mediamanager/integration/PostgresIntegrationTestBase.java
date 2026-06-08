package com.mediamanager.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class PostgresIntegrationTestBase {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String host = System.getenv().getOrDefault("POSTGRES_HOST", "localhost");
            String port = System.getenv().getOrDefault("POSTGRES_PORT", "5432");
            String db = System.getenv().getOrDefault("POSTGRES_DB", "mediamanager");
            return "jdbc:postgresql://" + host + ":" + port + "/" + db;
        });
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("POSTGRES_USER", "mediamanager"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("POSTGRES_PASSWORD", "mediamanager"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}

