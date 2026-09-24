---
description: "Integration testing standards with Testcontainers and Flyway migrations, ensuring isolated, order-independent test suites."
globs: "src/test/java/**"
alwaysApply: true
---

# Testcontainers & Flyway Integration Testing Guidelines

## Core Principles
1. **Test Isolation & Independence**: Every integration test (`*IT.java`) must be independent and never rely on the execution order or side-effects of other test classes.
2. **Real Database with Testcontainers**: Integration tests against persistence logic must run against a containerised database — never an external, pre-configured one — using whichever of these forms fits the Testcontainers version on the classpath: (a) a database-specific container (`PostgreSQLContainer`) when that module still ships separately; (b) `GenericContainer` + `@DynamicPropertySource` **with an explicit readiness wait** (`Wait.forLogMessage(...)`, `Wait.forListeningPort()`); or (c) `spring-boot-testcontainers` with `@ServiceConnection`. Version note — Spring Boot 4.1+ manages Testcontainers 2.0.5, which merged the per-database and JUnit-5 modules into `org.testcontainers:testcontainers`; `org.testcontainers.containers.PostgreSQLContainer` is **not on the classpath** there, so form (b) is the compliant one in that setup. Readiness, not the class name, is what matters: a container that is up but not yet accepting connections makes the first Flyway migration flaky. See `RULE_DECISIONS.md` AD-2.
3. **Automated Flyway Migrations**: The test database container must be automatically migrated using the exact Flyway migration scripts (`classpath:db/migration/tenant`) to ensure schema parity with production.
4. **Dynamic Property Registration**: Use `@DynamicPropertySource` to bind test container ports and connection strings dynamically to Spring Boot configuration.
5. **State Resetting**: Clean up test fixtures or roll back transactions after test execution to prevent database pollution across test cases.
