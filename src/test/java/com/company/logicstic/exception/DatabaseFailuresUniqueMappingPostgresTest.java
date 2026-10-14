package com.company.logicstic.exception;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.opentest4j.AssertionFailedError;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real tenant migrations: PostgreSQL supplies implicit/truncated names and
 * applies dropped/replaced indexes. pg_index also includes partial unique indexes
 * which information_schema.table_constraints does not list.
 * Requires Docker, like RemediationContextPostgresTest; never silently skips coverage.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DatabaseFailuresUniqueMappingPostgresTest {
    private static final String ALLOWLIST = "/database-failures-unique-allowlist.properties";
    private static PostgreSQLContainer postgres;

    private record UniqueIndex(String table, String name) {
        String description() {
            return table + "." + name;
        }
    }

    @BeforeAll
    static void migrateTenantSchema() {
        postgres = new PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("codex_unique_mapping");
        try {
            postgres.start();
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration/tenant")
                    .load().migrate();
        } catch (RuntimeException | Error failure) {
            postgres.stop();
            throw failure;
        }
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void everyMigratedUniqueIndexHasMappingOrDocumentedException() throws Exception {
        assertCovered(uniqueIndexes(), mappedConstraints(), allowlist().stringPropertyNames());
    }

    @Test
    void allowlistContainsOnlyExistingConstraintsWithReasons() throws Exception {
        var reasons = allowlist();
        assertFalse(reasons.isEmpty(), "Expected explicit exceptions for command/history constraints");
        var existing = new TreeSet<String>();
        uniqueIndexes().forEach(index -> existing.add(index.name()));
        for (String name : reasons.stringPropertyNames()) {
            assertTrue(existing.contains(name), () -> "Obsolete or misspelled allowlist constraint: " + name);
            assertFalse(reasons.getProperty(name).isBlank(), () -> "Missing allowlist reason: " + name);
        }
        assertFalse(reasons.containsKey("ix_trucks_number"), "CRUD number inputs must require a mapping");
    }

    @Test
    void removingMappingNamesTheMissingConstraintAndLeavesProductionIntact() throws Exception {
        // FIELDS is immutable. Mutate only this snapshot, never the shared production map.
        var mappings = mappedConstraints();
        assertTrue(mappings.remove("ix_trucks_number"), "Session A truck mapping must exist");
        var failure = assertThrows(AssertionFailedError.class,
                () -> assertCovered(uniqueIndexes(), mappings, allowlist().stringPropertyNames()));
        assertTrue(failure.getMessage().contains("trucks.ix_trucks_number"), failure::getMessage);
        assertTrue(mappedConstraints().contains("ix_trucks_number"), "Production mapping must remain intact");
    }

    @Test
    void newlyAddedCrudUniqueIndexIsDiscoveredAndFailsWithoutMapping() throws Exception {
        String name = "uq_session_e_trucks_status_probe";
        try (var connection = postgres.createConnection(""); var statement = connection.createStatement()) {
            statement.execute("CREATE UNIQUE INDEX " + name + " ON public.trucks(status)");
            try {
                var failure = assertThrows(AssertionFailedError.class,
                        () -> assertCovered(uniqueIndexes(), mappedConstraints(), allowlist().stringPropertyNames()));
                assertTrue(failure.getMessage().contains("trucks." + name), failure::getMessage);
            } finally {
                statement.execute("DROP INDEX public." + name);
            }
        }
    }

    private static List<UniqueIndex> uniqueIndexes() throws Exception {
        var indexes = new ArrayList<UniqueIndex>();
        try (var connection = postgres.createConnection(""); var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT tables.relname AS table_name, indexes.relname AS constraint_name
                        FROM pg_catalog.pg_index uniqueness
                        JOIN pg_catalog.pg_class indexes ON indexes.oid = uniqueness.indexrelid
                        JOIN pg_catalog.pg_class tables ON tables.oid = uniqueness.indrelid
                        JOIN pg_catalog.pg_namespace schemas ON schemas.oid = tables.relnamespace
                        WHERE schemas.nspname = 'public'
                          AND uniqueness.indisunique AND NOT uniqueness.indisprimary
                          AND tables.relname <> 'flyway_schema_history'
                        ORDER BY tables.relname, indexes.relname
                        """)) {
            while (rows.next()) {
                indexes.add(new UniqueIndex(rows.getString("table_name"), rows.getString("constraint_name")));
            }
        }
        assertFalse(indexes.isEmpty(), "Migration inventory is empty; coverage was not performed");
        return indexes;
    }

    private static Set<String> mappedConstraints() throws ReflectiveOperationException {
        // Read the actual registry without exposing private production details as a public API.
        var field = DatabaseFailures.class.getDeclaredField("FIELDS");
        field.setAccessible(true);
        var mappings = (Map<?, ?>) field.get(null);
        var names = new TreeSet<String>();
        mappings.keySet().forEach(name -> names.add((String) name));
        return names;
    }

    private static Properties allowlist() throws IOException {
        var reasons = new Properties();
        try (var input = DatabaseFailuresUniqueMappingPostgresTest.class.getResourceAsStream(ALLOWLIST)) {
            assertNotNull(input, "Unique mapping allowlist resource is missing: " + ALLOWLIST);
            reasons.load(input);
        }
        return reasons;
    }

    private static void assertCovered(List<UniqueIndex> indexes, Set<String> mappings, Set<String> exceptions) {
        var missing = indexes.stream()
                .filter(index -> !mappings.contains(index.name()) && !exceptions.contains(index.name()))
                .map(UniqueIndex::description).sorted().toList();
        assertTrue(missing.isEmpty(), () -> "Unique constraints missing from DatabaseFailures.FIELDS: "
                + String.join(", ", missing)
                + ". Add the CRUD field mapping, or an exact allowlist entry with a command/history/non-CRUD reason.");
    }
}
