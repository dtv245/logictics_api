package com.company.logicstic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Application context smoke test.
 *
 * <p>The no-database profile verifies the web/application configuration without requiring an
 * external PostgreSQL process. Repository HQL is validated separately by enabled metadata tests.
 */
@SpringBootTest
@ActiveProfiles("nodb")
class LogicsticApplicationTest {

  @Test
  void contextLoads() {}
}
