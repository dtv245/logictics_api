# LogisticsX API — Engineering Conventions

Authoritative coding, structure and test conventions for the Spring Boot backend
(`com.company.logicstic`). Every rule below is enforceable in code review; violations must be
either fixed or waived explicitly in the PR description.

**Stack of record**

| Concern      | Choice                                                              |
| ------------ | ------------------------------------------------------------------- |
| Language     | Java 21 (`<java.version>21</java.version>`)                         |
| Framework    | Spring Boot **4.1.0** (not 3.x — see [§12](#12-spring-boot-4-notes)) |
| Build        | Maven Wrapper (`./mvnw`, `pom.xml`)                                |
| Persistence  | Spring Data JPA + Hibernate 7, PostgreSQL 18                        |
| Migration    | Flyway (`db/migration/tenant`, `db/migration/registry`)             |
| Security     | Spring Security OAuth2 Resource Server (JWT, external Identity Server) |
| Mapping      | MapStruct 1.5.5                                                     |
| Multi-tenancy| `AbstractRoutingDataSource` (database-per-tenant)                   |
| Test         | JUnit 5, Mockito, AssertJ, `spring-security-test`, `spring-boot-starter-webmvc-test` |
| Style gate   | Spotless (google-java-format), Checkstyle, SpotBugs                 |

---

## Table of contents

1. [Package structure](#1-package-structure)
2. [Dependency rules](#2-dependency-rules)
3. [Naming](#3-naming)
4. [DTO, entity and mapping](#4-dto-entity-and-mapping)
5. [Lombok](#5-lombok)
6. [Dependency injection](#6-dependency-injection)
7. [Transactions](#7-transactions)
8. [Exceptions and error handling](#8-exceptions-and-error-handling)
9. [REST API contract](#9-rest-api-contract)
10. [Persistence](#10-persistence)
11. [Configuration and secrets](#11-configuration-and-secrets)
12. [Spring Boot 4 notes](#12-spring-boot-4-notes)
13. [Logging](#13-logging)
14. [Multi-tenancy](#14-multi-tenancy)
15. [Test conventions](#15-test-conventions)
16. [Build gates](#16-build-gates)
17. [Code review checklist](#17-code-review-checklist)

---

## 1. Package structure

Target layout is **package-by-feature**. A feature package is self-contained; `common/` holds only
what at least two features genuinely share.

```text
com.company.logicstic
├── LogisticsApplication.java
├── config/                     # @Configuration only: Jackson, OpenAPI, Async, Cache, CORS, JpaAuditing
├── common/
│   ├── exception/              # ApiException + typed subclasses, GlobalExceptionHandler
│   ├── dto/                    # ApiResponse<T>, PageResponse<T>
│   ├── util/
│   ├── validation/             # custom constraint annotations + validators
│   └── audit/                  # BaseEntity (createdAt, createdBy, updatedAt, updatedBy)
├── security/                   # SecurityConfiguration, JwtAuthenticationConverter, UserPrincipal
├── tenancy/                    # tenant resolution, routing datasource, provisioning, migration
└── <feature>/                  # load, trip, truck, customer, employee, invoice, payment, …
    ├── controller/
    ├── service/                # public interface + impl/
    ├── repository/
    ├── entity/
    ├── dto/
    │   ├── request/
    │   └── response/
    ├── mapper/
    └── enums/
```

Rules:

- One bounded context per feature package. Use the six contexts already documented in
  `docs/docs/architecture/module-layout.md` — `operations`, `compliance`, `financial`,
  `identityaccess`, `integrations`, `platform` — as the grouping when a feature clearly belongs to
  one of them. Do not invent a new top-level package for a single entity.
- An entity lives in the feature that **owns its lifecycle**, not in the feature that happens to
  reference it. A `TripStop` belongs to `trip`, a `DvirReport` to `compliance`, an `Expense` to
  `financial`.
- No empty packages. Delete leftovers (e.g. an `entity/` directory kept after a refactor).
- Reference implementation of the full target layout:
  `src/main/java/com/company/logicstic/modules/terminal/` — real, compiled, tested code, not a
  template. Copy its shape when adding a feature.

✅ **Do**

```text
financial/expense/{controller,service,repository,entity,dto,mapper,enums}
```

❌ **Don't**

```text
fleet/entity/Expense.java          # expense lifecycle is financial, not fleet
load/entity/TripStop.java          # stop is part of the trip aggregate
document/entity/AccidentReport.java # accident is compliance; document only stores files
```

---

## 2. Dependency rules

Allowed direction, no exceptions:

```text
controller → service → repository → entity
```

| Rule                                                            | Enforcement                                    |
| --------------------------------------------------------------- | ---------------------------------------------- |
| Controller must not import any `*Repository`                     | review + ArchUnit test (see [§15.6](#156-architecture-tests)) |
| Service must not import any `*Controller`                        | review + ArchUnit                              |
| Feature A must not import `B.repository.*`                       | review + ArchUnit                              |
| Feature A must not construct/mutate `B`'s entity                 | review                                         |
| Cross-feature reads/writes go through B's **public service interface** | review                                    |

Cross-feature JPA associations (`Load.customer`, `Trip.truck`) are allowed — the schema is one
relational database and the associations mirror it. What is **not** allowed is reaching into another
feature's repository or writing another feature's aggregate.

✅ **Do**

```java
// load/service/impl/LoadServiceImpl.java
private final CustomerService customerService;   // public interface of the customer feature

Customer customer = customerService.getEntityById(request.customerId());
```

❌ **Don't**

```java
// load/service/impl/LoadServiceImpl.java
private final CustomerRepository customerRepository;   // reaches into another feature's data layer
private final InvoiceRepository invoiceRepository;     // and writes another aggregate
```

When feature A needs an entity of feature B for an association, B exposes a narrow lookup on its
service interface (`getEntityById`, `getReference`) and owns the "not found" error. That keeps the
404 message, the tenant scoping and the fetch strategy in one place.

Circular package dependencies are a build failure, not a style issue. Two packages must never
import each other.

---

## 3. Naming

| Element         | Convention              | Example                       |
| --------------- | ----------------------- | ----------------------------- |
| Class           | `PascalCase`            | `LoadService`                 |
| Method, field   | `camelCase`             | `dispatchLoad`                |
| Constant        | `UPPER_SNAKE_CASE`      | `MAX_PAGE_SIZE`               |
| Package         | lowercase, single word  | `loadboard` (not `load_board`)|
| Generic type    | single uppercase letter | `<T>`, `<E, V, R>`            |

Mandatory suffixes: `*Controller`, `*Service` (interface), `*ServiceImpl`, `*Repository`,
`*Request`, `*Response`, `*Mapper`, `*Exception`, `*Config` / `*Configuration`, `*Properties`.

- Test classes end with `Test` (unit/slice) or `IT` (integration). Never `Tests`.
- Do not abbreviate domain words: `Employee`, not `Emp`; `Container`, not `Cntr`.
- Boolean getters read as predicates: `isOverdue()`, `hasDriver()`.
- Do not use the existing spelling mistake as a precedent. New code uses correct English
  (`logistics`, not `logicstic`); the package rename is tracked separately as a mechanical refactor.

✅ `TerminalResponse`, `CreateTerminalRequest`, `TerminalServiceImpl`
❌ `TerminalDto`, `TerminalVO`, `TerminalView`, `TerminalSvc`, `TerminalServiceTests`

---

## 4. DTO, entity and mapping

- A controller **never** accepts or returns a JPA entity. Request bodies are `*Request` records,
  responses are `*Response` records.
- `dto/request/` and `dto/response/` are separate packages. A request type is never reused as a
  response type and vice versa.
- Never reuse one request record for both create and update when the validation differs. Prefer
  `CreateXRequest` / `UpdateXRequest`.
- Use Java `record` for DTOs — immutable, no Lombok needed, works with Bean Validation.
- All mapping is MapStruct. Hand-written mappers, `BeanUtils.copyProperties` and manual
  `new Response(...)` chains inside services are not allowed.
- Mappers must not resolve foreign keys. Ignore association targets in the mapper and resolve them
  in the service, where the "not found" error belongs.

✅ **Do**

```java
public record CreateTerminalRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Pattern(regexp = "^[A-Z]{5}$") String code,
    @NotNull TerminalType type) {}

@Mapper(config = MapperConfiguration.class)
public interface TerminalMapper {
  @Mapping(target = "id", ignore = true)
  Terminal toEntity(CreateTerminalRequest request);

  TerminalResponse toResponse(Terminal terminal);
}
```

❌ **Don't**

```java
@PostMapping
public Terminal create(@RequestBody Terminal terminal) { ... }   // entity in and out

TerminalResponse response = new TerminalResponse(t.getId(), t.getName(), ...); // manual mapping
```

---

## 5. Lombok

Allowed: `@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@NoArgsConstructor`,
`@Slf4j`.

Forbidden:

| Annotation                          | Why                                                                 |
| ----------------------------------- | ------------------------------------------------------------------- |
| `@Data` on a JPA entity             | Generates `equals`/`hashCode`/`toString` over all fields, triggering lazy loads and breaking JPA identity semantics |
| `@EqualsAndHashCode` (default) on entity | Compares mutable/lazy fields; JPA identity must be based on the ID only |
| `@ToString` on entity               | Triggers lazy loads inside logs, can leak PII                        |
| `@AllArgsConstructor` on entity     | Positional constructor silently breaks when a column is added        |
| `@Value` (Lombok)                   | Collides visually with Spring's `@Value`; use `record`               |

For DTOs prefer `record` over any Lombok combination.

If an entity needs equality, implement it explicitly on the identifier and nothing else.

✅ **Do**

```java
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Terminal extends BaseEntity { ... }
```

❌ **Don't**

```java
@Entity
@Data              // forbidden on entity
@Builder
public class Terminal { ... }
```

---

## 6. Dependency injection

- Constructor injection only. Prefer `@RequiredArgsConstructor` with `private final` fields.
- `@Autowired` on a field is forbidden in production code **and in tests** (use constructor
  injection or the test-slice injection points).
- No setter injection, no field injection, no `ApplicationContext.getBean(...)` lookups.
- Never inject a concrete `*ServiceImpl`; inject the interface.

✅ **Do**

```java
@Service
@RequiredArgsConstructor
public class TerminalServiceImpl implements TerminalService {
  private final TerminalRepository terminalRepository;
  private final TerminalMapper terminalMapper;
}
```

❌ **Don't**

```java
@Service
public class TerminalServiceImpl {
  @Autowired private TerminalRepository terminalRepository;   // field injection
}
```

---

## 7. Transactions

- `@Transactional` belongs on the **service implementation**. Never on a controller, never on a
  repository interface or repository method.
- Class-level `@Transactional(readOnly = true)`, method-level `@Transactional` on writes.
- Use `org.springframework.transaction.annotation.Transactional` (not the Jakarta one).
- Keep external I/O (HTTP calls, blob upload, e-mail, push) **outside** the transaction, or make it
  idempotent and compensating. A remote timeout must not hold a database transaction open.
- Publish domain events so listeners run after commit
  (`@TransactionalEventListener(phase = AFTER_COMMIT)`); a notification must never be sent for a
  rolled-back change.

**Self-invocation trap.** Spring proxies the bean, so calling a `@Transactional` method from another
method of the *same* class bypasses the proxy and the annotation has no effect:

```java
public void importAll(List<Row> rows) {
  rows.forEach(this::importOne);   // ❌ @Transactional on importOne is ignored
}

@Transactional
public void importOne(Row row) { ... }
```

Fix by moving the inner method to a separate bean, or by injecting the batch entry point and making
the *outer* method transactional when a single unit of work is intended.

`@Modifying` bulk updates need a transaction from the caller; declare it in the service, not on the
repository method:

❌ `NotificationRepository.markAllAsRead()` annotated with `@Transactional`
✅ `NotificationServiceImpl.markAllAsRead()` annotated with `@Transactional`, repository declares
only `@Modifying @Query`.

---

## 8. Exceptions and error handling

- Exactly one `@RestControllerAdvice` for the whole application.
- Business rule violations throw one of the typed `ApiException` subclasses in
  `shared/exception/` — `ResourceNotFoundException` (404), `ConflictException` (409),
  `BadRequestException` (400), `InvalidStateTransitionException` (400). Each carries
  `(httpStatus, code, message)` and the advice maps it straight to the envelope. Services never build
  a `ResponseEntity` for errors.
- A new failure mode either reuses one of those types with a specific `code`
  (`new ConflictException("TERMINAL_IN_USE", "…")`) or adds a subclass — it never invents an
  ad-hoc string at the throw site, and never throws raw `RuntimeException`.
- Never swallow an exception. `catch` is allowed only to (a) translate, (b) compensate and rethrow,
  or (c) close a resource on a best-effort basis — and (c) must log at `debug`/`warn` with the
  cause.
- Forbidden: `e.printStackTrace()`, empty `catch {}`, `catch (Exception e) { return null; }`,
  throwing raw `RuntimeException`/`IllegalStateException` for business errors.
- Validation failures return a field-level error list; do not collapse them into one string.
- Error responses must not leak stack traces, SQL, provider payloads or tenant identifiers. Log the
  detail server-side with a correlation ID and return the ID to the client.

✅ **Do**

```java
throw new ConflictException("Terminal with code '" + code + "' already exists");
throw new ConflictException("TERMINAL_IN_USE", "Terminal '" + code + "' is referenced by loads");
throw new ResourceNotFoundException("Terminal not found: " + id);
```

❌ **Don't**

```java
try {
  ...
} catch (Exception e) {
  e.printStackTrace();          // forbidden
  return null;                  // hides the failure
}
```

Reference: `shared/exception/GlobalExceptionHandler.java` (the single advice) and
`modules/terminal/service/impl/TerminalServiceImpl.java` (throw sites, including translating a
`DataIntegrityViolationException` into a domain conflict instead of leaking the constraint name).

---

## 9. REST API contract

- Resources are plural nouns, kebab-case when multi-word: `/api/loads`, `/api/tracking-links`.
- Sub-resources nest one level: `/api/loads/{loadId}/documents`.
- State transitions are `POST /{id}/<verb>`: `/api/loads/{id}/dispatch`, `/api/loads/{id}/cancel`.
  Do not encode a transition as a `PUT` of the whole resource with a new status — the status field
  in an update request must equal the persisted status.
- `@Valid` on every request body; `@Validated` on the controller for query-parameter constraints.
- Pagination is 1-based `page` + `pageSize`, bounded by `Constants.MAX_PAGE_SIZE`, sorted via
  `orderBy` + `descending`. Controllers pass them through; services build the `Pageable`.
- Every response is wrapped in `ApiResponse<T>`; list endpoints wrap `PagedResponse<T>`.
- Status codes: `200` read/update, `201` create, `204` delete-with-no-body, `400` validation,
  `401` unauthenticated, `403` unauthorised, `404` not found in tenant scope, `409` duplicate/state
  conflict, `422` business rule violation, `503` provider unavailable.
- **Versioning.** The whole surface is currently unversioned (`/api/loads`, `/api/trips`, …) and is
  consumed by the Angular portals and the driver app, so existing paths and payloads are frozen. New
  endpoints follow the existing unversioned shape (`/api/terminals`) — consistency beats a half-
  migrated `/api/v1` prefix. Introducing versioning is a deliberate, one-time decision that needs an
  ADR and a plan for the generated Angular client, not a per-feature choice.
- **Response envelope is contract.** `docs/docs/api/overview.md` documents
  `{isSuccess, data, error}` while the implementation emits
  `{success, code, message, data, errors, meta}`, and
  `docs/docs/migration/migration-plan-dotnet-to-springboot.md:157` requires snake_case JSON while
  the application serialises camelCase. This divergence must be resolved by an explicit product
  decision before the frontends are pointed at this backend; until then, do not "fix" one side
  unilaterally, and do not add new fields to the envelope.

✅ `POST /api/loads/{id}/dispatch` → `200 ApiResponse<LoadResponse>`
❌ `POST /api/load/dispatchLoad?id=…` → raw entity

---

## 10. Persistence

- `FetchType.LAZY` on every `@ManyToOne` and `@OneToOne`. `EAGER` is forbidden.
- Solve N+1 with `@EntityGraph` on the repository method or an explicit `join fetch`; never by
  switching to `EAGER` and never by touching associations inside a loop.
- `open-in-view` stays `false`. Anything the response needs must be fetched or mapped inside the
  service transaction.
- Schema changes are Flyway migrations, forward-only, immutable once merged. `ddl-auto` is
  `validate` in every environment (`none` in production); `update` and `create-drop` are forbidden.
- The tenant schema mirrors the legacy .NET database. Column names, enum string values, sequences
  and JSON columns are contract — see
  `docs/docs/migration/migration-plan-dotnet-to-springboot.md:161`. A migration that renames or
  drops a legacy column needs sign-off.
- Status columns are `text` in the schema. Represent them as a dedicated enum in `<feature>/enums/`
  with explicit `dbValue()` / `fromDbValue()` conversion and a `isValidTransition` table; never
  persist `Enum.name()` implicitly and never compare status with a raw string literal in a service.
- State transitions live **in the entity**, not in the service. The service loads, calls
  `entity.dispatch()`, saves.
- Repositories return `Optional<T>`, `List<T>` or `Page<T>` — never `null`.
- Native queries only when JPQL cannot express it, and always with a comment stating why.

✅ **Do**

```java
@EntityGraph(attributePaths = {"customer", "assignedTruck"})
@Query("select l from Load l where (:status is null or l.status = :status)")
Page<Load> search(@Param("status") String status, Pageable pageable);
```

❌ **Don't**

```java
@ManyToOne(fetch = FetchType.EAGER)          // forbidden
private Customer customer;

if ("Draft".equals(invoice.getStatus())) { ... }   // magic string instead of enum
```

---

## 11. Configuration and secrets

- One `application.yml` plus `application-{local,dev,staging,prod}.yml`. Profile-specific values
  never live in the default file.
- Every secret comes from an environment variable with **no default**:
  `${DB_PASSWORD}`, not `${DB_PASSWORD:postgres}`. A committed default is a leaked credential the
  day it reaches an environment.
- `.env` is for local development only and must stay out of version control; `.env.example`
  documents the keys with placeholder values.
- Bind related properties with `@ConfigurationProperties` + `@EnableConfigurationProperties`
  (see `TenancyProperties`, `SecurityJwtProperties`). `@Value` is acceptable only for a single
  standalone value and never for secrets.
- Do not comment out configuration blocks to disable a feature. Use a profile, a
  `@ConditionalOnProperty` flag, or delete it. Commented YAML rots and hides the effective
  behaviour (currently the JPA, Flyway and tenancy blocks in `application.yml` are commented out,
  so `ddl-auto`, `open-in-view` and Flyway behaviour are implicit defaults).
- Production code must not be annotated for a test convenience. `@Profile("!nodb")` on 30
  controllers/services is a workaround for missing test slices: a plain unit test needs no Spring
  context, and a slice test declares only the beans it needs. New code adds it only where the
  surrounding feature already requires it for the `nodb` context test to pass, and the annotation is
  removed feature-by-feature as slice tests land.
- **`.env` must never decide the active profile.** `application.yml` imports
  `optional:file:.env[.properties]`, and `.env.example` ships `SPRING_PROFILES_ACTIVE=nodb`. Because
  tests read the same configuration, this silently activates `nodb` in every Spring test, every
  `@Profile("!nodb")` bean disappears from the context, and requests resolve to the static-resource
  handler — a 404 that looks like a routing bug and never mentions profiles. Two consequences:
  - `.env` carries credentials and ports only. The profile is chosen by the run configuration
    (`--spring.profiles.active=…`), not by a file every developer has a different copy of.
  - Every Spring test declares its profile explicitly with `@ActiveProfiles`, so it cannot inherit
    one from the environment.

✅ `url: ${DB_URL}` + `TENANT_REGISTRY_ENCRYPTION_KEY` from the environment
❌ `password: ${DB_PASSWORD:postgres}`, `jwt.secret: "dev-secret"`, `SPRING_PROFILES_ACTIVE` in `.env`

---

## 12. Spring Boot 4 notes

This project runs Boot **4.1.0**. Boot 4 split the starters into fine-grained modules and moved to
Jackson 3, so most Boot 3 snippets found online will not compile.

| Concern              | Boot 3 (do not copy)                                    | Boot 4 (use this)                                                   |
| -------------------- | ------------------------------------------------------- | ------------------------------------------------------------------- |
| Jackson              | `com.fasterxml.jackson.databind.ObjectMapper`            | `tools.jackson.databind.ObjectMapper`                               |
| MVC slice test       | `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| MockMvc autoconfig   | `…test.autoconfigure.web.servlet.AutoConfigureMockMvc`    | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |
| Mocked bean in slice | `@MockBean` (removed)                                    | `org.springframework.test.context.bean.override.mockito.MockitoBean` |
| Datasource autoconfig exclusion | `org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration` | `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration` |
| JPA slice test       | `@DataJpaTest` from `spring-boot-test-autoconfigure`      | requires the separate `spring-boot-data-jpa-test` module; confirm the artifact before using it |

Rule: when an example does not compile, check whether the class simply moved package before
changing the code.

---

## 13. Logging

- SLF4J via `@Slf4j`. `System.out.println` and `System.err` are forbidden.
- Parameterised messages only: `log.info("Dispatched load={} truck={}", loadId, truckId)`. No string
  concatenation, no `String.format`.
- Levels: `error` = needs human action; `warn` = degraded but handled; `info` = business milestone
  (dispatch, invoice issued, tenant provisioned); `debug` = developer detail. No `info` inside loops.
- Always include the operational context available: `tenantId`, `correlationId`, actor `userId`,
  entity id.
- Never log: passwords, JWTs, API keys, tracking/payment tokens, card data, full addresses, raw
  provider payloads containing PII. Tokens are logged as a prefix (`tok_ab…`) or not at all.
- Every `catch` that does not rethrow logs the cause object (`log.warn("…", ex)`), not `ex.getMessage()`.

✅ `log.error("Tenant migration failed tenant={}", tenantId, ex);`
❌ `log.info("payload=" + rawStripeEvent);`

---

## 14. Multi-tenancy

- Tenant is resolved per request in the filter chain (MCP API key → `X-Tenant` header → JWT claim)
  and stored in `TenantContext`. Business code reads it from the context; it never takes a
  `tenantId` parameter through service signatures for convenience.
- `TenantContext` must be cleared in a `finally` block for every request/job — a leaked
  `ThreadLocal` on a pooled thread is a cross-tenant data leak.
- Background jobs and event listeners run outside a request: they set the tenant explicitly before
  touching data.
- A query must never span tenants. Cross-tenant aggregation iterates tenants deliberately at the
  platform layer.
- Every new tenant table goes into `db/migration/tenant`; registry/platform tables into
  `db/migration/registry`. Never mix the two.
- A missing or unresolvable tenant is a rejected request, not a fallback to a default database.

---

## 15. Test conventions

### 15.1 Layout and naming

- `src/test/java` mirrors `src/main/java` **exactly**. A test for
  `modules/load/service/LoadService` lives in `modules/load/service/LoadServiceTest`, not in
  `service/` or `load/`.
- Unit and slice tests: `<ClassUnderTest>Test`. Integration tests: `<Feature>IT`.
- Method names: `should_<expected outcome>_when_<condition>`.
- One behaviour per test. If the name needs "and", split the test.

✅ `should_reject_dispatch_when_load_is_already_delivered`
❌ `testDispatch`, `loadWorkflow`, `managesStopsAndLifecycleWithoutCrudStatusBypass`

### 15.2 Structure

Given–When–Then, separated by blank lines or comments, with AssertJ assertions.

```java
@Test
void should_return_conflict_when_terminal_code_exists() {
  // given
  given(terminalRepository.existsByCode("USNYC")).willReturn(true);

  // when
  ThrowingCallable call = () -> service.create(request("USNYC"));

  // then
  assertThatThrownBy(call)
      .isInstanceOf(ConflictException.class)
      .hasMessageContaining("USNYC");
}
```

AssertJ is the single assertion library for new tests; do not mix in JUnit `assertEquals` in the
same module.

### 15.3 Layers

| Layer            | Tooling                                                        | Rules                                                                       |
| ---------------- | -------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Unit (service, entity, mapper) | `@ExtendWith(MockitoExtension.class)`, `@Mock`, AssertJ | **No Spring context.** Mock repositories only. Entity state machines and calculations are pure unit tests. |
| Slice — controller | `@WebMvcTest(XController.class)` + `MockMvc` + `@MockitoBean` service | Asserts status codes, JSON shape, validation and security wiring. No database. |
| Slice — repository | Testcontainers PostgreSQL + `@SpringBootTest` (or `@DataJpaTest` once the Boot 4 module is added) | Real PostgreSQL. Verifies JPQL, `@EntityGraph`, generated columns, constraints. |
| Integration      | `@SpringBootTest` + Testcontainers + `MockMvc`/`RestClient`     | One happy path plus the critical failure path per feature. Flyway runs the real migrations. |

- Mock only what you own's collaborators: repositories, gateways, clocks. Never mock the class under
  test, never mock `Page`/`Optional`/value objects, never mock a mapper (use the generated one via
  `Mappers.getMapper(...)`).
- Hand-rolled `java.lang.reflect.Proxy` stand-ins for repositories are not allowed in new tests —
  Mockito is on the classpath and gives verification, argument capture and failure messages that a
  proxy cannot.
- H2 must not be used to test anything that depends on PostgreSQL behaviour (identity columns,
  `text` types, quoted column names, `timestamptz`). The current suite validates JPA metadata by
  bootstrapping Hibernate without a connection — keep that as a fast guard, but it is not a
  substitute for one real-database test per repository.

#### The Boot 4 controller-slice recipe

`@WebMvcTest` loads no `@Configuration` of its own, so a naive slice runs with **no security filter
chain** and returns `200` for an anonymous request — a security assertion that can never fail. Import
the production configuration and the two auto-configurations that supply `HttpSecurity`:

```java
@WebMvcTest(TerminalController.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
@Import({
  SecurityConfiguration.class,
  RestAuthenticationEntryPoint.class,
  RestAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
@ActiveProfiles("test")                       // never inherit `nodb` from .env — see §11
@TestPropertySource(properties = {
  "app.security.jwt.issuer=https://issuer.example",
  "app.security.jwt.audience=logisticsx.api",
  "app.security.jwt.jwk-set-uri=https://issuer.example/jwks"
})
class TerminalControllerTest {
  @Autowired private MockMvc mockMvc;
  @MockitoBean private TerminalService terminalService;   // not @MockBean — removed in Boot 4
}
```

Authenticate with `.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER")))`; the
post-processor bypasses the decoder, so no JWKS call happens. Working reference:
`src/test/java/com/company/logicstic/modules/terminal/controller/TerminalControllerTest.java`.

#### The repository/integration recipe

Requires two test-scoped dependencies that are **not** on the classpath yet — `org.testcontainers:postgresql`
and `org.springframework.boot:spring-boot-testcontainers` (versions from the Boot BOM). Rationale: the
tenant schema depends on PostgreSQL-only behaviour, so H2 cannot validate it; the alternative
(today's Hibernate-metadata-only checks) catches mapping typos but never a broken query, constraint
or migration. Impact is test scope only, plus roughly 20 s of container startup per module.

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class TerminalIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

  @DynamicPropertySource
  static void flyway(DynamicPropertyRegistry registry) {
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration/tenant");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }
}
```

The container is `static` so it starts once per class. Flyway runs the real tenant migrations, so the
test validates the same DDL production uses.

### 15.4 Data

- Build test data with a builder or object-mother per aggregate
  (`TerminalTestData.aTerminal().withCode("USNYC").build()`), never by copy-pasting a 20-line setup
  block.
- Each test creates the data it needs and is independent of execution order. No shared mutable
  static state, no `@TestMethodOrder`.
- Testcontainers containers are `static` per class (reused), the schema is migrated by Flyway, and
  each test cleans up what it wrote (or runs in a rolled-back transaction).

### 15.5 Forbidden in tests

| Anti-pattern                                   | Instead                                                      |
| ---------------------------------------------- | ------------------------------------------------------------ |
| `Thread.sleep(...)`                            | Awaitility, or make the collaborator synchronous in the test |
| Assertion-free test ("it did not throw")       | Assert the observable outcome                                |
| `@Disabled` without a reason and ticket id     | Fix, delete, or `@Disabled("LOG-123: flaky until X")`        |
| Order-dependent tests, shared un-reset fixtures| Independent setup per test                                   |
| Magic totals over scanned classes (`assertEquals(50, entities.size())`) | Assert the invariant (every entity has an `@Id`, builds metadata), not the count |
| `@Autowired` field in a test                   | Constructor injection or slice injection points              |
| Dead profile/config (an `application-test.yml` no test activates) | Delete it or activate it with `@ActiveProfiles("test")` |

### 15.6 Architecture tests

Encode [§2](#2-dependency-rules) as an executable test so the rules cannot silently rot. ArchUnit
(`com.tngtech.archunit:archunit-junit5`, test scope) is the cheapest way to keep the layering honest;
the alternative, Checkstyle `ImportControl`, cannot detect package cycles. Without it every rule in
§2 is review-only — which is how the current 22 cross-feature repository dependencies and the
`load` ↔ `trip` package cycle got in.

```java
@AnalyzeClasses(packages = "com.company.logicstic", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule CONTROLLERS_MUST_NOT_USE_REPOSITORIES =
      noClasses().that().haveSimpleNameEndingWith("Controller")
          .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");

  @ArchTest
  static final ArchRule NO_PACKAGE_CYCLES =
      slices().matching("com.company.logicstic.modules.(*)..").should().beFreeOfCycles();
}
```

Merge it with the rules that already hold, then enable one more rule per refactor step. A rule that
is commented out because it fails is worse than no rule — it reads as compliance.

### 15.7 Coverage targets

- Service layer: line ≥ 80%, branch ≥ 70%.
- Entity state machines and money/HOS calculations: 100% of transitions and formula branches.
- Excluded from the metric: DTOs, entities without behaviour, `config/`, generated MapStruct impls,
  `devtools/`.
- Coverage is a floor, not a goal. A feature with 90% coverage and no test for its failure paths is
  not tested.

---

## 16. Build gates

`mvn verify` must be green before review. The build already wires:

- **Spotless** (`google-java-format`, `process-sources`) — formatting is applied automatically; never
  reformat unrelated lines in a feature PR.
- **Checkstyle** (`validate` phase, `checkstyle.xml`) — do not delete the config to make the build
  pass; fix the finding or change the rule deliberately.
- **SpotBugs** (`compile` phase, `spotbugs-exclude.xml`) — the filter file is currently empty, so the
  log is dominated by `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` on JPA entities and Spring beans, which are
  false positives for this architecture. Suppress those two patterns for `**.entity.**` and Spring
  components in `spotbugs-exclude.xml` so real findings become visible again, and keep
  `failOnError=false` only until the log is clean.
- `pom.xml`, `checkstyle.xml` and `spotbugs-exclude.xml` are part of the build contract. Deleting
  them locally to unblock a build is not a fix — the build fails for everyone else.
- Add a Maven wrapper (`mvnw`) so every developer and CI job uses the same Maven version.

---

## 17. Code review checklist

Reviewer blocks the PR on any unchecked box.

**Structure**

- [ ] New code sits in the feature that owns the lifecycle; no new top-level package for one entity
- [ ] No controller imports a repository; no feature imports another feature's `repository`
- [ ] No new package cycle
- [ ] Service exposed as an interface; callers depend on the interface

**API**

- [ ] Path is plural, kebab-case; transitions are `POST /{id}/verb`
- [ ] Request/response are records in `dto/request` / `dto/response`; no entity crosses the boundary
- [ ] `@Valid` present; validation messages are field-level
- [ ] Response wrapped in `ApiResponse<T>` / `PagedResponse<T>`; status codes per [§9](#9-rest-api-contract)
- [ ] Existing public paths and payloads unchanged (or the break is called out explicitly)

**Code**

- [ ] Constructor injection, no `@Autowired` field
- [ ] No `@Data`/`@EqualsAndHashCode`/`@ToString` on an entity
- [ ] `@Transactional` on the service impl only; write methods annotated; no self-invocation trap
- [ ] Business failures throw a typed `ApiException` subclass; nothing is swallowed; no `printStackTrace`
- [ ] All associations `LAZY`; N+1 handled with `@EntityGraph`/fetch join
- [ ] Status compared through the enum, not a string literal
- [ ] State transition implemented in the entity
- [ ] Logs are parameterised, contextual, and contain no secrets or PII
- [ ] No secret with a default value; new config bound via `@ConfigurationProperties`
- [ ] Schema change is a new forward-only Flyway migration in the right folder

**Tests**

- [ ] Test package mirrors the production package; class ends with `Test`/`IT`
- [ ] Method names follow `should_..._when_...`; Given–When–Then; AssertJ
- [ ] Unit tests use Mockito, no Spring context; controller has a `@WebMvcTest`
- [ ] New repository query covered by a real-PostgreSQL test
- [ ] Happy path **and** the failure/permission path are asserted
- [ ] No `Thread.sleep`, no `@Disabled` without a ticket, no order dependency
- [ ] `mvn verify` green locally, including Checkstyle and Spotless

---

_Reference implementation for every rule above: the `terminal` feature
(`src/main/java/com/company/logicstic/modules/terminal/`) and its tests
(`src/test/java/com/company/logicstic/modules/terminal/`). It is real code covered by `mvn verify`,
so it cannot silently drift from this document — when a convention changes, change both._
