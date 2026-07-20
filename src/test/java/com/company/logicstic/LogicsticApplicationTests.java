package com.company.logicstic;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Application context smoke test.
 * <p>
 * This test requires a running PostgreSQL instance on port 5433 (see application-test.yml).
 * It is disabled in the default CI build because the DB is an external dependency.
 * To run this test manually, start PostgreSQL and invoke:
 * {@code mvn test -Dtest=LogicsticApplicationTests -Dspring.profiles.active=test}
 * with the database credentials set as environment variables.
 */
@SpringBootTest
@Disabled("Requires a live PostgreSQL instance on port 5433 — run manually against a real DB")
class LogicsticApplicationTests {

    @Test
    void contextLoads() {
    }
}
