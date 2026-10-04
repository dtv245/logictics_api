package com.company.logicstic;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@Disabled("Requires a live PostgreSQL instance on port 5433 — run manually against a real DB")
class LogicsticApplicationTests {

    @Test
    void contextLoads() {
    }
}
