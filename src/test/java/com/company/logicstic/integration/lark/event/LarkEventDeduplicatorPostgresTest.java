package com.company.logicstic.integration.lark.event;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TASK_DB_URL", matches = "jdbc:postgresql:.*codex_.*")
class LarkEventDeduplicatorPostgresTest {
  @Test void realPostgresMigrationAndConcurrentClaimsAreAtomic() throws Exception {
    String url = System.getenv("TASK_DB_URL");
    if (!url.matches("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):\\d+/codex_[a-z0-9_]+"))
      throw new IllegalStateException("Local disposable test database required");
    var source = new PGSimpleDataSource();
    source.setURL(url);
    source.setUser(System.getenv("TASK_DB_USER"));
    source.setPassword(System.getenv("TASK_DB_PASSWORD"));
    String schema = "lark_events_" + UUID.randomUUID().toString().replace("-", "");
    new JdbcTemplate(source).execute("CREATE SCHEMA " + schema);
    source.setCurrentSchema(schema);
    new ResourceDatabasePopulator(new ClassPathResource(
        "db/migration/tenant/V41__create_lark_event_receipts.sql"), new ClassPathResource(
        "db/migration/tenant/V42__persist_lark_event_inbox.sql")).execute(source);
    var one = new LarkEventDeduplicator(source);
    var two = new LarkEventDeduplicator(source);
    var calls = new ArrayList<Callable<Optional<LarkEventDeduplicator.Claim>>>();
    for (int i = 0; i < 16; i++) {
      var instance = i % 2 == 0 ? one : two;
      calls.add(() -> instance.claim("same-event"));
    }
    try (var pool = Executors.newFixedThreadPool(4)) {
      int claimed = 0;
      LarkEventDeduplicator.Claim owner = null;
      for (var future : pool.invokeAll(calls)) {
        var result = future.get();
        if (result.isPresent()) { claimed++; owner = result.get(); }
      }
      assertEquals(1, claimed);
      assertNotNull(owner);
      assertEquals(1, new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM lark_event_receipts", Integer.class));
      one.release(owner);
      assertTrue(two.claim("same-event").isPresent());
    }
  }
}
