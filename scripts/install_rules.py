#!/usr/bin/env python3
"""
Install and manage Cursor and Claude rules for LogisticsX project.
Copies rules from fetched repositories and generates custom project rules.
"""

import os
import shutil
from datetime import datetime

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CURSOR_RULES_DIR = os.path.join(ROOT_DIR, ".cursor", "rules")
CLAUDE_RULES_DIR = os.path.join(ROOT_DIR, ".claude", "rules")
TMP_FETCH = "/tmp/rules_fetch"

os.makedirs(CURSOR_RULES_DIR, exist_ok=True)
os.makedirs(CLAUDE_RULES_DIR, exist_ok=True)

installed_records = []
now_str = datetime.now().strftime("%Y-%m-%d")

def copy_rule(src_path, filename, source_name, purpose):
    dest_cursor = os.path.join(CURSOR_RULES_DIR, filename)
    dest_claude = os.path.join(CLAUDE_RULES_DIR, filename.replace(".mdc", ".md"))
    
    shutil.copyfile(src_path, dest_cursor)
    shutil.copyfile(src_path, dest_claude)
    
    installed_records.append({
        "file": filename,
        "source": source_name,
        "purpose": purpose,
        "date": now_str
    })
    print(f"[✓] Installed {filename} from {source_name}")

def write_custom_rule(filename, content, purpose):
    dest_cursor = os.path.join(CURSOR_RULES_DIR, filename)
    dest_claude = os.path.join(CLAUDE_RULES_DIR, filename.replace(".mdc", ".md"))
    
    with open(dest_cursor, "w", encoding="utf-8") as f:
        f.write(content)
    with open(dest_claude, "w", encoding="utf-8") as f:
        f.write(content)
        
    installed_records.append({
        "file": filename,
        "source": "Custom (Project-specific)",
        "purpose": purpose,
        "date": now_str
    })
    print(f"[✓] Created custom rule {filename}")

PROJECT_OVERRIDE_MARKER = "PROJECT OVERRIDE"

# Decision AD-1 in RULE_DECISIONS.md: two clauses of the generic JPA template are void here.
JPA_305_OVERRIDE_HEADER = """> ## ⚠️ PROJECT OVERRIDE — two clauses below are VOID (read before applying anything)
>
> This file is a generic community template (`awesome-cursorrules`), i.e. the **lowest tier** of this
> repository's rule precedence (see `RULES_INSTALLED.md` → *Rule Precedence*). Two of its clauses are
> superseded by `docs/docs/development/engineering-conventions.md`; full rationale in
> `RULE_DECISIONS.md` AD-1:
>
> | Clause in this file | Status | Follow instead |
> | :--- | :--- | :--- |
> | Entities "must annotate entity classes with `@Data`" (§Entities 2) | **VOID** | `@Getter` + `@Setter` + `@NoArgsConstructor`, `BaseAuditableEntity` for audit columns, identifier-based `equals`/`hashCode`. `@Data` generates mutable state, proxy-breaking `equals`/`hashCode` and a `toString` that walks lazy associations. |
> | Service/controller dependencies "must be `@Autowired` without a constructor" (§Service 4, §RestController 4) | **VOID** | Constructor injection: `@RequiredArgsConstructor` or an explicit constructor, dependencies `private final`. |
>
> Everything else in this file (DTOs as records, service/DTO separation, repositories returning DTOs
> for multi-join queries, LAZY relationships, `@EntityGraph` for N+1) **does** apply.
> Do **not** report the two voided clauses as findings or deviations.
## Instruction to developer: save this file as .cursorrules and place it on the root project directory
"""

JPA_305_OVERRIDES = [
    (
        "\n## Instruction to developer: save this file as .cursorrules and place it on the root project directory\n",
        "\n" + JPA_305_OVERRIDE_HEADER,
    ),
    (
        "2. Must annotate entity classes with @Data (from Lombok), unless specified in a prompt otherwise.",
        "2. ~~Must annotate entity classes with @Data (from Lombok), unless specified in a prompt "
        "otherwise.~~ — **VOID in this project** (see PROJECT OVERRIDE above / `RULE_DECISIONS.md` "
        "AD-1): use `@Getter` + `@Setter` + `@NoArgsConstructor` and let `BaseAuditableEntity` own "
        "the audit columns. Adding `@Data` to an `@Entity` is a finding, not a requirement.",
    ),
    (
        "4. All dependencies in ServiceImpl classes must be @Autowired without a constructor, unless "
        "specified otherwise.",
        "4. ~~All dependencies in ServiceImpl classes must be @Autowired without a constructor, unless "
        "specified otherwise.~~ — **VOID in this project** (`RULE_DECISIONS.md` AD-1): constructor "
        "injection (`@RequiredArgsConstructor` or an explicit constructor, dependencies "
        "`private final`), enforced as a finding by `scan_codebase.py`.",
    ),
    (
        "4. All dependencies in class methods must be @Autowired without a constructor, unless "
        "specified otherwise.",
        "4. ~~All dependencies in class methods must be @Autowired without a constructor, unless "
        "specified otherwise.~~ — **VOID in this project** (`RULE_DECISIONS.md` AD-1): controllers "
        "take their collaborators through the constructor.",
    ),
]


def apply_project_overrides(filename, overrides):
    """Re-applies the decisions recorded in RULE_DECISIONS.md to an upstream rule file.

    Upstream rules are copied verbatim, so a re-install would otherwise silently resurrect the clauses
    the project has already overruled. Idempotent: a file that already carries the marker is skipped.
    """
    for dest in (
        os.path.join(CURSOR_RULES_DIR, filename),
        os.path.join(CLAUDE_RULES_DIR, filename.replace(".mdc", ".md")),
    ):
        if not os.path.exists(dest):
            continue
        with open(dest, "r", encoding="utf-8") as f:
            content = f.read()
        if PROJECT_OVERRIDE_MARKER in content:
            print(f"[=] Overrides already present in {os.path.basename(dest)}")
            continue
        for old, new in overrides:
            content = content.replace(old, new, 1)
        with open(dest, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"[✓] Applied project overrides to {os.path.basename(dest)}")

# 1. Java Rules from chubenli/cursor-rules-java & jabrena/cursor-rules-java
java_rules = [
    ("110-java-maven-best-practices.mdc", "chubenli/cursor-rules-java", "Maven build configuration and best practices"),
    ("111-java-maven-deps-and-plugins.mdc", "chubenli/cursor-rules-java", "Maven dependency & plugin management rules"),
    ("112-java-maven-documentation.mdc", "chubenli/cursor-rules-java", "Maven project documentation conventions"),
    ("121-java-object-oriented-design.mdc", "chubenli/cursor-rules-java", "Object-oriented design patterns & SOLID principles"),
    ("122-java-type-design.mdc", "chubenli/cursor-rules-java", "Java type design, records, immutability & enums"),
    ("123-java-general-guidelines.mdc", "chubenli/cursor-rules-java", "General Java clean code conventions"),
    ("124-java-secure-coding.mdc", "chubenli/cursor-rules-java", "Secure coding standards & vulnerability prevention"),
    ("125-java-concurrency.mdc", "chubenli/cursor-rules-java", "Java concurrency, multithreading and thread-safety"),
    ("126-java-logging.mdc", "chubenli/cursor-rules-java", "SLF4J / Logback logging standards and sensitive data masking"),
    ("131-java-unit-testing.mdc", "chubenli/cursor-rules-java", "JUnit 5, Mockito & AssertJ unit testing rules"),
    ("141-java-refactoring-with-modern-features.mdc", "chubenli/cursor-rules-java", "Modern Java 17/21 refactoring guidelines"),
    ("142-java-functional-programming.mdc", "chubenli/cursor-rules-java", "Functional programming with Streams & Optional"),
    ("143-java-data-oriented-programming.mdc", "chubenli/cursor-rules-java", "Data-oriented programming (Records, Sealed Types, Pattern Matching)"),
    ("151-java-profiling-detect.mdc", "chubenli/cursor-rules-java", "Performance profiling & memory leak detection (JFR)"),
    ("152-java-profiling-analyze.mdc", "chubenli/cursor-rules-java", "JFR analysis & bottleneck identification"),
    ("154-java-profiling-compare.mdc", "chubenli/cursor-rules-java", "Performance benchmark comparison standards")
]

for filename, source, purpose in java_rules:
    src_file = os.path.join(TMP_FETCH, "chubenli_java", ".cursor", "rules", filename)
    if os.path.exists(src_file):
        copy_rule(src_file, filename, source, purpose)

# 2. Spring Boot Rules from jabrena/cursor-rules-spring-boot
spring_rules = [
    ("301-frameworks-spring-boot-core.mdc", "jabrena/cursor-rules-spring-boot", "Spring Boot core architecture, DI, configuration and beans"),
    ("302-frameworks-spring-boot-rest.mdc", "jabrena/cursor-rules-spring-boot", "RESTful API design, controller standards & error handling"),
    ("303-frameworks-spring-data-jdbc.mdc", "jabrena/cursor-rules-spring-boot", "Spring Data persistence and repository patterns"),
    ("304-frameworks-spring-boot-hikari.mdc", "jabrena/cursor-rules-spring-boot", "HikariCP connection pool configuration & tuning"),
    ("311-frameworks-spring-boot-slice-testing.mdc", "jabrena/cursor-rules-spring-boot", "Spring Boot slice testing (@WebMvcTest, @DataJpaTest)"),
    ("312-frameworks-spring-boot-integration-testing.mdc", "jabrena/cursor-rules-spring-boot", "Integration testing with Spring Boot test context"),
    ("313-frameworks-spring-boot-local-testing.mdc", "jabrena/cursor-rules-spring-boot", "Local dev environment test execution"),
    ("321-frameworks-spring-boot-native-compilation.mdc", "jabrena/cursor-rules-spring-boot", "GraalVM native compilation rules"),
    ("500-sql.mdc", "jabrena/cursor-rules-spring-boot", "SQL query standards, indexing and syntax best practices")
]

for filename, source, purpose in spring_rules:
    src_file = os.path.join(TMP_FETCH, "jabrena_spring", ".cursor", "rules", filename)
    if os.path.exists(src_file):
        copy_rule(src_file, filename, source, purpose)

# 3. Awesome Cursorrules: Java Spring Boot JPA
awesome_src = os.path.join(TMP_FETCH, "awesome_java_springboot_jpa.mdc")
if os.path.exists(awesome_src):
    copy_rule(awesome_src, "305-frameworks-spring-boot-jpa.mdc", "PatrickJS/awesome-cursorrules", "Spring Boot JPA entity mapping, DTO separation & lazy fetching")
    # RULE_DECISIONS.md AD-1: this template's @Data-on-entity and field-injection clauses are void here.
    apply_project_overrides("305-frameworks-spring-boot-jpa.mdc", JPA_305_OVERRIDES)

# 4. Phase 2: Custom Project Rules

rule_multi_tenancy = """---
description: "Multi-tenancy guidelines enforcing database isolation, tenant context resolution, and preventing cross-tenant leakage."
globs: "src/main/java/com/company/logicstic/**"
alwaysApply: true
---

# Multi-Tenancy Coding Guidelines (Database Isolation)

## Core Principles
1. **Tenant Context Resolution**: All database queries, repository calls, and cache operations must execute strictly within the active tenant context resolved via `TenantRoutingDataSource` and `TenantContextHolder`.
2. **No Hard-coded Tenant IDs**: Never hard-code tenant identifiers or bypass tenant routing in production business code.
3. **Cross-Tenant Access Forbidden**: Cross-tenant data access, queries without tenant boundaries, or shared database connections across different tenant requests are strictly prohibited.
4. **Tenant Lifecycle Management**: New tenant workspaces must be provisioned and migrated through `TenantProvisioningService` and `TenantMigrationService` using official Flyway tenant scripts (`classpath:db/migration/tenant`).
5. **Async & Background Context Propagation**: When spawning asynchronous threads, background jobs, or scheduled tasks, the tenant context MUST be explicitly passed and cleared in a `finally` block:
   ```java
   try {
       TenantContextHolder.setTenantId(targetTenantId);
       // execute tenant-bound task
   } finally {
       TenantContextHolder.clear();
   }
   ```
6. **Graceful Fallback**: If tenancy is disabled (`app.tenancy.enabled=false`), services must operate cleanly on the default standalone datasource.
"""

rule_redis_caching = """---
description: "Redis caching rules for multi-tenant logistics platform, key prefix formatting, TTL definitions, and cache eviction."
globs: "src/main/java/com/company/logicstic/cache/**,src/main/resources/*.yml"
alwaysApply: true
---

# Redis Caching & Invalidation Guidelines

## Core Principles
1. **Multi-Tenant Key Prefix**: Cache keys MUST always follow the structure:
   `logistics:{tenant}:{cache}:{id}`
   This is enforced by `TenantAwareCacheKeyPrefix` to guarantee complete tenant cache isolation.
2. **Explicit Entity TTLs**: Every cache must define an explicit Time-To-Live (TTL) suited for its update frequency:
   - `terminal`: 24h (prod) / 30m (dev) / 2m (local)
   - `role`: 6h (prod) / 15m (dev) / 2m (local)
   - `customer`: 1h (prod) / 10m (dev) / 1m (local)
   - `employee`: 30m (prod) / 5m (dev) / 1m (local)
   - `truck`: 30m (prod) / 5m (dev) / 1m (local)
3. **Write-Time Eviction**: Any mutating operation (create, update, delete, state change) in a service MUST explicitly evict the affected cache entries using `@CacheEvict` or `CacheManager`.
4. **No Sensitive / Unencrypted Data**: Never cache unencrypted passwords, secrets, credit card numbers, or sensitive PII.
5. **Fail-Open Resilience**: Cache errors (e.g. Redis timeout or downtime) must be handled by `CacheErrorHandler` and gracefully fall back to the primary PostgreSQL database without disrupting user requests.
"""

rule_testcontainers_flyway = """---
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
"""

rule_security_oauth2 = """---
description: "OAuth2 Resource Server & JWT security standards, claims validation, and sensitive data protection."
globs: "src/main/java/com/company/logicstic/security/**,src/main/java/com/company/logicstic/identity/**"
alwaysApply: true
---

# OAuth2 Resource Server & JWT Security Guidelines

## Core Principles
1. **Strict Token Verification**: OAuth2 Resource Server must validate JWT signatures, issuer (`iss`), audience (`aud`), and expiration (`exp`) against the configured Identity Provider JWKS endpoint.
2. **Tenant Claim Verification**: Incoming JWT tokens must contain valid tenant claims, and the `TenantJwtClaimFilter` must ensure the authenticated principal has permission to access the requested tenant context.
3. **No Sensitive Logging**: NEVER log raw JWT access tokens, refresh tokens, Authorization headers, or passwords.
4. **Security Filter Chain Whitelist**:
   - Protected: All business domain endpoints (`/api/**`) require valid authentication and appropriate role claims.
   - Public: Only health checks (`/actuator/health`), OpenAPI docs (`/swagger-ui/**`, `/v3/api-docs/**`), and local dev-auth endpoints (when explicitly enabled) may be public (`permitAll`).
5. **Principal-Bound Operations**: Sensitive domain operations (e.g. status confirmations, messaging, payments) must bind the actor to the authenticated employee ID extracted from the security principal rather than trusting client-supplied ID parameters.
"""

write_custom_rule("multi-tenancy.mdc", rule_multi_tenancy, "Rules for TenantRoutingDataSource, TenantRegistry, TenantProvisioning, and tenant isolation")
write_custom_rule("redis-caching.mdc", rule_redis_caching, "Rules for Redis key prefixes (logistics:{tenant}:{cache}:{id}), TTLs, and cache eviction")
write_custom_rule("testcontainers-flyway.mdc", rule_testcontainers_flyway, "Rules for Testcontainers + Flyway integration tests and test isolation")
write_custom_rule("security-oauth2.mdc", rule_security_oauth2, "Rules for OAuth2 JWT resource server, token validation, and logging prevention")

# 5. Generate RULES_INSTALLED.md
report_lines = [
    "# Coding Agent Rules Installed",
    "",
    f"**Installation Date:** {now_str}  ",
    f"**Total Rules Installed:** {len(installed_records)}  ",
    f"**Target Folders:** `.cursor/rules/` and `.claude/rules/`  ",
    "",
    "## Rule Precedence (how conflicts are resolved)",
    "",
    "When two rules disagree, the **first** tier that covers the question wins. Do not \"average\" them,",
    "and do not follow a lower tier silently: if the lower-tier clause contradicts a higher tier, treat",
    "it as overridden and say so in one line (this is what the `PROJECT OVERRIDE` banner in a rule file",
    "is for).",
    "",
    "1. **`docs/docs/development/engineering-conventions.md`** — this project's engineering conventions.",
    "   Absolute source of truth for architecture, layering, persistence, cache, tenancy, testing and",
    "   release process.",
    "2. **Project custom rules** — `multi-tenancy.mdc`, `redis-caching.mdc`, `testcontainers-flyway.mdc`,",
    "   `security-oauth2.mdc`. Written for this codebase; they know about `TenantRoutingDataSource`,",
    "   `TenantAwareCacheKeyPrefix`, the container versions in use, and the JWT claim set.",
    "3. **Actively maintained upstream packs** — `jabrena/cursor-rules-spring-boot` (301-321, 500-sql)",
    "   and `chubenli/cursor-rules-java` (110-154). Current, reviewed, aligned with Spring Boot 4 / Java 21.",
    "4. **`awesome-cursorrules` generic templates** — lowest priority, most likely to be outdated (e.g.",
    "   `305-frameworks-spring-boot-jpa.mdc`, written for Spring Boot 3 / Java 17 with field injection and",
    "   Lombok `@Data` on entities). Follow only where tiers 1-3 are silent.",
    "",
    "Decisions where a rule was overruled, with rationale, are recorded in **`RULE_DECISIONS.md`**.",
    "That file is machine-readable: `scripts/scan_codebase.py` reads its `accepted-deviation` markers and",
    "excludes those items from `RULES_SCAN_REPORT.md` instead of re-reporting them as findings.",
    "",
    "## Installed Rule Manifest",
    "",
    "| No. | Rule File | Source | Purpose |",
    "| :---: | :--- | :--- | :--- |"
]

for idx, rec in enumerate(installed_records, 1):
    report_lines.append(f"| {idx} | `{rec['file']}` | **{rec['source']}** | {rec['purpose']} |")

report_lines.extend([
    "",
    "## Rule Categories",
    "1. **Java Clean Code & Architecture:** General guidelines, type design, OOP, concurrency, logging, unit testing, DOP/FP, modern refactoring, profiling (JFR).",
    "2. **Maven Build & Dependencies:** Best practices, dependency/plugin management, documentation.",
    "3. **Spring Boot & REST API:** Core architecture, REST design, Spring Data JDBC/JPA, HikariCP, slice & integration testing.",
    "4. **Multi-Tenancy:** Database-isolated multi-tenant routing, provisioning, async context propagation.",
    "5. **Redis Caching:** Multi-tenant cache key prefixing, entity-specific TTLs, write eviction, resilience.",
    "6. **Testing with Testcontainers & Flyway:** Isolated containerized testing, schema migration validation.",
    "7. **Security & OAuth2:** JWT verification, tenant claim validation, credentials protection, principal-bound mutations.",
    ""
])

with open(os.path.join(ROOT_DIR, "RULES_INSTALLED.md"), "w", encoding="utf-8") as f:
    f.write("\n".join(report_lines) + "\n")

print(f"\n[✓] RULES_INSTALLED.md successfully generated with {len(installed_records)} rules.")
