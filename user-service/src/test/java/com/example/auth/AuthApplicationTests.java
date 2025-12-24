package com.example.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        // Use in-memory H2 so tests don't require external Postgres
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        // Disable Redis auto-config to avoid external dependency in tests
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        // Shorten JWT validity in tests (optional)
        "security.jwt.access-minutes=10"
})
class AuthApplicationTests {

    @Test
    void contextLoads() {
        // verifies Spring context boots with test overrides
    }
}

